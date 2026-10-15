/*
 * Copyright 2026 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.wire

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Stream
import fs2.io.net.Network
import scodec.bits.ByteVector

class Http2CaseOracleSuite extends RuntimeDiagnostics.Suite {
  private val validResponseFramingVariants = Set(
    "valid",
    "fragmented",
    "padded",
    "trailers",
    "fragmented-trailers",
    "controls",
    "early-ack",
  )

  private val validRelatedRejectionModes = Set(
    "plain",
    "bare-eof",
    "graceful-prefix",
    "graceful-only",
    "priority-before",
    "enable-push-zero",
  )

  import Http2CleanupFixture._

  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  private type HpackFields = Vector[(String, String)]

  for (suffix <- Vector("default-frame-size-splits-data", "settings-replace-frame-limit"))
    test(s"H2 completion repair: complete response permits closed transport/$suffix") {
      import Http2Wire._
      for {
        trace <- Transcript.create[IO]()
        eofReads <- Ref.of[IO, Int](0)
        failedWrites <- Ref.of[IO, Int](0)
        finalRead <- Deferred[IO, Unit]
        frames = ByteVector.fill(40000)(42.toByte).grouped(16000).toVector.zipWithIndex.map {
          case (bytes, index) => data(1, bytes, index == 2)
        }
        // Independent transport input: the final complete DATA is already readable,
        // but the remote socket has closed and any further send would fail.
        prefix = encode(settings()) ++ encode(settings(ack = true)) ++
          (if (suffix == "settings-replace-frame-limit")
             encode(settings(ack = true)) ++
               encode(settings(ack = true))
           else ByteVector.empty) ++
          encode(headers(1, Vector(":status" -> "200"), false)) ++
          frames.init.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ encode(frame))
        writeError <- Http2WriteFailureFixture.closedWrite
        peer <- observerSocket(
          prefix,
          encode(frames.last),
          Some(writeError),
          false,
          trace,
          eofReads,
          failedWrites,
          finalRead,
        )
        target = ScriptedServer[IO](
          "completed-response-closed-transport",
          Resource.pure(
            ServerHandle[IO](
              SocketAddress(ip"127.0.0.1", port"1"),
              IO.never[RequestData],
              IO.pure(None),
            )
          ),
        )
        _ <- selected("h2.server." + suffix).run(
          CaseContext(target, trace, (_, _) => Resource.pure(peer))
        )
        _ <- finalRead.tryGet.assertEquals(Some(()))
        _ <- failedWrites.get.assertEquals(0)
      } yield ()
    }

  for (
    suffix <- Vector(
      "priority-does-not-open-stream",
      "priority-on-closed-stream",
      "default-frame-size-splits-data",
      "settings-replace-frame-limit",
    )
  )
    test(s"H2 completion repair: ordinary Control disables push/$suffix") {
      import Http2Wire._
      for {
        pushDisabled <- Deferred[IO, Boolean]
        fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
          for {
            _ <- peer.readExact(24)
            initial <- readFrame(peer)
            _ <- pushDisabled.complete(
              initial.payload
                .grouped(6)
                .exists(b => b.take(2) == ByteVector(0, 2) && readUint32(b.drop(2)) == 0L)
            )
            _ <- peer.write(encode(settings()) ++ encode(settings(ack = true)))
            request <- readUntil(peer, fromServer = false)(_.frameType == Headers)
            _ <- publish(RequestData(path = "/control"))
            _ <- writeFrame(peer, headers(request.streamId, Vector(":status" -> "200"), false))
            body =
              if (suffix.contains("frame")) ByteVector.fill(40000)(42.toByte)
              else Http1Wire.ascii("accepted")
            _ <- body.grouped(16000).toVector.zipWithIndex.traverse_ { case (bytes, i) =>
              writeFrame(peer, data(request.streamId, bytes, (i + 1) * 16000 >= body.size))
            }
            _ <-
              if (suffix == "priority-on-closed-stream")
                readUntil(peer, fromServer = false)(_.frameType == Ping)
                  .flatMap(p => writeFrame(peer, p.copy(flags = Ack)))
              else IO.unit
          } yield ()
        }
        trace <- Transcript.create[IO]()
        _ <- selected("h2.server." + suffix).run(CaseContext(fixture._1, trace))
        disabled <- pushDisabled.get
        _ <- IO(assert(disabled, "ordinary response fixture must disable push before its request"))
      } yield ()
    }

  for {
    role <- Vector("server", "client")
    suffix <-
      if (role == "server") Vector("default-frame-size-splits-data", "settings-replace-frame-limit")
      else Vector("default-frame-size-splits-data")
    mutation <- Vector("none", "wrong-body", "oversize")
  } test(s"H2 completion repair: padded frame transfer/$role/$suffix/$mutation") {
    import Http2Wire._
    import scala.concurrent.duration._
    def script(peer: RawPeer[IO]): IO[Unit] = {
      def send(body: ByteVector, streamCredit: Long, connectionCredit: Long): IO[Unit] =
        if (body.isEmpty) IO.unit
        else {
          val bytes = body.take(if (mutation == "oversize") 16384 else 128)
          val payload = ByteVector(255.toByte) ++ bytes ++ ByteVector.fill(255)(0.toByte)
          if (payload.size > math.min(streamCredit, connectionCredit))
            readUntil(peer, fromServer = role == "client")(_.frameType == WindowUpdate).flatMap {
              f =>
                val increment = readUint32(f.payload)
                send(
                  body,
                  streamCredit + (if (f.streamId == 1) increment else 0L),
                  connectionCredit + (if (f.streamId == 0) increment else 0L),
                )
            }
          else
            writeFrame(
              peer,
              Frame(Data, Padded | (if (bytes.size == body.size) EndStream else 0), 1, payload),
            ) *>
              IO.defer(
                send(
                  body.drop(bytes.size),
                  streamCredit - payload.size,
                  connectionCredit - payload.size,
                )
              )
        }
      val setup =
        if (role == "server")
          serverHandshake(peer) *> readUntil(peer, fromServer = false)(
            _.frameType == Headers
          ).void *>
            writeFrame(peer, headers(1, Vector(":status" -> "200"), false))
        else
          clientHandshake(peer) *> writeFrame(
            peer,
            headers(
              1,
              Vector(
                ":method" -> "POST",
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> "/",
                "content-length" -> "40000",
              ),
              false,
            ),
          )
      setup *> send(
        ByteVector.fill(40000)((if (mutation == "wrong-body") 43 else 42).toByte),
        65535L,
        65535L,
      ) *>
        (if (role == "client")
           readUntil(peer, fromServer = true)(_.frameType == Headers).void *>
             readUntil(peer, fromServer = true)(_.frameType == Data)
               .flatMap(frame => IO(assertEquals(unpad(frame), Http1Wire.ascii("control"))))
         else IO.unit)
    }
    val run =
      if (role == "server") runServer(s"h2.$role.$suffix")(script)
      else
        runClient(s"h2.$role.$suffix")(peer =>
          script(peer).as(response(Http1Wire.ascii("control")))
        )
    // Diagnostic bound only: a timeout is a failed regression, never rejection evidence.
    if (mutation == "none") run.timeout(5.seconds)
    else run.intercept[ProtocolFailure].void.timeout(5.seconds)
  }

  for {
    padded <- Vector(false, true)
    ending <- Vector("eof", "flow-error", "generic-error")
  } test(s"H2 completion repair: empty upload really overflows/$padded/$ending") {
    import Http2Wire._
    import scala.concurrent.duration._
    for {
      exceeded <- Ref.of[IO, Boolean](false)
      _ <- runClient("h2.client.connection-window-overflow") { peer =>
        for {
          _ <- clientHandshake(peer)
          _ <- writeFrame(
            peer,
            headers(
              1,
              Vector(
                ":method" -> "POST",
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> "/",
                "content-length" -> "0",
              ),
              !padded,
            ),
          )
          _ <-
            if (padded) (Vector.fill(255)(256) :+ 255).traverse_ { n =>
              writeFrame(
                peer,
                Frame(
                  Data,
                  Padded,
                  1,
                  ByteVector((n - 1).toByte) ++ ByteVector.fill(n.toLong - 1L)(0.toByte),
                ),
              )
            } *> writeFrame(peer, data(1, ByteVector.empty))
            else IO.unit
          first <- readUntil(peer, fromServer = true)(_.frameType == WindowUpdate)
          before = if (padded) 0L else 65535L
          after <-
            if (before + readUint32(first.payload) > Int.MaxValue)
              IO.pure(before + readUint32(first.payload))
            else
              readUntil(peer, fromServer = true)(_.frameType == WindowUpdate)
                .map(f => before + readUint32(first.payload) + readUint32(f.payload))
          _ <- exceeded.set(after > Int.MaxValue)
          _ <-
            if (ending == "eof") IO.unit
            else
              writeFrame(
                peer,
                Frame(GoAway, 0, 0, uint32(0) ++ uint32(if (ending == "flow-error") 3 else 1)),
              )
          _ <- peer.endOfOutput
          r <- IO.raiseError[ResponseData[IO]](
            new RuntimeException("native rejected overflowing connection")
          )
        } yield r
      }.timeout(5.seconds)
      observed <- exceeded.get
      _ <- IO(assert(observed, "fixture must exceed, not merely reach, maximum connection credit"))
    } yield ()
  }

  for (
    path <- Vector(
      "/a%2Fb?x=%20&x=two",
      "/a%2fb?x=%20&x=two",
      "/a/b?x=%20&x=two",
      "/A%2Fb?x=%20&x=two",
      "/a%2Fb?x=two&x=%20",
      "/a%2Fb?x=%20&x=changed",
    )
  )
    test(s"H2 completion repair: escaped path observation/$path") {
      import Http2Wire._
      for {
        fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
          for {
            _ <- serverHandshake(peer)
            head <- readUntil(peer, fromServer = false)(_.frameType == Headers)
            decoder <- HpackWire.decoder[IO]()
            fields <- readHeaders(peer, head, decoder)
            _ <- IO(
              assertEquals(
                fields.collectFirst { case (":path", v) => v },
                Some("/a%2Fb?x=%20&x=two"),
              )
            )
            _ <- publish(RequestData(path = path))
            _ <- writeFrame(peer, headers(head.streamId, Vector(":status" -> "200")))
          } yield ()
        }
        trace <- Transcript.create[IO]()
        run = selected("h2.server.request-path-and-query").run(CaseContext(fixture._1, trace))
        _ <-
          if (path == "/a%2Fb?x=%20&x=two" || path == "/a%2fb?x=%20&x=two") run
          else run.intercept[ProtocolFailure].void
      } yield ()
    }

  for {
    role <- Vector("client", "server")
    replace <- Vector(false, true)
  } test(s"H2 completion repair: opaque recommendation/$role/$replace") {
    for {
      observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
      trace <- Transcript.create[IO]()
      c = selected(s"h2.$role.field-value-interior-tab-and-obs-text")
      run = c.run(
        CaseContext(
          hpackPresentationTarget(
            fields =>
              fields.map { case (k, v) =>
                k -> (if (replace) v.replace('\u0080', '\ufffd').replace('\u00ff', '\ufffd') else v)
              },
            observed,
          ),
          trace,
        )
      )
      _ <-
        if (replace)
          run
            .intercept[ProtocolFailure]
            .flatMap(e =>
              IO(assert(e.getMessage.contains("valid field octets were not preserved")))
            )
        else run
      _ <- IO(
        assert(
          c.references.exists(r => r.number == 9110 && r.section == "5.5" && r.strength == "SHOULD")
        )
      )
    } yield ()
  }

  for {
    timing <- Vector("before", "after", "body", "last-ping")
    queued <- Vector("empty", "terminal", "trailers", "reset", "positive", "padding")
  } test(s"H2 completion repair: queued reset output/$timing/$queued") {
    import Http2Wire._
    val run = runServer("h2.server.reset-closes-blocked-stream") { peer =>
      val entry = HpackWire.encodeLiteral(Vector("x-retired" -> "retained"))
      val pending = queued match {
        case "empty" => Vector(data(1, ByteVector.empty, false))
        case "terminal" => Vector(data(1, ByteVector.empty))
        case "trailers" =>
          Vector(
            Frame(Headers, EndStream, 1, ByteVector(0x40)),
            Frame(Continuation, EndHeaders, 1, entry.drop(1)),
          )
        case "reset" => Vector(Frame(RstStream, 0, 1, uint32(8)))
        case "positive" => Vector(data(1, ByteVector(1)))
        case "padding" => Vector(Frame(Data, Padded, 1, ByteVector(0)))
      }
      def emit(at: String): IO[Unit] =
        if (timing == at) pending.traverse_(writeFrame(peer, _)) else IO.unit
      for {
        _ <- serverHandshake(peer)
        _ <- readUntil(peer, fromServer = false)(_.frameType == Headers)
        _ <- writeFrame(
          peer,
          Frame(Headers, EndHeaders, 1, HpackWire.encodeLiteral(Vector(":status" -> "200"))),
        )
        _ <- readUntil(peer, fromServer = false)(_.frameType == RstStream)
        ping <- readUntil(peer, fromServer = false)(_.frameType == Ping)
        _ <- emit("before")
        _ <- writeFrame(peer, ping.copy(flags = Ack))
        _ <- emit("after")
        _ <- readUntil(peer, fromServer = false)(f => f.frameType == Headers && f.streamId == 3)
        _ <- writeFrame(
          peer,
          if (queued == "trailers" && (timing == "before" || timing == "after"))
            Frame(Headers, EndHeaders, 3, ByteVector(0x88, 0xbe))
          else headers(3, Vector(":status" -> "200"), false),
        )
        _ <- readUntil(peer, fromServer = false)(f =>
          f.frameType == WindowUpdate && f.streamId == 3
        )
        _ <- emit("body")
        _ <- writeFrame(peer, data(3, Http1Wire.ascii("abcdefghijklmnop"), false))
        _ <- writeFrame(peer, headers(3, Vector.empty))
        last <- readUntil(peer, fromServer = false)(_.frameType == Ping)
        _ <- emit("last-ping")
        _ <- writeFrame(peer, last.copy(flags = Ack))
      } yield ()
    }
    if (queued == "positive" || queued == "padding")
      run
        .intercept[ProtocolFailure]
        .flatMap(e => IO(assert(e.getMessage.contains("exceeds remaining credit"))))
    else run
  }

  private val hpackPresentationCases = Vector(
    "indexed-same-block",
    "dynamic-order",
    "dynamic-duplicates",
    "without-preserves-table",
    "never-preserves-table",
    "dynamic-name-evicted-source",
    "dynamic-name-without",
    "dynamic-name-never",
    "name-index-six-bit-boundary",
    "indexed-seven-bit-boundary",
    "table-oldest-eviction",
    "table-shrink-retains-newest",
    "table-zero-restore-insert",
    "reject-shrink-evicted-index",
    "encoder-stateful-roundtrip",
    "encoder-reduction-zero",
    "encoder-minimum-before-restore",
  )

  // These transformations model the native HTTP layer, before encoding or after
  // decoding. The adapter copies its collection; the codec sees/preserves its own
  // input/output. No transformation is applied inside the real adapter.
  private def hpackNativePresentation(fields: HpackFields, mode: String): HpackFields = {
    val names = fields.map(_._1).distinct
    mode match {
      case "reorder" => names.sorted.reverse.flatMap(name => fields.filter(_._1 == name))
      case "combine" | "combine-ows" =>
        names.flatMap { name =>
          val matching = fields.filter(_._1 == name)
          if ((name == "x" || name == "y") && matching.size > 1)
            Vector(name -> matching.map(_._2).mkString(if (mode == "combine") ", " else "\t,\t"))
          else matching
        }
      case _ => fields
    }
  }

  for {
    suffix <- hpackPresentationCases
    role <- Vector("client", "server")
    mode <- Vector("unchanged", "reorder", "combine", "combine-ows")
  } test(s"HPACK HTTP presentation: $role/$suffix/$mode") {
    for {
      observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
      trace <- Transcript.create[IO]()
      target = hpackPresentationTarget(hpackNativePresentation(_, mode), observed)
      _ <- selected(s"h2.$role.hpack-$suffix").run(CaseContext(target, trace))
      fields <- observed.get
      _ <- IO(assert(fields.nonEmpty, "the actual case must reach the codec"))
    } yield ()
  }

  for {
    role <- Vector("client", "server")
    mode <- Vector(
      "wrong-name",
      "wrong-value",
      "missing-member",
      "extra-member",
      "reversed-members",
      "member-whitespace",
    )
  } test(s"HPACK HTTP presentation rejects changed members: $role/$mode") {
    def mutate(fields: HpackFields): HpackFields = {
      val changed = mode match {
        case "wrong-name" =>
          fields.map { case (name, value) =>
            (if (name == "x") "z" else name) -> value
          }
        case "wrong-value" =>
          fields.map { case (name, value) =>
            name -> (if (name == "x") "wrong" else value)
          }
        case "missing-member" => fields.patch(fields.indexWhere(_._1 == "x"), Nil, 1)
        case "extra-member" => fields ++ fields.filter(_._1 == "x").take(1)
        case "reversed-members" => fields.reverse
        case "member-whitespace" =>
          fields.map { case (name, value) =>
            name -> (if (name == "x" && value == "a") " a" else value)
          }
      }
      hpackNativePresentation(changed, "combine")
    }
    for {
      observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
      trace <- Transcript.create[IO]()
      result <- selected(s"h2.$role.hpack-dynamic-order")
        .run(CaseContext(hpackPresentationTarget(mutate, observed), trace))
        .attempt
      _ <- IO(assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString))
    } yield ()
  }

  for (role <- Vector("client", "server"))
    test(s"HPACK HTTP presentation retains dynamic index ordering witness: $role") {
      for {
        observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
        trace <- Transcript.create[IO]()
        result <- selected(s"h2.$role.hpack-dynamic-order")
          .run(
            CaseContext(
              hpackPresentationTarget(
                hpackNativePresentation(_, "combine"),
                observed,
                wrongIndex = true,
              ),
              trace,
            )
          )
          .attempt
        fields <- observed.get
        _ <- IO {
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
          // Stream 1 succeeded with both 34-octet entries. Only the stream 3 lookup
          // maps indices 62/63 backwards; combination must not conceal that defect.
          assertEquals(
            fields
              .map { case (stream, fs) => stream -> fs.filter(_._1 == "x") }
              .filter(_._2.nonEmpty),
            Vector(1 -> Vector("x" -> "a", "x" -> "b"), 3 -> Vector("x" -> "a", "x" -> "b")),
          )
          assert(result.swap.exists(_.getMessage.contains("b,a")), result.toString)
        }
      } yield ()
    }

  // Single literal values remain opaque: the special list comparison must not
  // trim significant whitespace or interpret arbitrary commas/raw octets.
  for {
    role <- Vector("client", "server")
    suffix <- Vector("literal-without-new-name", "string-raw-octets")
  } test(s"HPACK HTTP presentation retains opaque values: $role/$suffix") {
    for {
      observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
      trace <- Transcript.create[IO]()
      unchanged = hpackPresentationTarget(identity, observed)
      _ <- selected(s"h2.$role.hpack-$suffix").run(CaseContext(unchanged, trace))
      changed = hpackPresentationTarget(
        _.map { case (name, value) => name -> (if (name == "x") value + " " else value) },
        observed,
      )
      result <- selected(s"h2.$role.hpack-$suffix").run(CaseContext(changed, trace)).attempt
      _ <- IO(assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString))
    } yield ()
  }

  for {
    role <- Vector("client", "server")
    (label, value, valid) <- Vector(
      ("space-before", "a ,b", true),
      ("tab-after", "a,\tb", true),
      ("mixed-ows", "a \t,\t b", true),
      ("leading-space", " a,b", false),
      ("trailing-tab", "a,b\t", false),
      ("internal-space", "a z,b", false),
      ("non-ows-control", "a\r,\nb", false),
      ("leading-comma", ",a,b", false),
      ("trailing-comma", "a,b,", false),
      ("empty-member", "a, \t,b", false),
      ("long-separator-ows", "a" + (" \t" * 40000) + ",b", true),
      ("long-internal-space", "a" + (" " * 80000) + "z,b", false),
      ("long-internal-tab", "a" + ("\t" * 80000) + "z,b", false),
    )
  } test(s"HPACK separator normalization: $role/$label") {
    import scala.concurrent.duration._
    for {
      observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
      trace <- Transcript.create[IO]()
      target = hpackPresentationTarget(
        fields => {
          val members = fields.collect { case ("x", member) => member }
          // Only change the first decoded HTTP observation; codec input and the
          // subsequent reversed-index witness retain their original values.
          if (members == Vector("a", "b")) fields.filterNot(_._1 == "x") :+ ("x" -> value)
          else fields
        },
        observed,
      )
      result <- selected(s"h2.$role.hpack-dynamic-order")
        .run(CaseContext(target, trace))
        .timeout(1.second)
        .attempt
      fields <- observed.get
      _ <- IO {
        assert(fields.nonEmpty, "the actual case must reach the codec")
        if (valid) assertEquals(result, Right(()))
        else
          assert(
            result.swap.exists(_.isInstanceOf[ProtocolFailure]),
            result.fold(_.getClass.getSimpleName, _ => "unexpected success"),
          )
      }
    } yield ()
  }

  private def hpackPresentationTarget(
      present: HpackFields => HpackFields,
      observed: Ref[IO, Vector[(Int, HpackFields)]],
      wrongIndex: Boolean = false,
      rejectMode: String = "plain",
  ): WireTarget[IO] = new WireTarget[IO] {
    def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

    import Http2Wire._
    val name = "native-http-presentation"
    val version = "1"

    def updateSettings(frame: Frame, updates: Ref[IO, ByteVector]): IO[Unit] =
      frame.payload.grouped(6).toVector.traverse_ { bytes =>
        val key = ((bytes(0) & 255) << 8) | (bytes(1) & 255)
        if (key != 1) IO.unit
        else updates.update(_ ++ HpackWire.encodeInteger(readUint32(bytes.drop(2)).toInt, 5, 0x20))
      }

    def emit(
        peer: RawPeer[IO],
        stream: Int,
        fields: HpackFields,
        updates: Ref[IO, ByteVector],
    ): IO[Unit] = for {
      update <- updates.getAndSet(ByteVector.empty)
      input = fields.filter(_._1.startsWith(":")) ++ present(fields.filterNot(_._1.startsWith(":")))
      block = HpackWire.encodeLiteral(input)
      inspector <- HpackWire.decoder[IO]()
      decoded <- inspector.decode(block)
      _ <- IO(assertEquals(decoded, input, "encoder must preserve its actual codec input"))
      _ <- observed.update(_ :+ (stream -> input))
      _ <- writeFrame(peer, Frame(Headers, EndHeaders | EndStream, stream, update ++ block))
    } yield ()

    def decode(peer: RawPeer[IO], head: Frame, decoder: HpackWire.Decoder[IO]): IO[HpackFields] = {
      val input =
        if (wrongIndex && head.streamId == 3)
          head.copy(payload = replace(head.payload, "bebf", "bfbe"))
        else head
      readHeaders(peer, input, decoder).flatTap(fields =>
        observed.update(_ :+ (head.streamId -> fields))
      )
    }

    def reject(peer: RawPeer[IO], stream: Int, fromServer: Boolean): IO[Unit] =
      rejectionTraffic(rejectMode, stream, fromServer, 9L).traverse_(
        writeFrame(peer, _)
      ) *> peer.endOfOutput

    def server(p: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] = for {
      requests <- Resource.eval(cats.effect.std.Queue.unbounded[IO, RequestData])
      listener <- Network[IO].bind(SocketAddress(ip"127.0.0.1", port"0"))
      _ <- listener.accept.head.compile.resource.lastOrError.use { socket =>
        for {
          trace <- Transcript.create[IO]()
          peer <- RawPeer.create(socket, trace)
          decoder <- HpackWire.decoder[IO]()
          updates <- Ref.of[IO, ByteVector](ByteVector.empty)
          _ <- serverHandshake(peer)
          _ <- {
            def loop: IO[Unit] = readFrame(peer).flatMap {
              case f if f.frameType == Settings && !f.hasFlag(Ack) =>
                updateSettings(f, updates) *> writeFrame(
                  peer,
                  Http2Wire.settings(ack = true),
                ) *> loop
              case f if f.frameType == Headers =>
                decode(peer, f, decoder).attempt.flatMap {
                  case Right(fields) =>
                    val request = RequestData(
                      method = fields.collectFirst { case (":method", value) => value }.get,
                      path = fields.collectFirst { case (":path", value) => value }.get,
                      headers = present(fields.filterNot(_._1.startsWith(":"))),
                    )
                    requests.offer(request) *> (scenario match {
                      case ServerScenario.Reply(_, headers, _) =>
                        emit(peer, f.streamId, Vector(":status" -> "200") ++ headers, updates)
                      case _ =>
                        writeFrame(
                          peer,
                          headers(f.streamId, Vector(":status" -> "200"), endStream = true),
                        )
                    }) *> loop
                  case Left(_) => reject(peer, f.streamId, fromServer = true) *> IO.never[Unit]
                }
              case _ => loop
            }
            loop
          }
        } yield ()
      }.background
    } yield ServerHandle(listener.address.asIpUnsafe, requests.take, requests.tryTake)

    def client(p: Protocol, address: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
      for {
        socket <- Network[IO].connect(address)
        trace <- Resource.eval(Transcript.create[IO]())
        peer <- Resource.eval(RawPeer.create(socket, trace))
        decoder <- Resource.eval(HpackWire.decoder[IO]())
        updates <- Resource.eval(Ref.of[IO, ByteVector](ByteVector.empty))
        next <- Resource.eval(Ref.of[IO, Int](1))
        responses <- Resource.eval(
          cats.effect.std.Queue.unbounded[IO, Either[Throwable, ResponseData[IO]]]
        )
        _ <- Resource.eval(clientHandshake(peer))
        _ <- {
          def loop: IO[Unit] = readFrame(peer).flatMap {
            case f if f.frameType == Settings && !f.hasFlag(Ack) =>
              updateSettings(f, updates) *> writeFrame(peer, Http2Wire.settings(ack = true)) *> loop
            case f if f.frameType == Headers =>
              decode(peer, f, decoder).attempt.flatMap {
                case Right(fields) =>
                  responses.offer(
                    Right(
                      ResponseData[IO](
                        fields.collectFirst { case (":status", value) => value.toInt }.get,
                        present(fields.filterNot(_._1.startsWith(":"))),
                        Stream.empty,
                        IO.pure(Vector.empty),
                      )
                    )
                  ) *> loop
                case Left(_) =>
                  reject(peer, f.streamId, fromServer = false) *>
                    responses.offer(
                      Left(new RuntimeException("native compression rejection"))
                    ) *> IO.never[Unit]
              }
            case _ => loop
          }
          loop.background
        }
      } yield new ClientDriver[IO] {
        def exchange(request: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval(for {
          stream <- next.getAndUpdate(_ + 2)
          _ <- emit(
            peer,
            stream,
            Vector(
              ":method" -> request.method,
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> request.path,
            ) ++ request.headers,
            updates,
          )
          response <- responses.take.flatMap(_.liftTo[IO])
        } yield response)
        def exchangeStream(
            request: RequestData,
            body: FixtureCleanup[IO] => Stream[IO, Byte],
        ): Resource[IO, ResponseData[IO]] = exchange(request)
      }
  }

  for {
    outcome <- Vector(
      "success",
      "native-acquire",
      "native-body",
      "assertion",
      "adapter",
      "decoder",
      "observation",
      "timeout",
    )
    releaseFails <- Vector(false, true)
  } test(s"smoke rejection classifies before release: $outcome/releaseFails=$releaseFails") {
    smokeClientRejectionProbe(outcome, releaseFails, "reset", closeOnRelease = false)
  }

  test("smoke rejection accepts an independent transport EOF") {
    smokeClientRejectionProbe("native-body", releaseFails = false, "eof", closeOnRelease = false)
  }

  for (close <- Vector(false, true)) {
    test(s"smoke rejection holds response before wire evidence and cancels cleanly: close=$close") {
      smokeClientRejectionProbe("native-body", releaseFails = false, "held", closeOnRelease = close)
    }
    test(s"smoke rejection deadline cannot become cleanup EOF: close=$close") {
      smokeClientRejectionProbe(
        "native-body",
        releaseFails = false,
        "deadline",
        closeOnRelease = close,
      )
    }
  }

  private def smokeClientRejectionProbe(
      outcome: String,
      releaseFails: Boolean,
      witness: String,
      closeOnRelease: Boolean,
  ): IO[Unit] = {
    import Http2Wire._
    import scala.concurrent.duration._
    val failure: Throwable = outcome match {
      case "assertion" => new ProtocolFailure("smoke assertion")
      case "adapter" => new AdapterFailure("smoke fixture", new RuntimeException("fixture"))
      case "decoder" => new WireDecodingFailure("smoke decoder")
      case "observation" => new WireObservationLimit("smoke observation")
      case "timeout" => new java.util.concurrent.TimeoutException("smoke native timeout")
      case _ => new RuntimeException("smoke native rejection")
    }
    val releaseFailure = new RuntimeException("smoke response release")
    for {
      consumed <- Deferred[IO, Unit]
      releases <- Ref.of[IO, Int](0)
      target = ScriptedClient.network[IO] { (_, peer) =>
        Resource.pure[IO, ClientDriver[IO]](new ClientDriver[IO] {
          def exchange(request: RequestData): Resource[IO, ResponseData[IO]] = Resource.make {
            for {
              _ <- clientHandshake(peer)
              _ <- writeFrame(
                peer,
                headers(
                  1,
                  Vector(
                    ":method" -> request.method,
                    ":scheme" -> "http",
                    ":authority" -> "wire.test",
                    ":path" -> request.path,
                  ),
                ),
              )
              first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
              decoder <- HpackWire.decoder[IO]()
              fields <- readHeaders(peer, first, decoder)
              data <- readUntil(peer, fromServer = true)(_.frameType == Data)
              _ <- IO {
                assertEquals(
                  fields.collect { case ("content-length", value) => value },
                  Vector("4"),
                )
                assertEquals(data.streamId, 1)
                assertEquals(data.payload, Http1Wire.ascii("abc"))
                assert(data.hasFlag(EndStream), "the malformed response must be complete")
              }
              _ <- witness match {
                case "reset" => writeFrame(peer, Frame(RstStream, 0, 1, uint32(1)))
                case "eof" => peer.endOfOutput
                case _ => IO.unit
              }
              response <-
                if (outcome == "native-acquire") IO.raiseError[ResponseData[IO]](failure)
                else
                  IO.pure(
                    ResponseData[IO](
                      200,
                      fields.filterNot(_._1.startsWith(":")),
                      Stream.eval(consumed.complete(()).void).drain ++
                        (if (outcome == "success") Stream.chunk(fs2.Chunk.byteVector(data.payload))
                         else Stream.raiseError[IO](failure)),
                      IO.pure(Vector.empty),
                    )
                  )
            } yield response
          } { _ =>
            releases.update(_ + 1) *> (if (closeOnRelease) peer.endOfOutput else IO.unit) *>
              (if (releaseFails) IO.raiseError[Unit](releaseFailure) else IO.unit)
          }
          def exchangeStream(
              request: RequestData,
              body: FixtureCleanup[IO] => Stream[IO, Byte],
          ): Resource[IO, ResponseData[IO]] = exchange(request)
        })
      }
      trace <- Transcript.create[IO]()
      run = selected("h2.client.reject-content-length-mismatch").run(CaseContext(target, trace))
      _ <-
        if (witness == "held") assertHeldClientObservation(run, consumed, releases)
        else {
          val checked =
            if (witness == "deadline") run.timeout(500.millis) else run.timeout(5.seconds)
          checked.attempt.flatMap { result =>
            IO {
              if (witness == "deadline")
                assertEquals(WireSuite.outcomeStatus(result), "inconclusive")
              else if (outcome == "success")
                assert(
                  result.swap.exists(e =>
                    e.isInstanceOf[ProtocolFailure] && e.getMessage
                      .contains("successfully consumed")
                  ),
                  result.toString,
                )
              else if (outcome == "native-acquire" || outcome == "native-body")
                assertEquals(
                  result,
                  if (releaseFails && outcome == "native-body") Left(releaseFailure) else Right(()),
                )
              else assertEquals(result, Left(failure))
            }
          }
        }
      _ <- releases.get.assertEquals(if (outcome == "native-acquire") 0 else 1)
      _ <- consumed.tryGet.assertEquals(if (outcome == "native-acquire") None else Some(()))
    } yield ()
  }

  // These controls decode the actual complete prefix; no synthetic request is invented
  // for orphan/incomplete blocks. Publication does not discharge the wire obligation.
  private val progressiveTrailerFaults = Vector(
    "padding-equals-payload",
    "padding-exceeds-payload",
    "missing-pad-length",
    "short-priority",
  )

  private val progressiveHpackFaults = Vector(
    "index-zero",
    "index-empty-dynamic",
    "index-integer-truncated",
    "incremental-name-integer-truncated",
    "without-name-integer-truncated",
    "never-name-integer-truncated",
    "index-integer-huge",
    "name-truncated",
    "name-length-truncated",
    "value-truncated",
    "value-missing",
    "string-length-truncated",
    "huffman-string-truncated",
    "incremental-name-index",
    "without-name-index",
    "never-name-index",
    "huffman-value-eos",
    "huffman-name-eos",
    "huffman-padding-eight",
    "huffman-name-padding-eight",
    "huffman-value-padding",
    "huffman-name-padding",
    "update-after-index",
    "update-after-literal",
    "index-beyond-populated",
    "without-not-stored",
    "never-not-stored",
    "evicted-oldest-index",
    "shrink-evicted-index",
    "zero-restored-index",
    "oversize-clears-table",
    "huffman-compressed-size",
    "initial-dynamic-index",
  )

  for {
    id <- progressiveTrailerFaults.map("h2.server.reject-headers-" + _) ++
      progressiveHpackFaults.map("h2.server.hpack-reject-" + _)
    publish <- Vector(false, true)
    mode <- Vector("correct-error", "wrong-error", "delayed-close")
  } test(s"server progressive publication: $id/publish=$publish/$mode") {
    import scala.concurrent.duration._
    for {
      sent <- Deferred[IO, Unit]
      close <- Deferred[IO, Unit]
      completed <- Deferred[IO, Either[Throwable, Unit]]
      fixture <- ScriptedTarget.create[IO] { (peer, offer) =>
        progressiveServerScript(id, publish, mode, sent, close)(peer, offer)
      }
      trace <- Transcript.create[IO]()
      run = selected(id).run(CaseContext(fixture._1, trace))
      _ <- Resource.make(run.attempt.flatMap(completed.complete).start)(_.cancel).use { _ =>
        for {
          _ <- IO.race(sent.get, completed.get).void.timeout(5.seconds)
          _ <-
            if (mode == "delayed-close")
              // This bounded observation asserts pending, never protocol rejection. Only
              // the explicit subsequent transport close may complete the actual case.
              completed.get.map(Option(_)).timeoutTo(200.millis, IO.pure(None)).flatMap { early =>
                IO(assertEquals(early, None)) *> close.complete(()).void
              }
            else IO.unit
          result <- completed.get.timeout(5.seconds)
          _ <- IO {
            if (mode == "wrong-error") {
              assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
              assert(
                result.swap.exists(
                  _.getMessage.contains(
                    if (id.contains("hpack")) "compression failure used error code 8"
                    else "incorrect GOAWAY error code"
                  )
                ),
                result.toString,
              )
            } else assertEquals(result, Right(()))
          }
        } yield ()
      }
      _ <- fixture._2.listenerFinalizers.get.assertEquals(1)
      _ <- fixture._2.socketFinalizers.get.assertEquals(1)
      _ <- fixture._2.scriptFinalizers.get.assertEquals(1)
    } yield ()
  }

  for {
    id <- Vector("h2.server.reject-headers-short-priority", "h2.server.hpack-reject-index-zero")
  } test(s"server progressive publication retains cleanup errors: $id") {
    val cleanupFailure = new RuntimeException("progressive server cleanup failure")
    for {
      sent <- Deferred[IO, Unit]
      close <- Deferred[IO, Unit]
      fixture <- ScriptedTarget.create[IO] { (peer, offer) =>
        progressiveServerScript(id, true, "correct-error", sent, close)(peer, offer)
      }
      target = new WireTarget[IO] {
        def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

        val name = "progressive-cleanup"
        val version = "probe"
        def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
          fixture._1.server(p, s).onFinalize(IO.raiseError(cleanupFailure))
        def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
          fixture._1.client(p, a)
      }
      trace <- Transcript.create[IO]()
      result <- selected(id).run(CaseContext(target, trace)).attempt
      _ <- IO(assertEquals(result, Left(cleanupFailure)))
      _ <- fixture._2.listenerFinalizers.get.assertEquals(1)
      _ <- fixture._2.socketFinalizers.get.assertEquals(1)
      _ <- fixture._2.scriptFinalizers.get.assertEquals(1)
    } yield ()
  }

  private def progressiveServerScript(
      id: String,
      publish: Boolean,
      mode: String,
      sent: Deferred[IO, Unit],
      close: Deferred[IO, Unit],
  )(peer: RawPeer[IO], offer: RequestData => IO[Unit]): IO[Unit] = {
    import Http2Wire._
    def request(fields: Vector[(String, String)]): RequestData = RequestData(
      method = fields.collectFirst { case (":method", value) => value }.getOrElse(""),
      path = fields.collectFirst { case (":path", value) => value }.getOrElse(""),
      headers = fields.filterNot(_._1.startsWith(":")),
      body = ByteVector.empty,
    )
    def reject(streamId: Int, code: Long, fields: Vector[(String, String)]): IO[Unit] =
      (if (publish) offer(request(fields)) else IO.unit) *>
        writeFrame(
          peer,
          Frame(
            GoAway,
            0,
            0,
            uint32(streamId.toLong) ++
              uint32(if (mode == "wrong-error") 8L else code),
          ),
        ) *>
        sent.complete(()).void *>
        (if (mode == "delayed-close") close.get else IO.unit) *>
        (if (mode == "wrong-error") IO.never[Unit] else peer.endOfOutput)
    for {
      _ <- serverHandshake(peer)
      decoder <- HpackWire.decoder[IO]()
      _ <-
        if (id.contains("hpack")) {
          val prefix = ByteVector.fromValidHex("8286840109776972652e74657374")
          def loop: IO[Unit] =
            readUntil(peer, fromServer = false)(f => f.frameType == Headers || f.frameType == Data)
              .flatMap { head =>
                // END_STREAM with no intervening DATA establishes the empty body. Warmups
                // are decoded with their connection state and published without alteration.
                IO(assertEquals(head.frameType, Headers)) *>
                  IO(assert(head.hasFlag(EndStream) && head.hasFlag(EndHeaders))) *>
                  decoder.decode(head.payload).attempt.flatMap {
                    case Right(fields) =>
                      offer(request(fields)) *>
                        writeFrame(peer, headers(head.streamId, Vector(":status" -> "200"))) *> loop
                    case Left(_) =>
                      val at = head.payload.indexOfSlice(prefix)
                      for {
                        _ <- IO(
                          assert(at >= 0, "complete request prefix precedes compression fault")
                        )
                        prefixDecoder <- HpackWire.decoder[IO]()
                        fields <- prefixDecoder.decode(head.payload.take(at + prefix.size))
                        _ <- IO(
                          assertEquals(
                            fields,
                            Vector(
                              ":method" -> "GET",
                              ":scheme" -> "http",
                              ":path" -> "/",
                              ":authority" -> "wire.test",
                            ),
                          )
                        )
                        _ <- reject(head.streamId, 9L, fields)
                      } yield ()
                  }
              }
          loop
        } else
          for {
            initial <- readUntil(peer, fromServer = false)(_.frameType == Headers)
            fields <- readHeaders(peer, initial, decoder)
            _ <- IO(assert(!initial.hasFlag(EndStream)))
            _ <- IO(
              assertEquals(
                fields,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> "/mapping",
                ),
              )
            )
            // The very next non-SETTINGS frame must be the terminal malformed trailer:
            // no DATA bytes may be skipped before publishing the empty body.
            fault <- readUntil(peer, fromServer = false)(_.frameType != Settings)
            _ <- IO(assertEquals(fault.frameType, Headers))
            _ <- IO(assertEquals(fault.streamId, initial.streamId))
            _ <- IO(assert(fault.hasFlag(EndStream) && fault.hasFlag(EndHeaders)))
            _ <- IO(
              assertEquals(
                fault.payload,
                if (id.endsWith("equals-payload")) ByteVector(1)
                else if (id.endsWith("exceeds-payload")) ByteVector(2)
                else if (id.endsWith("missing-pad-length")) ByteVector.empty
                else ByteVector(0, 0, 0, 0),
              )
            )
            code =
              if (id.endsWith("short-priority") || id.endsWith("missing-pad-length")) 6L else 1L
            _ <- reject(initial.streamId, code, fields)
          } yield ()
    } yield ()
  }

  for {
    id <- Vector(
      "h2.client.rst-cancel-no-response",
      "h2.client.rst-unknown-error-no-response",
      "h2.client.goaway-debug-data",
      "h2.client.goaway-unknown-error",
    )
    outcome <- Vector("valid-response", "wrong-status", "wrong-body", "native-body", "assert-body")
    releaseFails <- Vector(false, true)
  } test(s"control release classification: $id/$outcome/releaseFails=$releaseFails") {
    import Http2Wire._
    import scala.concurrent.duration._
    val releaseFailure = new RuntimeException("control exchange release failure")
    val bodyFailure = new ProtocolFailure("control body assertion failure")
    for {
      pingAcked <- Ref.of[IO, Boolean](false)
      consumed <- Ref.of[IO, Boolean](false)
      releases <- Ref.of[IO, Int](0)
      target = new WireTarget[IO] {
        def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

        val name = "control-release-classification"
        val version = "probe"
        def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
          Resource.eval(IO.raiseError(new UnsupportedOperationException))
        def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] = for {
          socket <- Network[IO].connect(a)
          trace <- Resource.eval(Transcript.create[IO]())
          peer <- Resource.eval(RawPeer.create(socket, trace))
        } yield new ClientDriver[IO] {
          def exchange(request: RequestData): Resource[IO, ResponseData[IO]] = Resource.make {
            def throughPing(left: Int): IO[Unit] =
              if (left == 0) IO.raiseError(new IllegalStateException("control probe frame limit"))
              else
                readFrame(peer).flatMap { frame =>
                  if (frame.frameType == Ping && !frame.hasFlag(Ack))
                    writeFrame(peer, frame.copy(flags = Ack)) *> pingAcked.set(true)
                  else throughPing(left - 1)
                }
            val body: Stream[IO, Byte] = outcome match {
              case "native-body" =>
                Stream.raiseError[IO](new RuntimeException("native stream interruption"))
              case "assert-body" => Stream.raiseError[IO](bodyFailure)
              case "wrong-body" => Stream.chunk(fs2.Chunk.byteVector(Http1Wire.ascii("wrong")))
              case _ => Stream.chunk(fs2.Chunk.byteVector(Http1Wire.ascii("control")))
            }
            clientHandshake(peer) *>
              writeFrame(
                peer,
                headers(
                  1,
                  Vector(
                    ":method" -> request.method,
                    ":scheme" -> "http",
                    ":authority" -> "wire.test",
                    ":path" -> request.path,
                  ),
                ),
              ) *> throughPing(16) *>
              IO.pure(
                ResponseData[IO](
                  if (outcome == "wrong-status") 500 else 200,
                  Vector.empty,
                  Stream.eval(consumed.set(true)).drain ++ body,
                  IO.pure(Vector.empty),
                )
              )
          } { _ =>
            // Keep the socket open: release cannot supply an EOF wire witness.
            releases.update(_ + 1) *>
              (if (releaseFails) IO.raiseError[Unit](releaseFailure) else IO.unit)
          }
          def exchangeStream(
              request: RequestData,
              body: FixtureCleanup[IO] => Stream[IO, Byte],
          ): Resource[IO, ResponseData[IO]] = exchange(request)
        }
      }
      trace <- Transcript.create[IO]()
      result <- selected(id).run(CaseContext(target, trace)).timeout(5.seconds).attempt
      _ <- pingAcked.get.assertEquals(true)
      _ <- consumed.get.assertEquals(true)
      _ <- releases.get.assertEquals(1)
      _ <- IO {
        if (outcome == "assert-body") assertEquals(result, Left(bodyFailure))
        else if (
          outcome == "wrong-status" || outcome == "wrong-body" ||
          (id.contains(".rst-") && outcome == "valid-response")
        ) assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        else if (releaseFails) assertEquals(result, Left(releaseFailure))
        else assertEquals(result, Right(()))
      }
    } yield ()
  }

  for {
    id <- Vector(
      "h2.client.reject-content-length-mismatch",
      "h2.client.reject-missing-status",
      "h2.client.hpack-reject-index-zero",
      "h2.client.reject-ping-length-nine",
      "h2.client.idle-data",
    )
    kind <- Vector("native", "direct", "deep", "suppressed", "adapter-and-timeout")
  } test(s"$id classifies the complete $kind rejection graph") {
    val timeout = new java.util.concurrent.TimeoutException("native timeout")
    val error: Throwable = kind match {
      case "direct" => timeout
      case "deep" =>
        (1 to 32).foldLeft[Throwable](timeout)((cause, _) => new RuntimeException("wrapper", cause))
      case "suppressed" | "adapter-and-timeout" =>
        val wrapper = new RuntimeException("native wrapper")
        wrapper.addSuppressed(timeout)
        if (kind == "adapter-and-timeout")
          wrapper.addSuppressed(new AdapterFailure("fixture", new Exception("fixture")))
        wrapper
      case _ => new RuntimeException("native protocol rejection")
    }
    runClient(id) { peer =>
      import Http2Wire._
      val frameKind =
        if (id.contains("ping-length")) Ping
        else if (id.endsWith("idle-data")) Data
        else Headers
      for {
        _ <- clientHandshake(peer)
        _ <- writeFrame(
          peer,
          headers(
            1,
            Vector(
              ":method" -> (if (id.endsWith("idle-data")) "POST" else "GET"),
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/",
            ),
          ),
        )
        _ <- readUntil(peer, fromServer = true)(_.frameType == frameKind)
        _ <-
          if (id.contains("ping-length"))
            readUntil(peer, fromServer = true)(_.frameType == Headers) *> readUntil(
              peer,
              fromServer = true,
            )(_.frameType == Data).void
          else IO.unit
        _ <-
          if (id.contains("hpack") || id.contains("ping-length") || id.endsWith("idle-data")) {
            val code = if (id.contains("hpack")) 9L else 1L
            writeFrame(peer, Frame(GoAway, 0, 0, uint32(0) ++ uint32(code))) *> peer.endOfOutput
          } else writeFrame(peer, Frame(RstStream, 0, 1, uint32(1)))
        result <- IO.raiseError[ResponseData[IO]](error)
      } yield result
    }.attempt.map { result =>
      assertEquals(
        WireSuite.outcomeStatus(result),
        if (kind == "native") "pass"
        else if (kind == "adapter-and-timeout") "error"
        else "inconclusive",
      )
    }
  }

  // Complete nonempty DATA stimulus surface, including invalid-frame completion tails.
  // A zero receive window must stop the fixture before its unrelated traffic can fail the target.
  for (
    id <- Vector(
      "h2.client.headers-and-data",
      "h2.client.reject-content-length-mismatch",
      "h2.client.default-frame-size-splits-data",
      "h2.client.goaway-debug-data",
      "h2.client.goaway-unknown-error",
      "h2.client.ignore-ping-unused-flags",
      "h2.client.ignore-reserved-frame-bit",
      "h2.client.ignore-settings-unused-flags",
      "h2.client.ping-ack-no-response",
      "h2.client.ping-all-octets",
      "h2.client.prior-knowledge-preface",
      "h2.client.priority-exclusive-max-dependency",
      "h2.client.priority-idle",
      "h2.client.reject-goaway-length-seven",
      "h2.client.reject-goaway-stream-id",
      "h2.client.reject-ping-ack-length",
      "h2.client.reject-ping-length-nine",
      "h2.client.reject-ping-length-seven",
      "h2.client.reject-ping-stream-id",
      "h2.client.reject-priority-stream-zero",
      "h2.client.reject-rst-idle-stream",
      "h2.client.reject-rst-length-five",
      "h2.client.reject-rst-length-three",
      "h2.client.reject-rst-stream-zero",
      "h2.client.reject-settings-ack-payload",
      "h2.client.reject-settings-duplicate-invalid-first",
      "h2.client.reject-settings-duplicate-invalid-last",
      "h2.client.reject-settings-enable-push-two",
      "h2.client.reject-settings-frame-size-above-maximum",
      "h2.client.reject-settings-frame-size-below-minimum",
      "h2.client.reject-settings-length",
      "h2.client.reject-settings-server-enable-push-one",
      "h2.client.reject-settings-stream-id",
      "h2.client.reject-settings-window-overflow",
      "h2.client.settings-concurrency-uint32",
      "h2.client.settings-concurrency-zero",
      "h2.client.settings-empty-update",
      "h2.client.settings-enable-push-zero",
      "h2.client.settings-header-list-advisory",
      "h2.client.settings-header-list-zero",
      "h2.client.settings-header-table-uint32",
      "h2.client.settings-header-table-zero",
      "h2.client.settings-max-frame-maximum",
      "h2.client.settings-max-frame-minimum",
      "h2.client.settings-ordered-acknowledgments",
      "h2.client.settings-unknown-identifier",
      "h2.client.settings-window-maximum",
      "h2.client.settings-window-zero",
      "h2.client.unknown-connection-frame",
      "h2.client.unknown-empty-frame",
      "h2.client.unknown-max-default-frame",
      "h2.client.unknown-stream-frame",
      "h2.client.content-length-matching",
      "h2.client.content-length-padded-data",
      "h2.client.message-binary-data",
      "h2.client.message-empty-trailers",
      "h2.client.message-fragmented-trailers",
      "h2.client.message-trailers",
      "h2.client.reject-content-length-overflow",
      "h2.client.reject-content-length-underflow",
      "h2.client.reject-trailer-prohibited-field",
      "h2.client.reject-trailer-pseudo-header",
      "h2.client.reject-trailer-uppercase-name",
      "h2.client.reject-trailers-without-end-stream",
      "h2.server.content-length-matching",
      "h2.server.content-length-padded-data",
      "h2.server.message-binary-data",
      "h2.server.message-empty-trailers",
      "h2.server.message-fragmented-trailers",
      "h2.server.message-trailers",
      "h2.server.reject-content-length-overflow",
      "h2.server.reject-content-length-underflow",
      "h2.server.reject-trailer-prohibited-field",
      "h2.server.reject-trailer-pseudo-header",
      "h2.server.reject-trailer-uppercase-name",
      "h2.server.reject-trailers-without-end-stream",
      "h2.server.settings-stream-window-overflow",
      "h2.server.stream-window-overflow",
      "h2.server.zero-stream-window-update",
    )
  ) test(s"$id reports the unavailable zero-window DATA prerequisite") {
    import Http2Wire._
    val run = if (id.startsWith("h2.server.")) runServer(id) { peer =>
      peer.readExact(clientPreface.size.toInt) *> readFrame(peer) *>
        peer.write(encode(settings(Vector(4 -> 0L))) ++ encode(settings(ack = true))) *> IO.never
    }
    else
      runClient(id) { peer =>
        peer.write(clientPreface ++ encode(settings(Vector(4 -> 0L)))) *> IO.never
      }
    run
      .intercept[WireObservationLimit]
      .map(error => assert(error.getMessage.contains("initial stream window")))
  }

  for {
    id <- Vector(
      "h2.client.headers-and-data",
      "h2.client.message-binary-data",
      "h2.client.unknown-connection-frame",
      "h2.client.reject-ping-length-nine",
    )
    window <- Vector(65535L, 0L)
  } test(s"$id checks a later receive-window setting of $window") {
    import Http2Wire._
    val body =
      if (id.contains("message")) ByteVector(0, 1, 127, 128, 255)
      else if (id.contains("headers-and-data")) Http1Wire.ascii("abc")
      else Http1Wire.ascii("control")
    def collect(peer: RawPeer[IO], bytes: ByteVector): IO[ResponseData[IO]] =
      readFrame(peer).flatMap { frame =>
        frame.frameType match {
          case Data =>
            val next = bytes ++ unpad(frame)
            if (frame.hasFlag(EndStream))
              if (id.contains("reject-ping"))
                writeFrame(peer, Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))) *>
                  peer.endOfOutput *> IO.raiseError(
                    new RuntimeException("native protocol rejection")
                  )
              else IO(assertEquals(next, body)).as(response(next))
            else collect(peer, next)
          case Ping if !frame.hasFlag(Ack) && frame.payload.size == 8 =>
            writeFrame(peer, frame.copy(flags = Ack)) *> collect(peer, bytes)
          case Settings if !frame.hasFlag(Ack) =>
            writeFrame(peer, settings(ack = true)) *> collect(peer, bytes)
          case _ => collect(peer, bytes)
        }
      }
    val run = runClient(id) { peer =>
      for {
        _ <- peer.write(clientPreface ++ encode(settings()))
        _ <- readUntil(peer, fromServer = true)(_.frameType == Settings)
        _ <- peer.write(
          encode(settings(ack = true)) ++ encode(settings(Vector(4 -> window))) ++
            encode(
              headers(
                1,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> "/",
                ),
              )
            )
        )
        result <- collect(peer, ByteVector.empty)
      } yield result
    }
    if (window == 0L) run.intercept[WireObservationLimit].void else run
  }

  for {
    id <- Vector(
      "h2.client.headers-and-data",
      "h2.client.hpack-literal-without-new-name",
      "h2.client.unknown-connection-frame",
      "h2.client.stream-credit-boundary",
      "h2.client.message-binary-data",
    )
    kind <- Vector("native", "deep", "suppressed", "adapter-and-timeout")
  } test(s"$id preserves positive-operation $kind provenance") {
    val timeout = new java.util.concurrent.TimeoutException("native timeout")
    val error: Throwable =
      if (kind == "deep")
        (1 to 32).foldLeft[Throwable](timeout)((cause, _) => new RuntimeException("wrapper", cause))
      else {
        val wrapper = new RuntimeException("native rejection")
        if (kind != "native") wrapper.addSuppressed(timeout)
        if (kind == "adapter-and-timeout")
          wrapper.addSuppressed(new AdapterFailure("fixture", new Exception("fixture")))
        wrapper
      }
    runClient(id)(_ => IO.raiseError(error)).attempt.map { result =>
      assertEquals(
        WireSuite.outcomeStatus(result),
        if (kind == "native") "fail"
        else if (kind == "adapter-and-timeout") "error"
        else "inconclusive",
      )
    }
  }

  for {
    role <- Vector("server", "client")
    suffix <- Vector(
      "message-binary-data",
      "headers-fragmented-end-stream",
      "headers-padding",
      "headers-padding-and-priority",
      "message-fragmented-trailers",
      "message-empty-trailers",
    )
    capacity <- Vector(4096L, 0L)
  } test(s"$role $suffix uses lawful literal blocks at table capacity $capacity") {
    import Http2Wire._
    val id = s"h2.$role.$suffix"
    if (role == "server") runPublishedServer(id) { (peer, publish) =>
      for {
        _ <- peer.readExact(clientPreface.size.toInt)
        _ <- readFrame(peer)
        _ <- peer.write(encode(settings(Vector(1 -> capacity))) ++ encode(settings(ack = true)))
        message <- readLiteralMessage(peer)
        (fields, body) = message
        _ <- publish(
          RequestData(
            method = fields.collectFirst { case (":method", value) => value }.get,
            path = fields.collectFirst { case (":path", value) => value }.get,
            headers = fields.filterNot(_._1.startsWith(":")),
            body = body,
          )
        )
        _ <- peer.write(
          encode(headers(1, Vector(":status" -> "200"), endStream = false)) ++
            encode(data(1, body))
        )
      } yield ()
    }
    else
      runClient(id) { peer =>
        for {
          _ <- peer.write(clientPreface ++ encode(settings(Vector(1 -> capacity))))
          _ <- readUntil(peer, fromServer = true)(_.frameType == Settings)
          _ <- peer.write(
            encode(settings(ack = true)) ++ encode(
              headers(
                1,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> "/mapping",
                ),
              )
            )
          )
          message <- readLiteralMessage(peer)
        } yield response(message._2)
      }
  }

  for {
    role <- Vector("server", "client")
    window <- Vector(8L, 9L)
  } test(s"$role padded DATA counts all nine flow-controlled octets at window $window") {
    import Http2Wire._
    val id = s"h2.$role.content-length-padded-data"
    val run = if (role == "server") runPublishedServer(id) { (peer, publish) =>
      for {
        _ <- peer.readExact(clientPreface.size.toInt)
        _ <- readFrame(peer)
        _ <- peer.write(encode(settings(Vector(4 -> window))) ++ encode(settings(ack = true)))
        message <- readLiteralMessage(peer)
        _ <- publish(RequestData(method = "GET", path = "/mapping", body = message._2))
        _ <- peer.write(
          encode(headers(1, Vector(":status" -> "200"), endStream = false)) ++
            encode(data(1, message._2))
        )
      } yield ()
    }
    else
      runClient(id) { peer =>
        for {
          _ <- peer.write(clientPreface ++ encode(settings(Vector(4 -> window))))
          _ <- readUntil(peer, fromServer = true)(_.frameType == Settings)
          _ <- peer.write(
            encode(settings(ack = true)) ++ encode(
              headers(
                1,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> "/mapping",
                ),
              )
            )
          )
          message <- readLiteralMessage(peer)
        } yield response(message._2)
      }
    if (window == 8L) run.intercept[WireObservationLimit].void else run
  }

  for (capacity <- Vector(4096L, 0L))
    test(s"state invalid-stream stimulus keeps HPACK valid at table capacity $capacity") {
      import Http2Wire._
      runServer("h2.server.skipped-stream-cannot-open") { peer =>
        for {
          _ <- peer.readExact(clientPreface.size.toInt)
          _ <- readFrame(peer)
          _ <- peer.write(encode(settings(Vector(1 -> capacity))) ++ encode(settings(ack = true)))
          first <- readUntil(peer, fromServer = false)(_.frameType == Headers)
          _ <- IO(assertEquals(first.payload.head, 0x20.toByte))
          _ <- writeFrame(
            peer,
            headers(first.streamId, Vector(":status" -> "200"), endStream = false),
          )
          second <- readUntil(peer, fromServer = false)(_.frameType == Headers)
          _ <- IO(assertEquals(second.payload.head, 0x20.toByte))
          _ <- writeFrame(peer, Frame(GoAway, 0, 0, uint32(3) ++ uint32(1)))
          _ <- peer.endOfOutput
        } yield ()
      }
    }

  /** The prefix assertion is independent of decoding: a zero maximum alone would not detect
    * an omitted mandatory encoder update. Inspect each raw section before collecting fragments.
    */
  private def readLiteralMessage(peer: RawPeer[IO]): IO[(Vector[(String, String)], ByteVector)] = {
    import Http2Wire._
    def loop(
        decoder: HpackWire.Decoder[IO],
        fields: Vector[(String, String)],
        body: ByteVector,
    ): IO[(Vector[(String, String)], ByteVector)] =
      readFrame(peer).flatMap { frame =>
        frame.frameType match {
          case Headers =>
            val unpadded = unpad(frame)
            val block = if (frame.hasFlag(0x20)) unpadded.drop(5) else unpadded
            IO(assertEquals(block.head, 0x20.toByte)) *>
              readHeaders(peer, frame, decoder).flatMap { decoded =>
                val initial = if (fields.isEmpty) decoded else fields
                if (frame.hasFlag(EndStream)) IO.pure((initial, body))
                else loop(decoder, initial, body)
              }
          case Data =>
            val next = body ++ unpad(frame)
            if (frame.hasFlag(EndStream)) IO.pure((fields, next)) else loop(decoder, fields, next)
          case Settings if !frame.hasFlag(Ack) =>
            writeFrame(peer, settings(ack = true)) *> loop(decoder, fields, body)
          case Ping if !frame.hasFlag(Ack) =>
            writeFrame(peer, frame.copy(flags = Ack)) *> loop(decoder, fields, body)
          case _ => loop(decoder, fields, body)
        }
      }
    HpackWire.decoder[IO](maxTableSize = 0).flatMap(loop(_, Vector.empty, ByteVector.empty))
  }

  for (
    id <- Vector(
      "h2.client.headers-and-data",
      "h2.client.message-binary-data",
      "h2.client.unknown-connection-frame",
    )
  )
    test(s"$id rejects interleaved SETTINGS before considering its DATA prerequisite") {
      import Http2Wire._
      runClient(id) { peer =>
        peer.write(clientPreface ++ encode(settings())) *>
          readUntil(peer, fromServer = true)(_.frameType == Settings) *>
          peer.write(
            encode(settings(ack = true)) ++
              encode(Frame(Headers, EndStream, 1, ByteVector(0x20))) ++
              encode(settings(Vector(4 -> 0L)))
          ) *> IO.never
      }.attempt.map(result => assertEquals(WireSuite.outcomeStatus(result), "fail"))
    }

  private val extension = Http2Wire.encode(Http2Wire.Frame(0xff, 0, 0, ByteVector.empty))
  private val boundedProgress = Vector.fill(1024)(extension).foldLeft(ByteVector.empty)(_ ++ _)

  for {
    role <- Vector("server", "client")
    suffix <- Vector("ignore-reserved-frame-bit", "ignore-ping-unused-flags", "ping-all-octets")
  } test(s"$role $suffix accepts reversed PING acknowledgment scheduling") {
    val id = s"h2.$role.$suffix"
    def reverse(peer: RawPeer[IO]): IO[Unit] = for {
      first <- Http2Wire.readUntil(peer, fromServer = role == "client")(
        _.frameType == Http2Wire.Ping
      )
      second <- Http2Wire.readUntil(peer, fromServer = role == "client")(
        _.frameType == Http2Wire.Ping
      )
      _ <- Http2Wire.writeFrame(peer, second.copy(flags = Http2Wire.Ack))
      _ <- Http2Wire.writeFrame(peer, first.copy(flags = Http2Wire.Ack))
    } yield ()
    if (role == "server") runServer(id) { peer =>
      Http2Wire.serverHandshake(peer) *> reverse(peer)
    }
    else
      runClient(id) { peer =>
        startRequest(peer) *> reverse(peer) *>
          Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers) *>
          Http2Wire.readUntil(peer, fromServer = true)(f =>
            f.frameType == Http2Wire.Data && f.hasFlag(Http2Wire.EndStream)
          ) *>
          IO.pure(response(Http1Wire.ascii("control")))
      }
  }

  for (
    suffix <- Vector(
      "reject-missing-status",
      "reject-content-length-underflow",
      "reject-content-length-mismatch",
    )
  )
    test(s"$suffix cannot pass through target cleanup after only a local API error") {
      for {
        failed <- Deferred[IO, Unit]
        released <- Ref.of[IO, Boolean](false)
        target = clientTarget(
          peer =>
            startRequest(peer) *>
              Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers) *>
              failed.complete(()).void *>
              IO.raiseError[ResponseData[IO]](new IllegalArgumentException("local API error")),
          peer => Resource.make((failed.get *> peer.write(boundedProgress)).start)(_.cancel).void,
          released.set(true),
        )
        transcript <- Transcript.create[IO]()
        error <- selected(s"h2.client.$suffix")
          .run(CaseContext(target, transcript))
          .intercept[WireObservationLimit]
        _ <- IO(assert(error.getMessage.contains("without required protocol rejection")))
        _ <- released.get.assertEquals(true)
      } yield ()
    }

  for (
    suffix <- Vector("update-above-advertised", "huffman-name-padding-eight", "huffman-name-eos")
  )
    test(s"HPACK $suffix remains the only malformed-field fault in the actual server case") {
      val id = s"h2.server.hpack-reject-$suffix"
      runPublishedServer(id) { (peer, publish) =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          request <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          block = request.payload
          // A narrowly forgiving decoder mutant removes only the fault named by this case.
          repaired = suffix match {
            case "update-above-advertised" =>
              block.drop(HpackWire.encodeInteger(4097, 5, 0x20).size)
            case "huffman-name-padding-eight" =>
              replace(block, "0082f8ff0161", "0001260161")
            case _ => replace(block, "0085f8ffffffff0161", "0001260161")
          }
          decoder <- HpackWire.decoder[IO]()
          fields <- decoder.decode(repaired)
          _ <- IO(assertEquals(fields.filter(_._1 == ":method"), Vector(":method" -> "GET")))
          _ <- IO(assert(fields.forall(_._1.nonEmpty)))
          _ <- publish(RequestData(headers = fields))
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.GoAway,
              0,
              0,
              Http2Wire.uint32(request.streamId.toLong) ++ Http2Wire.uint32(8),
            ),
          )
          // Remain open: fixture cleanup must not supply the rejection EOF.
        } yield ()
      }.intercept[ProtocolFailure]
        .flatMap(e =>
          IO(
            assertEquals(
              e.getMessage,
              if (suffix == "update-above-advertised") "malformed HPACK reached the application"
              else "compression failure used error code 8",
            )
          )
        )
    }

  test("PING absence probe exposes its bounded scheduling window") {
    // A late erroneous reply is deliberately scheduled after the barrier. This probe can
    // pass: the inventory must keep arbitrary delayed replies as a gap, never claim drainage.
    runServer("h2.server.ping-ack-no-response") { peer =>
      for {
        _ <- Http2Wire.serverHandshake(peer)
        ack <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
        barrier <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
        _ <- peer.write(
          Http2Wire.encode(barrier.copy(flags = Http2Wire.Ack)) ++
            Http2Wire.encode(ack)
        )
      } yield ()
    }
  }

  for (
    suffix <- Vector(
      "reject-missing-status",
      "reject-content-length-underflow",
      "reject-content-length-mismatch",
    )
  )
    test(s"$suffix accepts a wire reset before releasing the target") {
      runClient(s"h2.client.$suffix") { peer =>
        startRequest(peer) *>
          Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers) *>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.RstStream, 0, 1, Http2Wire.uint32(1)),
          ) *>
          IO.raiseError(new IllegalArgumentException("native rejection"))
      }
    }

  for (
    suffix <- Vector("update-above-advertised", "huffman-name-padding-eight", "huffman-name-eos")
  )
    test(s"HPACK $suffix remains the only malformed-field fault in the actual client case") {
      runClient(s"h2.client.hpack-reject-$suffix") { peer =>
        for {
          _ <- startRequest(peer)
          frame <- Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers)
          repaired = suffix match {
            case "update-above-advertised" =>
              frame.payload.drop(HpackWire.encodeInteger(4097, 5, 0x20).size)
            case "huffman-name-padding-eight" =>
              replace(frame.payload, "0082f8ff0161", "0001260161")
            case _ => replace(frame.payload, "0085f8ffffffff0161", "0001260161")
          }
          decoder <- HpackWire.decoder[IO]()
          fields <- decoder.decode(repaired)
          _ <- IO(assertEquals(fields.filter(_._1 == ":status"), Vector(":status" -> "200")))
          _ <- IO(assert(fields.forall(_._1.nonEmpty)))
        } yield response(ByteVector.empty)
      }.intercept[ProtocolFailure]
        .flatMap(e => IO(assertEquals(e.getMessage, "target client consumed malformed HPACK")))
    }

  test("HPACK response inspector ignores retired self-dependencies in PRIORITY and HEADERS") {
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          request <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          decoder <- HpackWire.decoder[IO]()
          fields <- Http2Wire.readHeaders(peer, request, decoder)
          _ <- publish(RequestData(headers = fields.filterNot(_._1.startsWith(":"))))
          dependency = Http2Wire.uint32(1) ++ ByteVector(0)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Priority, 0, 1, dependency))
          headers = Http2Wire.headers(1, Vector(":status" -> "200"))
          _ <- Http2Wire.writeFrame(
            peer,
            headers.copy(flags = headers.flags | 0x20, payload = dependency ++ headers.payload),
          )
        } yield ()
      }
      transcript <- Transcript.create[IO]()
      _ <- selected("h2.server.hpack-literal-without-new-name").run(
        CaseContext(fixture._1, transcript)
      )
    } yield ()
  }

  test("retention budget accepts its exact boundary and cannot overflow") {
    for {
      _ <- Http2ObservationBudget
        .reserve[IO](0L, Http2ObservationBudget.MaxBytes)
        .assertEquals(Http2ObservationBudget.MaxBytes)
      _ <- Http2ObservationBudget
        .reserve[IO](Http2ObservationBudget.MaxBytes, 1L)
        .intercept[WireObservationLimit]
      _ <- Http2ObservationBudget
        .reserve[IO](1L, Long.MaxValue)
        .intercept[WireObservationLimit]
    } yield ()
  }

  test("message content reserves bytes before retaining the terminal DATA frame") {
    runServer("h2.server.message-empty") { peer =>
      for {
        _ <- Http2Wire.serverHandshake(peer)
        _ <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
        _ <- Http2Wire.writeFrame(
          peer,
          Http2Wire.headers(1, Vector(":status" -> "200"), endStream = false),
        )
        _ <- (0 until 64).toVector.traverse_(index =>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.data(1, ByteVector.fill(16384)(0.toByte), endStream = index == 63),
          )
        )
      } yield ()
    }.intercept[WireObservationLimit]
      .flatMap(e => IO(assert(e.getMessage.contains("observation byte budget exceeded"))))
  }

  for {
    rst <- Vector(false, true)
    overBudget <- Vector(false, true)
  } test(s"control observer accounts for its terminal PING (reset=$rst, overBudget=$overBudget)") {
    Ref.of[IO, Option[Transcript[IO]]](None).flatMap { sent =>
      val run = runServer(
        if (rst) "h2.server.rst-cancel-no-response" else "h2.server.ping-all-octets",
        observer =>
          sent.get.flatMap(_.traverse_ { script =>
            (observer.render, script.render).tupled.flatMap { case (received, written) =>
              // Both transcripts fill the same 65536-byte prefix. Equal omitted counts
              // prove every written byte, including the terminal ACK, was observed.
              // This also prevents the reset case's lawful EOF alternative hiding it.
              IO(assertEquals(received.split("\n").last, written.split("\n").last))
            }
          }),
      ) { peer =>
        Ref.of[IO, String]("handshake").flatMap { stage =>
          (for {
            _ <- sent.set(Some(peer.transcript))
            _ <- Http2Wire.serverHandshake(peer)
            _ <-
              if (rst)
                Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers) *>
                  Http2Wire
                    .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.RstStream)
                    .void
              else IO.unit
            _ <- stage.set("await first PING")
            first <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
            _ <- stage.set("await second PING")
            second <-
              if (rst) IO.pure(first)
              else Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
            _ <- (0 until 63).toVector.traverse_(index =>
              stage.set(s"extension write $index of 63") *> Http2Wire
                .writeFrame(peer, Http2Wire.Frame(0xff, 0, 0, ByteVector.fill(16384)(0.toByte)))
            )
            // Leave eight bytes before the terminal 17-byte PING. Neither its payload nor
            // its frame header may escape the aggregate reservation on the completion path.
            _ <- stage.set("final extension write")
            lastSize = (if (rst) 15800 else 15783) - (if (overBudget) 0 else 17)
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.Frame(0xff, 0, 0, ByteVector.fill(lastSize.toLong)(0.toByte)),
            )
            _ <- stage.set("terminal PING acknowledgments")
            _ <-
              if (rst) IO.unit else Http2Wire.writeFrame(peer, first.copy(flags = Http2Wire.Ack))
            _ <- Http2Wire.writeFrame(peer, second.copy(flags = Http2Wire.Ack))
            _ <- stage.set("all bytes written; ending finite output")
            _ <- peer.endOfOutput
            _ <- stage.set("output ended")
          } yield ()).onCancel(
            stage.get
              .flatMap(value => IO.println(s"Canceled byte-budget script (reset=$rst): $value"))
          )
        }
      }
      if (overBudget)
        run
          .intercept[WireObservationLimit]
          .flatMap(e => IO(assert(e.getMessage.contains("observation byte budget exceeded"))))
      else run
    }
  }

  for (
    id <- Vector(
      "h2.client.reject-missing-status",
      "h2.client.reject-content-length-mismatch",
      "h2.client.hpack-reject-index-zero",
      "h2.client.reject-ping-length-seven",
      "h2.client.hpack-literal-without-new-name",
      "h2.client.ping-all-octets",
    )
  ) test(s"$id preserves a cause-wrapped adapter failure") {
    val failure =
      new AdapterFailure("fixture conversion", new IllegalArgumentException("conversion"))
    runClient(id)(peer =>
      startRequest(peer) *>
        IO.raiseError(new RuntimeException("native operation wrapper", failure))
    )
      .intercept[AdapterFailure]
      .flatMap(e => IO(assert(e eq failure)))
  }

  for (truncate <- Vector(false, true))
    test(s"smoke connection rejection validates the complete-frame boundary (truncate=$truncate)") {
      val run = runServer("h2.server.reject-ping-length") { peer =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          healthy <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
          _ <- Http2Wire.writeFrame(peer, healthy.copy(flags = Http2Wire.Ack))
          _ <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
          goaway = Http2Wire.encode(
            Http2Wire.Frame(Http2Wire.GoAway, 0, 0, Http2Wire.uint32(0) ++ Http2Wire.uint32(6))
          )
          _ <- peer.write(if (truncate) goaway.dropRight(1) else goaway)
          _ <- peer.endOfOutput
        } yield ()
      }
      if (truncate) run.intercept[WireDecodingFailure].void else run
    }

  test("canceling a held rejection observation releases the client") {
    for {
      localFailure <- Deferred[IO, Unit]
      released <- Ref.of[IO, Boolean](false)
      target = clientTarget(
        peer =>
          startRequest(peer) *>
            Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers) *>
            localFailure.complete(()).void *>
            IO.raiseError(new IllegalArgumentException("local API error")),
        onRelease = released.set(true),
      )
      transcript <- Transcript.create[IO]()
      run = selected("h2.client.reject-missing-status").run(CaseContext(target, transcript))
      _ <- Resource.make(run.start)(_.cancel).use { fiber =>
        localFailure.get *> fiber.cancel *> released.get.assertEquals(true)
      }
    } yield ()
  }

  for (suffix <- Vector("rst-cancel-no-response", "rst-unknown-error-no-response"))
    test(s"$suffix exposes its bounded scheduling window") {
      runServer(s"h2.server.$suffix") { peer =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          _ <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          reset <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.RstStream)
          barrier <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
          _ <- peer.write(
            Http2Wire.encode(barrier.copy(flags = Http2Wire.Ack)) ++
              Http2Wire.encode(reset.copy(flags = 0))
          )
        } yield ()
      }
    }

  for {
    role <- Vector("server", "client")
    suffix <- Vector(
      "padding-equals-payload",
      "padding-exceeds-payload",
      "missing-pad-length",
      "short-priority",
    )
    reject <- Vector(false, true)
  } test(s"$role HEADERS $suffix isolates its framing fault (reject=$reject)") {
    def receive(peer: RawPeer[IO]): IO[Unit] = for {
      initial <- Http2Wire.readUntil(peer, fromServer = role == "client")(
        _.frameType == Http2Wire.Headers
      )
      decoder <- HpackWire.decoder[IO]()
      fields <- Http2Wire.readHeaders(peer, initial, decoder)
      _ <- IO(assert(!initial.hasFlag(Http2Wire.EndStream)))
      _ <- IO(
        assertEquals(
          fields.filter(_._1.startsWith(":")).map(_._1).toSet,
          if (role == "server") Set(":method", ":scheme", ":authority", ":path") else Set(":status"),
        )
      )
      malformed <- Http2Wire.readUntil(peer, fromServer = role == "client")(
        _.frameType == Http2Wire.Headers
      )
      _ <- IO(assertEquals(malformed.streamId, initial.streamId))
      _ <- IO(assert(malformed.hasFlag(Http2Wire.EndStream)))
      _ <- IO(assert(malformed.hasFlag(Http2Wire.EndHeaders)))
      _ <- IO(
        assertEquals(
          malformed.payload,
          suffix match {
            case "padding-equals-payload" => ByteVector(1)
            case "padding-exceeds-payload" => ByteVector(2)
            case "missing-pad-length" => ByteVector.empty
            case _ => ByteVector(0, 0, 0, 0)
          },
        )
      )
      // A mutant forgiving only the truncated metadata/invalid padding obtains an
      // empty final trailer block, whose lack of mandatory pseudo-fields is valid.
      _ <- decoder.decode(ByteVector.empty).assertEquals(Vector.empty)
      _ <-
        if (reject)
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.GoAway,
              0,
              0,
              Http2Wire.uint32(0) ++ Http2Wire.uint32(
                if (suffix.startsWith("padding")) 1 else 6
              ),
            ),
          ) *> peer.endOfOutput
        else IO.unit
    } yield ()
    val id = s"h2.$role.reject-headers-$suffix"
    val run =
      if (role == "server")
        runPublishedServer(id) { (peer, publish) =>
          Http2Wire.serverHandshake(peer) *> receive(peer) *>
            (if (reject) IO.unit
             else
               publish(RequestData(path = "/mapping")) *>
                 Http2Wire.writeFrame(
                   peer,
                   Http2Wire
                     .Frame(Http2Wire.GoAway, 0, 0, Http2Wire.uint32(1) ++ Http2Wire.uint32(8)),
                 ))
        }
      else
        runClient(id)(peer =>
          startRequest(peer) *> receive(peer) *>
            (if (reject)
               IO.raiseError[ResponseData[IO]](
                 new IllegalArgumentException("native rejected response")
               )
             else IO.pure(response(ByteVector.empty)))
        )
    if (reject) run
    else
      run
        .intercept[ProtocolFailure]
        .flatMap(e =>
          IO(
            assert(
              e.getMessage.contains(
                if (role == "server") "incorrect GOAWAY error code"
                else "client consumed a deliberately malformed response"
              )
            )
          )
        )
  }

  for {
    suffix <- Vector("message-trailers", "message-fragmented-trailers")
    retain <- Vector(false, true)
  } test(s"$suffix permits unadvertised trailer discard (retain=$retain)") {
    runClient(s"h2.client.$suffix") { peer =>
      for {
        _ <- startRequest(peer)
        first <- Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers)
        decoder <- HpackWire.decoder[IO]()
        _ <- Http2Wire.readHeaders(peer, first, decoder)
        data <- Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Data)
        trailer <- Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers)
        fields <- Http2Wire.readHeaders(peer, trailer, decoder)
        _ <- IO(assert(trailer.hasFlag(Http2Wire.EndStream)))
      } yield response(data.payload).copy(trailers = IO.pure(if (retain) fields else Vector.empty))
    }
  }

  for (
    id <- Vector(
      "h2.server.priority-on-closed-stream",
      "h2.server.message-empty",
      "h2.server.hpack-literal-without-new-name",
      "h2.server.interaction-coalesced-preface-request",
    )
  ) {
    test(s"$id classifies malformed HPACK as protocol failure") {
      runServer(id) { peer =>
        Http2Wire.serverHandshake(peer) *>
          Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers) *>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Headers,
              Http2Wire.EndHeaders | Http2Wire.EndStream,
              1,
              ByteVector(0x80.toByte),
            ),
          )
      }.attempt.flatMap(result =>
        IO {
          assertEquals(WireSuite.outcomeStatus(result), "fail")
          assert(result.left.exists(_.getMessage.contains("invalid HPACK index")))
        }
      )
    }

    test(s"$id preserves decoded header observation limits") {
      // One valid :status followed by repeated legal accept-encoding fields. The
      // compressed bytes fit in two frames, but decoded RFC size exceeds 1 MiB.
      val block = ByteVector(0x88.toByte) ++ ByteVector.fill(20000)(0x90.toByte)
      runServer(id) { peer =>
        Http2Wire.serverHandshake(peer) *>
          Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers) *>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Headers,
              0,
              1,
              block.take(16384),
            ),
          ) *>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Continuation,
              Http2Wire.EndHeaders,
              1,
              block.drop(16384),
            ),
          ) *> Http2Wire.writeFrame(peer, Http2Wire.data(1, ByteVector.empty))
      }.intercept[WireObservationLimit]
        .flatMap(error => IO(assertEquals(WireSuite.outcomeStatus(Left(error)), "error")))
    }
  }

  for (limit <- Vector("encoded", "continuation"))
    test(s"HPACK case preserves $limit field-block observation limit") {
      runServer("h2.server.hpack-literal-without-new-name") { peer =>
        val frames = if (limit == "encoded") 65 else 1024
        val payload =
          if (limit == "encoded") ByteVector.fill(16384)(0x90.toByte)
          else ByteVector.empty
        Http2Wire.serverHandshake(peer) *>
          Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers) *>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Headers,
              Http2Wire.EndStream,
              1,
              ByteVector(0x88.toByte),
            ),
          ) *>
          (0 until frames).toVector.traverse_(index =>
            Http2Wire.writeFrame(
              peer,
              Http2Wire.Frame(
                Http2Wire.Continuation,
                if (index == frames - 1) Http2Wire.EndHeaders else 0,
                1,
                payload,
              ),
            )
          )
      }.intercept[WireObservationLimit]
        .flatMap(error => IO(assertEquals(WireSuite.outcomeStatus(Left(error)), "error")))
    }

  for (
    id <- Vector(
      "h2.client.headers-and-data",
      "h2.client.hpack-encoder-reduction-zero",
      "h2.client.hpack-encoder-minimum-before-restore",
    )
  )
    for (kind <- Vector("native", "adapter", "observation", "decoder"))
      test(s"$id retains the $kind failure category at the target operation") {
        val error: Throwable = kind match {
          case "native" => new IllegalStateException("native rejected valid input")
          case "adapter" =>
            new RuntimeException(
              "native wrapper",
              new AdapterFailure("fixture", new Exception("fixture")),
            )
          case "observation" => new WireObservationLimit("observer budget")
          case _ => new WireDecodingFailure("observer decoding")
        }
        runClient(id)(peer =>
          startRequest(
            peer,
            if (id.contains("hpack"))
              Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
            else Vector.empty,
          ) *>
            Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers) *>
            IO.raiseError(error)
        ).attempt.flatMap { result =>
          IO {
            assertEquals(
              WireSuite.outcomeStatus(result),
              if (kind == "adapter" || kind == "observation") "error" else "fail",
            )
            if (kind == "observation" || kind == "decoder")
              assert(result.left.exists(_ == error))
            if (kind == "native")
              assert(
                result.left.exists(_.getMessage.contains("native rejected valid input"))
              )
          }
        }
      }

  for {
    id <- Vector(
      "h2.client.headers-and-data",
      "h1.client.fixed-length",
      "h1.client.reject-conflicting-content-length",
      "h2.client.reject-content-length-mismatch",
    )
    wrapped <- Vector(false, true)
  } test(s"$id preserves native timeout as inconclusive (wrapped=$wrapped)") {
    val timeout = new java.util.concurrent.TimeoutException("native timeout")
    val error = if (wrapped) new RuntimeException("native wrapper", timeout) else timeout
    runClient(id)(_ => IO.raiseError(error)).attempt.flatMap(result =>
      IO(assertEquals(WireSuite.outcomeStatus(result), "inconclusive"))
    )
  }

  for (
    id <- Vector(
      "h2.server.ping-all-octets",
      "h2.server.rst-cancel-no-response",
      "h2.server.message-empty",
      "h2.server.hpack-literal-without-new-name",
      "h2.server.window-update-after-peer-end",
      "h2.server.interaction-coalesced-preface-request",
    )
  )
    test(s"$id reports legal frame count exhaustion as incomplete observation") {
      runServer(id) { peer =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          _ <-
            if (id.endsWith("ping-all-octets"))
              Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping).void
            else
              Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers).void
          _ <-
            if (id.endsWith("rst-cancel-no-response"))
              Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.RstStream).void
            else IO.unit
          _ <-
            if (id.endsWith("message-empty") || id.endsWith("window-update-after-peer-end"))
              Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(1, Vector(":status" -> "200"), endStream = false),
              )
            else IO.unit
          bytes =
            if (id.endsWith("message-empty"))
              Vector
                .fill(1024)(
                  Http2Wire.encode(Http2Wire.data(1, ByteVector.empty, endStream = false))
                )
                .foldLeft(ByteVector.empty)(_ ++ _)
            else
              Vector
                .fill(if (id.endsWith("window-update-after-peer-end")) 4096 else 1024)(extension)
                .foldLeft(ByteVector.empty)(_ ++ _)
          _ <- peer.write(bytes)
          _ <-
            if (id.endsWith("message-empty"))
              Http2Wire.writeFrame(peer, Http2Wire.data(1, ByteVector.empty))
            else IO.unit
        } yield ()
      }.intercept[WireObservationLimit]
        .flatMap(error => IO(assertEquals(WireSuite.outcomeStatus(Left(error)), "error")))
    }

  test("HPACK reserves its body limit before retaining terminal DATA") {
    runServer("h2.server.hpack-literal-without-new-name") { peer =>
      Http2Wire.serverHandshake(peer) *>
        Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers) *>
        Http2Wire.writeFrame(
          peer,
          Http2Wire.headers(1, Vector(":status" -> "200"), endStream = false),
        ) *>
        (0 until 5).toVector.traverse_(index =>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.data(1, ByteVector.fill(16384)(0.toByte), endStream = index == 4),
          )
        )
    }.intercept[WireObservationLimit].void
  }

  for (
    id <- Vector(
      "h2.server.priority-on-closed-stream",
      "h2.server.hpack-literal-without-new-name",
      "h2.server.skipped-stream-cannot-open",
    )
  ) test(s"$id preserves the redundant-zero HPACK integer observation limit") {
    runServer(id) { peer =>
      Http2Wire.serverHandshake(peer) *>
        Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers).flatMap {
          request =>
            Http2Wire.writeFrame(
              peer,
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream,
                request.streamId,
                ByteVector.fromValidHex("880f808080808000057574662d38"),
              ),
            )
        }
    }.intercept[WireObservationLimit].void
  }

  for {
    informational <- Vector(false, true)
    malformed <- Vector(false, true)
  } test(
    s"control response preserves independent fragmented trailers (informational=$informational, interrupted=$malformed)"
  ) {
    val run = runServer("h2.server.default-frame-size-splits-data") { peer =>
      for {
        _ <- Http2Wire.serverHandshake(peer)
        request <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
        // Incremental x:a in 103, then dynamic index 62 in the final response.
        _ <-
          if (informational)
            Http2Wire.writeFrame(
              peer,
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders,
                request.streamId,
                ByteVector.fromValidHex("08033130334001780161"),
              ),
            )
          else IO.unit
        _ <- Http2Wire.writeFrame(
          peer,
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders,
            request.streamId,
            ByteVector.fromValidHex(if (informational) "88be" else "884001780161"),
          ),
        )
        _ <- ByteVector
          .fill(40000)(42.toByte)
          .grouped(16384)
          .toVector
          .traverse_(bytes =>
            Http2Wire.writeFrame(peer, Http2Wire.data(request.streamId, bytes, endStream = false))
          )
        // A table update is legal at the start of this independent trailer block.
        _ <- Http2Wire.writeFrame(
          peer,
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndStream,
            request.streamId,
            ByteVector.fromValidHex("20"),
          ),
        )
        _ <-
          if (malformed)
            Http2Wire.writeFrame(
              peer,
              Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector.fill(8)(0.toByte)),
            )
          else IO.unit
        _ <- Http2Wire.writeFrame(
          peer,
          Http2Wire.Frame(
            Http2Wire.Continuation,
            Http2Wire.EndHeaders,
            request.streamId,
            ByteVector.fromValidHex("0001780162"),
          ),
        )
      } yield ()
    }
    if (malformed) run.intercept[ProtocolFailure].void else run
  }

  for (
    id <- Vector("h2.server.hpack-literal-without-new-name", "h2.server.skipped-stream-cannot-open")
  )
    test(s"$id accepts informational then final headers sharing the dynamic table") {
      runPublishedServer(id) { (peer, publish) =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          request <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Headers,
              Http2Wire.EndHeaders,
              request.streamId,
              ByteVector.fromValidHex("08033130334001780161"),
            ),
          )
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Headers,
              Http2Wire.EndHeaders,
              request.streamId,
              ByteVector.fromValidHex("88be"),
            ),
          )
          _ <-
            if (id.contains("hpack"))
              Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.Headers,
                  Http2Wire.EndHeaders | Http2Wire.EndStream,
                  request.streamId,
                  ByteVector.fromValidHex("be"),
                ),
              ) *>
                publish(RequestData(method = "GET", path = "/", headers = Vector("x" -> "wire")))
            else
              Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers) *>
                Http2Wire.writeFrame(
                  peer,
                  Http2Wire
                    .Frame(Http2Wire.GoAway, 0, 0, Http2Wire.uint32(3) ++ Http2Wire.uint32(1)),
                ) *> peer.endOfOutput
        } yield ()
      }
    }

  for (
    id <- Vector("h2.server.hpack-literal-without-new-name", "h2.server.skipped-stream-cannot-open")
  )
    test(s"$id bounds informational sections without a false protocol failure") {
      runServer(id) { peer =>
        Http2Wire.serverHandshake(peer) *>
          Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers).flatMap {
            request =>
              val interim = Http2Wire.encode(
                Http2Wire.headers(request.streamId, Vector(":status" -> "103"), endStream = false)
              )
              peer.write(Vector.fill(1024)(interim).foldLeft(ByteVector.empty)(_ ++ _))
          }
      }.intercept[WireObservationLimit].void
    }

  for (termination <- Vector("interleaving", "eof"))
    test(s"control END_STREAM cannot hide an incomplete trailer block ($termination)") {
      runServer("h2.server.default-frame-size-splits-data") { peer =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          _ <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.headers(1, Vector(":status" -> "200"), endStream = false),
          )
          _ <- ByteVector
            .fill(40000)(42.toByte)
            .grouped(16384)
            .toVector
            .traverse_(bytes =>
              Http2Wire.writeFrame(peer, Http2Wire.data(1, bytes, endStream = false))
            )
          // Empty trailers still require END_HEADERS: the old concatenating reader
          // passed after this frame without observing its promised CONTINUATION.
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndStream, 1, ByteVector.empty),
          )
          _ <-
            if (termination == "eof") peer.endOfOutput
            else
              Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector.fill(8)(0.toByte)),
              )
        } yield ()
      }.attempt.flatMap(result => IO(assertEquals(WireSuite.outcomeStatus(result), "fail")))
    }

  for {
    suffix <- Vector("hpack-encoder-reduction-zero", "hpack-encoder-minimum-before-restore")
    lateUpdate <- Vector(false, true)
  } test(s"$suffix checks table updates in the first informational block (late=$lateUpdate)") {
    val run = runServer(s"h2.server.$suffix") { peer =>
      val fields =
        Vector(":status" -> "200", "x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
      val informational = HpackWire.encodeLiteral(Vector(":status" -> "103"))
      def head(stream: Int, block: ByteVector, end: Boolean): IO[Unit] =
        Http2Wire.writeFrame(
          peer,
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders | (if (end) Http2Wire.EndStream else 0),
            stream,
            block,
          ),
        )
      for {
        _ <- Http2Wire.serverHandshake(peer)
        first <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
        // The precondition must use capacity after all sections, not this interim zero.
        _ <- head(first.streamId, ByteVector(0x20) ++ informational, end = false)
        _ <- head(
          first.streamId,
          HpackWire.encodeInteger(4096, 5, 0x20) ++ HpackWire.encodeLiteral(fields),
          end = true,
        )
        _ <- IO
          .defer {
            Http2Wire.readUntil(peer, fromServer = false)(frame =>
              frame.frameType == Http2Wire.Settings && !frame.hasFlag(Http2Wire.Ack)
            ) *>
              Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
          }
          .replicateA_(if (suffix.endsWith("zero")) 1 else 2)
        second <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
        _ <- head(
          second.streamId,
          (if (lateUpdate) ByteVector.empty else ByteVector(0x20)) ++ informational,
          end = false,
        )
        _ <- head(
          second.streamId,
          (if (lateUpdate) ByteVector(0x20) else ByteVector.empty) ++ HpackWire.encodeLiteral(
            fields
          ),
          end = true,
        )
      } yield ()
    }
    if (lateUpdate)
      run
        .intercept[ProtocolFailure]
        .flatMap(error =>
          IO(assertEquals(error.getMessage, "encoder omitted the required minimum-zero update"))
        )
    else run
  }

  for {
    role <- Vector("server", "client")
    suffix <- Vector("stream-credit-boundary", "connection-credit-independent")
    schedule <- Vector("whole", "split", "ack-first", "overspend", "padding")
  } test(s"measured flow credit: $role/$suffix/$schedule") {
    val run = creditSchedule(role, suffix, schedule)
    if (schedule == "overspend" || schedule == "padding")
      run
        .intercept[ProtocolFailure]
        .flatMap(error => IO(assert(error.getMessage.contains("exceeds remaining credit"))))
    else run
  }

  for {
    role <- Vector("server", "client")
    schedule <- Vector(
      "whole",
      "split",
      "inflight",
      "retained",
      "bounded-retained",
      "negative-overspend",
      "zero-overspend",
      "one-overspend",
      "padding",
      "missing-prefix",
    )
  } test(s"measured flow credit: $role/negative-stream-window/$schedule") {
    val run = creditSchedule(role, "negative-stream-window", schedule)
    if (schedule == "missing-prefix") run.intercept[WireObservationLimit].void
    else if (schedule.endsWith("overspend") || schedule == "padding")
      run
        .intercept[ProtocolFailure]
        .flatMap(error => IO(assert(error.getMessage.contains("exceeds remaining credit"))))
    else run
  }

  for {
    role <- Vector("server", "client")
    schedule <- Vector("exhausted", "padding-only", "clamped-negative")
  } test(s"independent negative credit: $role/$schedule") {
    val run = negativeCreditSender(role, schedule)
    if (schedule == "clamped-negative")
      run
        .intercept[ProtocolFailure]
        .flatMap(error =>
          IO(assertEquals(error.getMessage, "DATA payload 1 exceeds remaining credit 0"))
        )
    else run
  }

  // Unlike creditSchedule's deliberate wire mutations, this sender releases DATA
  // only when BOTH windows allow it. Padding spends credit without consuming body.
  private def negativeCreditSender(role: String, schedule: String): IO[Unit] = {
    import Http2Wire._
    def script(peer: RawPeer[IO]): IO[Unit] =
      Ref.of[IO, (Long, Long, Long)]((65535L, 65535L, 65536L)).flatMap { budget =>
        def send(size: Long): IO[Unit] = budget.get.flatMap {
          case (stream, connection, remaining) =>
            IO(assert(size <= stream && size <= connection && size <= remaining)) *>
              budget.set((stream - size, connection - size, remaining - size)) *>
              ByteVector.fill(size)(0x61.toByte).grouped(16384).toVector.zipWithIndex.traverse_ {
                case (bytes, index) =>
                  writeFrame(
                    peer,
                    data(1, bytes, endStream = size == remaining && (index + 1) * 16384 >= size),
                  )
              }
        }
        def flush: IO[Unit] = budget.get.flatMap { case (stream, connection, remaining) =>
          val size = math.max(0L, math.min(remaining, math.min(stream, connection)))
          if (size == 0L) IO.unit else send(size)
        }
        def loop: IO[Unit] = readFrame(peer).flatMap { frame =>
          frame.frameType match {
            case Settings if !frame.hasFlag(Ack) =>
              IO(assertEquals(frame, settings(Vector(4 -> 0L)))) *>
                budget.update { case (stream, connection, remaining) =>
                  val reduced = stream - 65535L
                  (
                    if (schedule == "clamped-negative") math.max(0L, reduced) else reduced,
                    connection,
                    remaining,
                  )
                } *> writeFrame(peer, settings(ack = true)) *> loop
            case WindowUpdate =>
              val increment = readUint32(frame.payload) & 0x7fffffffL
              budget.update { case (stream, connection, remaining) =>
                if (frame.streamId == 0) (stream, connection + increment, remaining)
                else (stream + increment, connection, remaining)
              } *> flush *> loop
            case Ping if !frame.hasFlag(Ack) =>
              flush *> writeFrame(peer, frame.copy(flags = Ack)) *> loop
            case Headers if role == "client" => IO.unit
            case Settings if frame.hasFlag(Ack) => loop
            case _ => IO.raiseError(new AssertionError(s"unexpected sender input $frame"))
          }
        }
        val fields =
          if (role == "server") Vector(":status" -> "200")
          else
            Vector(
              ":method" -> "POST",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/",
              "content-length" -> "65536",
            )
        for {
          _ <-
            if (role == "server")
              serverHandshake(peer) *>
                readUntil(peer, fromServer = false)(_.frameType == Headers).void
            else clientHandshake(peer)
          _ <- writeFrame(peer, headers(1, fields, endStream = false))
          _ <-
            if (schedule == "padding-only")
              (Vector.fill(255)(256) :+ 255).traverse_ { size =>
                writeFrame(
                  peer,
                  Frame(
                    Data,
                    Padded,
                    1,
                    ByteVector((size - 1).toByte) ++ ByteVector.fill(size.toLong - 1L)(0.toByte),
                  ),
                )
              } *> budget.set((0L, 0L, 65536L))
            else send(65535L)
          _ <- loop
        } yield ()
      }
    if (role == "server") runServer(s"h2.$role.negative-stream-window")(script)
    else
      runClient(s"h2.$role.negative-stream-window")(peer =>
        script(peer).as(response(ByteVector.empty))
      )
  }

  // A lawful sender with independent stream and connection windows. These controls
  // spend padding without advancing the expected application body, including the
  // second response on a connection and the negative-window one-credit phase.
  for {
    role <- Vector("server", "client")
    suffix <-
      if (role == "server") stateCompletionServerCases
      else
        Vector(
          "stream-credit-boundary",
          "connection-credit-independent",
          "negative-stream-window",
          "maximum-stream-window-update",
        )
    mode <-
      if (suffix == "negative-stream-window") Vector("one", "final", "exhausted-one")
      else if (suffix == "window-update-on-closed-stream") Vector("first", "second")
      else Vector("first")
  } test(s"lawful completion padding: $role/$suffix/$mode") {
    completionPaddingSender(role, suffix, mode)
  }

  private def stateCompletionServerCases: Vector[String] = Vector(
    "window-update-after-peer-end",
    "stream-credit-boundary",
    "connection-credit-independent",
    "negative-stream-window",
    "maximum-stream-window-update",
    "reset-closes-blocked-stream",
    "window-update-on-closed-stream",
    "peer-concurrency-zero-allows-responses",
  )

  private def completionPaddingSender(
      role: String,
      suffix: String,
      mode: String,
      connectionGrant: Long => IO[Unit] = _ => IO.unit,
  ): IO[Unit] = {
    import Http2Wire._
    final case class Sender(
        initial: Long = 65535L,
        streamCredit: Long = 65535L,
        connectionCredit: Long = 65535L,
        stream: Int = 0,
        body: ByteVector = ByteVector.empty,
        padding: Long = 0L,
        ended: Boolean = false,
        reduced: Boolean = false,
        latePadding: Boolean = false,
    )
    val negative = suffix == "negative-stream-window"
    val early =
      if (mode == "early-reset") 65535
      else if (mode.startsWith("early-")) mode.stripPrefix("early-").toInt
      else -1
    val content =
      if (negative || suffix == "connection-credit-independent")
        ByteVector.fill(65536)(0x61.toByte)
      else Http1Wire.ascii("abcdefghijklmnop")
    def script(peer: RawPeer[IO]): IO[Unit] = {
      def settingsState(s: Sender, frame: Frame): Sender =
        frame.payload.grouped(6).foldLeft(s) { (current, bytes) =>
          val key = ((bytes(0) & 255) << 8) | (bytes(1) & 255)
          val value = readUint32(bytes.drop(2))
          if (key == 4)
            current.copy(
              initial = value,
              streamCredit = current.streamCredit + value - current.initial,
              reduced = negative && value == 0L,
            )
          else current
        }
      def produce(s: Sender): IO[Sender] = {
        val credit = math.max(0L, math.min(s.streamCredit, s.connectionCredit))
        if (s.stream == 0 || s.ended || credit == 0L) IO.pure(s)
        else if (s.padding > 0L) {
          val size = math.min(256L, math.min(credit, s.padding))
          writeFrame(
            peer,
            Frame(
              Data,
              Padded,
              s.stream,
              ByteVector((size - 1).toByte) ++ ByteVector.fill(size - 1)(0.toByte),
            ),
          ) *>
            IO.defer(
              produce(
                s.copy(
                  streamCredit = s.streamCredit - size,
                  connectionCredit = s.connectionCredit - size,
                  padding = s.padding - size,
                )
              )
            )
        } else if (negative && !s.reduced) IO.pure(s)
        else if (s.body.nonEmpty) {
          val bytes = s.body.take(math.min(16384L, credit))
          val end = bytes.size == s.body.size
          writeFrame(peer, data(s.stream, bytes, endStream = end)) *>
            IO.defer(
              produce(
                s.copy(
                  streamCredit = s.streamCredit - bytes.size,
                  connectionCredit = s.connectionCredit - bytes.size,
                  body = s.body.drop(bytes.size),
                  ended = end,
                )
              )
            )
        } else IO.pure(s)
      }
      def open(s: Sender, stream: Int): Sender = s.copy(
        stream = stream,
        streamCredit = s.initial,
        body = content,
        ended = false,
        padding =
          if (negative) { if (mode == "exhausted-one") 65535L else 1L }
          else if (mode == "second" && stream == 1) 0L
          else 65535L,
      )
      def loop(s: Sender): IO[Unit] = readFrame(peer).flatMap { frame =>
        frame.frameType match {
          case Settings if !frame.hasFlag(Ack) =>
            writeFrame(peer, settings(ack = true)) *> loop(settingsState(s, frame))
          case Settings => loop(s)
          case Headers if role == "server" =>
            writeFrame(
              peer,
              headers(frame.streamId, Vector(":status" -> "200"), endStream = false),
            ) *>
              produce(open(s, frame.streamId)).flatMap(loop)
          case Headers => IO.unit
          case WindowUpdate =>
            val increment = readUint32(frame.payload) & 0x7fffffffL
            val next =
              if (frame.streamId == 0) s.copy(connectionCredit = s.connectionCredit + increment)
              else if (frame.streamId == s.stream && !s.ended)
                s.copy(streamCredit = s.streamCredit + increment)
              else s
            val observed = if (frame.streamId == 0) connectionGrant(increment) else IO.unit
            // A maximum-stream test must not receive a second overflowing grant.
            observed *> IO(
              assert(
                increment > 0L && next.streamCredit <= Int.MaxValue &&
                  next.connectionCredit <= Int.MaxValue
              )
            ) *>
              produce(
                if (
                  negative && next.reduced && !next.latePadding &&
                  ((mode != "final" && next.streamCredit == 1L) ||
                    (mode == "final" && next.streamCredit > 1L))
                )
                  next.copy(padding = 1L, latePadding = true)
                else next
              ).flatMap(loop)
          case Ping if !frame.hasFlag(Ack) =>
            writeFrame(peer, frame.copy(flags = Ack)) *> loop(s)
          case RstStream => loop(s.copy(body = ByteVector.empty, padding = 0L, ended = true))
          case _ => IO.raiseError(new AssertionError(s"unexpected completion sender input $frame"))
        }
      }
      if (role == "server") for {
        preface <- peer.readExact(clientPreface.size.toInt)
        _ <- IO(assertEquals(preface, clientPreface))
        initial <- readFrame(peer)
        _ <- writeFrame(peer, settings()) *> writeFrame(peer, settings(ack = true))
        _ <- loop(settingsState(Sender(), initial))
      } yield ()
      else
        for {
          _ <- peer.write(clientPreface ++ encode(settings()))
          initial <- readFrame(peer)
          _ <- if (early < 0) writeFrame(peer, settings(ack = true)) else IO.unit
          _ <- writeFrame(
            peer,
            headers(
              1,
              Vector(
                ":method" -> "POST",
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> "/",
                "content-length" -> content.size.toString,
              ),
              endStream = false,
            ),
          )
          opened = open(Sender(), 1)
          _ <-
            if (early == 0) writeFrame(peer, data(1, ByteVector.empty, endStream = false))
            else IO.unit
          spent <-
            if (early > 0) produce(opened.copy(padding = early.toLong, body = ByteVector.empty))
            else IO.pure(opened)
          _ <- if (early >= 0) writeFrame(peer, settings(ack = true)) else IO.unit
          start =
            if (early >= 0)
              settingsState(
                spent.copy(body = content, padding = if (early > 0) 0L else opened.padding),
                initial,
              )
            else open(settingsState(Sender(), initial), 1)
          _ <- produce(
            if (mode == "early-reset")
              start.copy(streamCredit = start.initial, connectionCredit = 65535L, padding = 65535L)
            else start
          ).flatMap(loop)
        } yield ()
    }
    if (role == "server") runServer(s"h2.$role.$suffix")(script)
    else runClient(s"h2.$role.$suffix")(peer => script(peer).as(response(ByteVector.empty)))
  }

  for {
    suffix <- Vector(
      "stream-credit-boundary",
      "connection-credit-independent",
      "negative-stream-window",
      "maximum-stream-window-update",
    )
    prefix <- Vector(0, 5, 65535)
  } test(s"Streams setup carries early DATA: $suffix/$prefix") {
    completionPaddingSender("client", suffix, s"early-$prefix")
  }

  for (
    suffix <- Vector(
      "stream-credit-boundary",
      "connection-credit-independent",
      "negative-stream-window",
    )
  )
    test(s"Streams setup rejects lost padding debit: $suffix") {
      completionPaddingSender("client", suffix, "early-reset")
        .intercept[ProtocolFailure]
        .flatMap(error => IO(assert(error.getMessage.contains("exceeds remaining credit"))))
    }

  // Internal bookkeeping regression: this fixture chooses its grant; no sender emission
  // or universal receiver grant amount is required by the protocol.
  test("Streams maximum update retains setup connection debit in its completion grant") {
    for {
      grants <- Ref.of[IO, Vector[Long]](Vector.empty)
      _ <- completionPaddingSender(
        "client",
        "maximum-stream-window-update",
        "early-0",
        n => grants.update(_ :+ n),
      )
      _ <- completionPaddingSender(
        "client",
        "maximum-stream-window-update",
        "early-65535",
        n => grants.update(_ :+ n),
      )
      observed <- grants.get
      _ <- IO {
        assertEquals(observed.size, 2)
        assertEquals(observed(1) - observed(0), 65535L)
      }
    } yield ()
  }

  for {
    suffix <- Vector(
      "zero-stream-window-update",
      "stream-window-overflow",
      "settings-stream-window-overflow",
    )
    prefix <- Vector(0, 5, 65535)
    mode <- Vector("reset", "close", "wrong-code", "body", "end", "over-credit")
    if suffix != "settings-stream-window-overflow" || mode != "reset"
  } test(s"Streams gated error DATA: $suffix/$prefix/$mode") {
    import Http2Wire._
    val run = runClient(s"h2.client.$suffix") { peer =>
      def padding(size: Int): IO[Unit] =
        if (size == 0) writeFrame(peer, data(1, ByteVector.empty, endStream = false))
        else
          (0 until size by 256).toVector.traverse_ { offset =>
            val n = math.min(256, size - offset)
            writeFrame(
              peer,
              Frame(
                Data,
                Padded,
                1,
                ByteVector((n - 1).toByte) ++ ByteVector.fill(n.toLong - 1L)(0.toByte),
              ),
            )
          }
      for {
        _ <- peer.write(clientPreface ++ encode(settings()))
        _ <- readFrame(peer)
        _ <- writeFrame(
          peer,
          headers(
            1,
            Vector(
              ":method" -> "POST",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/",
              "content-length" -> "16",
            ),
            endStream = false,
          ),
        )
        _ <- padding(prefix)
        _ <- writeFrame(peer, settings(ack = true))
        first <- readUntil(peer, fromServer = true)(_.frameType == WindowUpdate)
        _ <- IO(
          assertEquals(
            readUint32(first.payload),
            if (suffix == "zero-stream-window-update") 0L else Int.MaxValue.toLong,
          )
        )
        // Spend the full independently available connection remainder between updates.
        _ <-
          if (suffix == "zero-stream-window-update") padding(0)
          else if (mode == "body") padding(0)
          else padding(65535 - prefix)
        second <- if (suffix == "zero-stream-window-update") IO.pure(first) else readFrame(peer)
        _ <- IO {
          if (suffix == "stream-window-overflow") assertEquals(second, windowUpdate(1, 65536))
          if (suffix == "settings-stream-window-overflow")
            assertEquals(second, settings(Vector(4 -> 65536L)))
        }
        _ <- mode match {
          case "body" => writeFrame(peer, data(1, ByteVector(97), endStream = false))
          case "end" => writeFrame(peer, data(1, ByteVector.empty))
          case "over-credit" => padding(1)
          case _ => padding(0)
        }
        _ <-
          if (
            mode == "reset" || (mode == "wrong-code" && suffix != "settings-stream-window-overflow")
          )
            writeFrame(peer, Frame(RstStream, 0, 1, uint32(if (mode == "wrong-code") 8 else 1)))
          else
            writeFrame(
              peer,
              Frame(GoAway, 0, 0, uint32(0) ++ uint32(if (mode == "wrong-code") 8 else 1)),
            ) *> peer.endOfOutput
        result <- IO.raiseError[ResponseData[IO]](new RuntimeException("native rejection"))
      } yield result
    }
    if (mode == "wrong-code" || mode == "body" || mode == "end" || mode == "over-credit")
      run.intercept[ProtocolFailure].flatMap { error =>
        IO {
          val message = error.getMessage
          assert(
            if (mode == "wrong-code")
              message.contains("unexpected RST_STREAM") || message.contains("unexpected GOAWAY")
            else if (mode == "end") message.contains("prematurely ended")
            else if (mode == "over-credit") message.contains("exceeds remaining credit")
            else
              message.contains("exceeds remaining credit") || message
                .contains("gated upload content")
          )
        }
      }
    else run
  }

  for {
    suffix <- Vector("idle-data", "idle-window-update")
    stream <- Vector(1, Int.MaxValue)
  } test(s"Streams idle identifier: $suffix/$stream") {
    import Http2Wire._
    runClient(s"h2.client.$suffix") { peer =>
      for {
        _ <- clientHandshake(peer)
        _ <- writeFrame(
          peer,
          headers(
            stream,
            Vector(
              ":method" -> "POST",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/",
            ),
          ),
        )
        stimulus <- readUntil(peer, fromServer = true)(_.frameType != Settings)
        _ <- IO(
          assertEquals(
            stimulus,
            if (suffix == "idle-data") data(2, ByteVector.empty) else windowUpdate(2, 1),
          )
        )
        _ <- writeFrame(peer, Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))) *> peer.endOfOutput
        result <- IO.raiseError[ResponseData[IO]](new RuntimeException("native rejection"))
      } yield result
    }
  }

  private val statePushCallers = Vector(
    "window-update-after-peer-end" -> Vector(4 -> 0L),
    "stream-credit-boundary" -> Vector(4 -> 3L),
    "connection-credit-independent" -> Vector(4 -> 131072L),
    "negative-stream-window" -> Vector(4 -> 65535L),
    "maximum-stream-window-update" -> Vector(4 -> 0L),
    "reset-closes-blocked-stream" -> Vector(4 -> 0L),
    "window-update-on-closed-stream" -> Vector.empty[(Int, Long)],
    "peer-concurrency-zero-allows-responses" -> Vector(3 -> 0L),
  )

  for {
    caller <- statePushCallers
    mode <- Vector("obey", "push-before-head", "push-after-head", "invalid-ping")
  } test(s"State push capability: ${caller._1}/$mode") {
    val run = statePushSender(caller._1, caller._2, mode)
    if (mode == "push-before-head")
      run
        .intercept[WireDecodingFailure]
        .flatMap(error =>
          IO(assertEquals(error.getMessage, "unexpected HTTP/2 frame type 5 on stream 1"))
        )
    else if (mode == "push-after-head")
      run
        .intercept[ProtocolFailure]
        .flatMap(error => IO(assert(error.getMessage.contains("frame type 5 on stream 1"))))
    else if (mode == "invalid-ping")
      run
        .intercept[ProtocolFailure]
        .flatMap(error => IO(assertEquals(error.getMessage, "invalid PING")))
    else run
  }

  private def statePushSender(
      suffix: String,
      expectedSettings: Vector[(Int, Long)],
      mode: String,
  ): IO[Unit] =
    runServer(s"h2.server.$suffix")(statePushScript(suffix, expectedSettings, mode))

  for {
    caller <- statePushCallers.filter { case (suffix, _) =>
      suffix == "window-update-after-peer-end" || suffix == "peer-concurrency-zero-allows-responses"
    }
    partialFrame <- Vector(false, true)
  } test(s"State push capability EOF: ${caller._1}/partial-frame=$partialFrame") {
    import Http2Wire._
    val input = clientPreface ++ encode(settings(caller._2 :+ (2 -> 0L))) ++
      encode(headers(1, Vector(":method" -> "GET"))) ++ encode(windowUpdate(1, 16)) ++
      (if (partialFrame) ByteVector(0) else ByteVector.empty)
    for {
      fixture <- ScriptedPeer.peer(input, fragment = 1)
      (peer, output) = fixture
      run = statePushScript(caller._1, caller._2, "obey")(peer)
      _ <- if (partialFrame) run.intercept[WireDecodingFailure].void else run
      frames <- output.flatMap(bytes => IO.fromEither(decodeAll(bytes)))
    } yield assertEquals(
      frames.filter(_.frameType == Data),
      Vector(data(1, Http1Wire.ascii("abcdefghijklmnop"))),
    )
  }

  private def statePushScript(
      suffix: String,
      expectedSettings: Vector[(Int, Long)],
      mode: String,
  ): RawPeer[IO] => IO[Unit] = {
    import Http2Wire._
    final case class Sender(
        initial: Long = 65535L,
        connection: Long = 65535L,
        stream: Int = 0,
        window: Long = 65535L,
        body: ByteVector = ByteVector.empty,
        pushEnabled: Boolean = true,
    )
    peer => {
      def flush(state: Sender): IO[Sender] = {
        val size = math.max(0L, math.min(16384L, math.min(state.window, state.connection)))
        val bytes = state.body.take(size)
        if (bytes.isEmpty) IO.pure(state)
        else
          writeFrame(peer, data(state.stream, bytes, endStream = bytes.size == state.body.size)) *>
            flush(
              state.copy(
                window = state.window - bytes.size,
                connection = state.connection - bytes.size,
                body = state.body.drop(bytes.size),
              )
            )
      }
      def settingsState(frame: Frame, state: Sender): Sender =
        frame.payload.grouped(6).foldLeft(state) { (current, bytes) =>
          val key = ((bytes(0) & 255) << 8) | (bytes(1) & 255)
          val value = readUint32(bytes.drop(2))
          if (key == 2) current.copy(pushEnabled = value != 0L)
          else if (key == 4)
            current.copy(initial = value, window = current.window + value - current.initial)
          else current
        }
      def push(stream: Int): IO[Unit] = writeFrame(
        peer,
        Frame(
          PushPromise,
          EndHeaders,
          stream,
          uint32(2) ++ literalFields(
            Vector(
              ":method" -> "GET",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/asset",
            )
          ),
        ),
      )
      // The oracle closes its peer before the server resource cancels this loop.
      // Treat closure between frames as normal; partial frames must still fail.
      def loop(state: Sender): IO[Unit] = readFrameOrClose(peer).flatMap {
        case None => IO.unit
        case Some(frame) =>
          frame.frameType match {
            case Settings if !frame.hasFlag(Ack) =>
              writeFrame(peer, settings(ack = true)) *> flush(settingsState(frame, state)).flatMap(
                loop
              )
            case Headers =>
              val body =
                if (suffix == "negative-stream-window" || suffix == "connection-credit-independent")
                  ByteVector.fill(65536)(0x61.toByte)
                else Http1Wire.ascii("abcdefghijklmnop")
              val next = state.copy(stream = frame.streamId, window = state.initial, body = body)
              (if (mode == "push-before-head" || state.pushEnabled) push(frame.streamId)
               else IO.unit) *>
                writeFrame(
                  peer,
                  headers(frame.streamId, Vector(":status" -> "200"), endStream = false),
                ) *>
                (if (mode == "push-after-head") push(frame.streamId) else IO.unit) *>
                (if (mode == "invalid-ping") writeFrame(peer, Frame(Ping, 0, 0, ByteVector(0)))
                 else IO.unit) *>
                flush(next).flatMap(loop)
            case Ping if !frame.hasFlag(Ack) =>
              writeFrame(peer, frame.copy(flags = Ack)) *> loop(state)
            case WindowUpdate =>
              val increment = readUint32(frame.payload) & 0x7fffffffL
              flush(
                if (frame.streamId == 0) state.copy(connection = state.connection + increment)
                else if (frame.streamId == state.stream)
                  state.copy(window = state.window + increment)
                else state
              ).flatMap(loop)
            case RstStream => loop(state.copy(body = ByteVector.empty))
            case _ => loop(state)
          }
      }
      for {
        preface <- peer.readExact(clientPreface.size.toInt)
        _ <- IO(assertEquals(preface, clientPreface))
        first <- readFrame(peer)
        // This is the first frame after the preface, before any request. Preserve
        // each caller's settings in the same frame and require one no-push value.
        _ <- IO(assertEquals(first, settings(expectedSettings :+ (2 -> 0L))))
        _ <- writeFrame(peer, settings()) *> writeFrame(peer, settings(ack = true))
        _ <- loop(settingsState(first, Sender()))
      } yield ()
    }
  }

  private def creditSchedule(role: String, suffix: String, schedule: String): IO[Unit] = {
    import Http2Wire._
    val negative = suffix == "negative-stream-window"
    val connection = suffix == "connection-credit-independent"
    val body =
      if (negative || connection) ByteVector.fill(65536)(0x61.toByte)
      else Http1Wire.ascii("abcdefghijklmnop")
    def script(peer: RawPeer[IO]): IO[Unit] = {
      def next(kind: Int): IO[Frame] =
        readUntil(peer, fromServer = role == "client")(_.frameType == kind)
      def send(bytes: ByteVector, end: Boolean = false): IO[Unit] =
        bytes.grouped(16384).toVector.zipWithIndex.traverse_ { case (chunk, index) =>
          writeFrame(peer, data(1, chunk, endStream = end && (index + 1) * 16384 >= bytes.size))
        }
      def ping(beforeAck: IO[Unit] = IO.unit, afterAck: IO[Unit] = IO.unit): IO[Unit] =
        next(Ping).flatMap(frame =>
          beforeAck *> writeFrame(peer, frame.copy(flags = Ack)) *> afterAck
        )
      def update(stream: Int, increment: Long): IO[Unit] =
        next(WindowUpdate).flatMap(frame =>
          IO(assertEquals(frame, windowUpdate(stream, increment.toInt)))
        )
      val head =
        if (role == "server") Vector(":status" -> "200")
        else
          Vector(
            ":method" -> "POST",
            ":scheme" -> "http",
            ":authority" -> "wire.test",
            ":path" -> "/",
            "content-length" -> body.size.toString,
          )
      val initial =
        if (negative)
          schedule match {
            case "split" => 65534L
            case "whole" => 49152L
            case "inflight" => 16384L
            case "missing-prefix" => 0L
            case _ => 1L
          }
        else if (schedule == "ack-first") 0L
        else if (connection) if (schedule == "whole") 49152L else 65535L
        else if (schedule == "whole") 0L
        else 3L
      val inFlight = if (schedule == "inflight") 16384L else 0L
      val one = if (schedule == "split") 1L else 0L
      for {
        _ <-
          if (role == "server") serverHandshake(peer) *> next(Headers).void
          else clientHandshake(peer)
        _ <- writeFrame(peer, headers(1, head, endStream = false))
        _ <- send(body.take(initial))
        _ <-
          if (schedule == "missing-prefix")
            // No positive payload: exhaust the existing frame observation bound,
            // rather than using a timeout as evidence of a protocol violation.
            (0 until 4096).toVector
              .traverse_(_ => writeFrame(peer, data(1, ByteVector.empty, endStream = false)))
          else IO.unit
        _ <-
          if (negative)
            for {
              _ <- ping()
              _ <- update(0, 65536)
              changed <- next(Settings)
              _ <- IO(assertEquals(changed, settings(Vector(4 -> 0L))))
              // These bytes were queued under the old window and precede its ACK.
              _ <- send(body.slice(initial, initial + inFlight))
              _ <- writeFrame(peer, settings(ack = true))
              _ <- ping(
                if (schedule == "negative-overspend") send(ByteVector(0x61)) else IO.unit
              )
              _ <- update(1, initial + inFlight)
              _ <- ping(
                if (schedule == "zero-overspend") send(ByteVector(0x61)) else IO.unit
              )
              _ <- update(1, 1)
              _ <- ping(
                if (schedule == "one-overspend") send(ByteVector(0x61, 0x61))
                else if (schedule == "padding")
                  writeFrame(peer, Frame(Data, Padded, 1, ByteVector(1, 0)))
                else send(body.slice(initial + inFlight, initial + inFlight + one))
              )
              remaining = body.drop(initial + inFlight + one)
              budget = remaining.size + 4096L * 256L
              _ <- update(0, budget)
              _ <- update(1, budget - (1L - one))
              _ <-
                if (schedule == "bounded-retained")
                  // Exactly 4096 lawful frames spend all B = R + 4096*256
                  // payload octets. One old credit octet survives: resetting the
                  // reader to the increment B-1 rejects the final legal frame.
                  (0 until 4096).toVector.traverse_ { index =>
                    val content = remaining.slice(index * 16L, (index + 1) * 16L)
                    writeFrame(
                      peer,
                      Frame(
                        Data,
                        Padded | (if (index == 4095) EndStream else 0),
                        1,
                        ByteVector(255.toByte) ++ content ++ ByteVector.fill(255)(0.toByte),
                      ),
                    )
                  }
                else if (schedule == "retained")
                  // Keep the original partial-padding retained-credit control;
                  // bounded-retained above exercises the new exact grant boundary.
                  writeFrame(
                    peer,
                    Frame(Data, Padded, 1, ByteVector(0) ++ remaining.take(16383)),
                  ) *>
                    send(remaining.drop(16383), end = true)
                else send(remaining, end = true)
            } yield ()
          else
            for {
              _ <- ping(
                if (!connection && schedule == "overspend") send(ByteVector(0x61))
                else if (!connection && schedule == "padding")
                  writeFrame(peer, Frame(Data, Padded, 1, ByteVector(0)))
                else IO.unit
              )
              _ <-
                if (connection)
                  update(1, 1) *> next(Settings).flatMap { frame =>
                    IO(assertEquals(frame, settings(Vector(4 -> 262144L)))) *>
                      writeFrame(peer, settings(ack = true))
                  } *> ping(
                    if (schedule == "overspend") send(ByteVector(0x61))
                    else if (schedule == "padding")
                      writeFrame(peer, Frame(Data, Padded, 1, ByteVector(0)))
                    else IO.unit
                  )
                else IO.unit
              budget = body.size - initial + 4096L * 256L
              _ <- update(if (connection) 1 else 0, budget)
              _ <- update(
                if (connection) 0 else 1,
                budget -
                  (if (connection) 65535L - initial else 3L - initial),
              )
              _ <- send(body.drop(initial), end = true)
            } yield ()
        _ <- if (role == "client") next(Headers).void else IO.unit
      } yield ()
    }
    if (role == "server") runServer(s"h2.$role.$suffix")(script)
    else runClient(s"h2.$role.$suffix")(peer => script(peer).as(response(ByteVector.empty)))
  }

  for {
    role <- Vector("server", "client")
    ending <- Vector(
      "data",
      "empty",
      "dynamic",
      "fragmented",
      "pseudo",
      "no-end",
      "interleaved",
      "over-credit",
    )
  } test(s"$role stream-credit boundary validates $ending completion with connection HPACK state") {
    def script(peer: RawPeer[IO]): IO[Unit] = {
      import Http2Wire._
      val fields =
        if (role == "server") Vector(":status" -> "200")
        else
          Vector(
            ":method" -> "POST",
            ":scheme" -> "http",
            ":authority" -> "wire.test",
            ":path" -> "/",
            "content-length" -> "16",
          )
      // Insert an entry in the initial section and reference it only from trailers.
      val entry = HpackWire.encodeLiteral(Vector("x-context" -> "retained"))
      val head = HpackWire.encodeLiteral(fields) ++ ByteVector(0x40) ++ entry.drop(1)
      val trailer =
        if (ending == "pseudo") ByteVector(0x88)
        else if (ending == "empty") ByteVector.empty
        else ByteVector(0xbe)
      for {
        _ <-
          if (role == "server")
            serverHandshake(peer) *> readUntil(peer, fromServer = false)(
              _.frameType == Headers
            ).void
          else clientHandshake(peer)
        _ <- writeFrame(peer, Frame(Headers, EndHeaders, 1, head))
        _ <- writeFrame(
          peer,
          data(
            1,
            Http1Wire.ascii(if (ending == "over-credit") "abcd" else "abc"),
            endStream = false,
          ),
        )
        ping <- readUntil(peer, fromServer = role == "client")(f =>
          f.frameType == Ping && !f.hasFlag(Ack)
        )
        _ <- writeFrame(peer, ping.copy(flags = Ack))
        _ <- readUntil(peer, fromServer = role == "client")(f =>
          f.frameType == WindowUpdate && f.streamId == 1
        )
        _ <- writeFrame(
          peer,
          data(1, Http1Wire.ascii("defghijklmnop"), endStream = ending == "data"),
        )
        _ <-
          if (ending == "data") IO.unit
          else if (ending == "fragmented" || ending == "interleaved")
            writeFrame(peer, Frame(Headers, EndStream, 1, ByteVector.empty)) *>
              writeFrame(
                peer,
                if (ending == "interleaved") Frame(Ping, 0, 0, ByteVector.fill(8)(0.toByte))
                else Frame(Continuation, EndHeaders, 1, trailer),
              )
          else
            writeFrame(
              peer,
              Frame(Headers, EndHeaders | (if (ending == "no-end") 0 else EndStream), 1, trailer),
            )
        _ <-
          if (role == "client") readUntil(peer, fromServer = true)(_.frameType == Headers).void
          else IO.unit
      } yield ()
    }
    val run =
      if (role == "server") runServer("h2.server.stream-credit-boundary")(script)
      else
        runClient("h2.client.stream-credit-boundary")(peer =>
          script(peer).as(response(ByteVector.empty))
        )
    if (
      ending == "pseudo" || ending == "no-end" || ending == "interleaved" || ending == "over-credit"
    )
      run.attempt.flatMap(result => IO(assertEquals(WireSuite.outcomeStatus(result), "fail")))
    else run
  }

  for {
    suffix <- Vector("hpack-encoder-reduction-zero", "hpack-encoder-minimum-before-restore")
    behavior <- Vector(
      "zero-trailer",
      "nonzero-trailer",
      "restore-trailer",
      "missing-minimum",
      "invalid-index",
    )
  } test(s"client $suffix observes full first-request capacity ($behavior)") {
    import Http2Wire._
    for {
      exchanges <- Ref.of[IO, Int](0)
      result <- runClient(s"h2.client.$suffix") { peer =>
        exchanges.getAndUpdate(_ + 1).flatMap { n =>
          val fields = Vector(
            ":method" -> "GET",
            ":scheme" -> "http",
            ":authority" -> "wire.test",
            ":path" -> "/",
            "content-length" -> "0",
            "x-first" -> "one",
            "x-second" -> "two",
            "x-third" -> "three",
          )
          val firstPrefix =
            if (behavior == "restore-trailer") ByteVector(0x20) else ByteVector.empty
          val secondPrefix =
            if (behavior == "missing-minimum") ByteVector.empty
            else
              ByteVector(0x20) ++ (if (suffix.endsWith("restore"))
                                     HpackWire.encodeInteger(128, 5, 0x20)
                                   else ByteVector.empty)
          val trailer = behavior match {
            case "zero-trailer" => ByteVector(0x20)
            case "invalid-index" => ByteVector(0xbe)
            case _ => HpackWire.encodeInteger(128, 5, 0x20)
          }
          for {
            _ <- if (n == 0) clientHandshake(peer) else IO.unit
            _ <- writeFrame(
              peer,
              Frame(
                Headers,
                EndHeaders | (if (n == 0) 0 else EndStream),
                2 * n + 1,
                (if (n == 0) firstPrefix else secondPrefix) ++ HpackWire.encodeLiteral(fields),
              ),
            )
            _ <-
              if (n == 0) writeFrame(peer, Frame(Headers, EndHeaders | EndStream, 1, trailer))
              else IO.unit
            _ <- readUntil(peer, fromServer = true)(_.frameType == Headers)
            _ <-
              if (n == 0)
                IO.defer {
                  readUntil(peer, fromServer = true)(f =>
                    f.frameType == Settings && !f.hasFlag(Ack)
                  ) *> writeFrame(
                    peer,
                    settings(ack = true),
                  )
                }.replicateA_(if (suffix.endsWith("zero")) 1 else 2)
              else IO.unit
          } yield response(ByteVector.empty)
        }
      }.attempt
      _ <- IO {
        if (behavior == "zero-trailer") {
          assertEquals(WireSuite.outcomeStatus(result), "error")
          assert(result.left.exists(_.getMessage.contains("already chose zero capacity")))
        } else if (behavior == "missing-minimum" || behavior == "invalid-index")
          assertEquals(WireSuite.outcomeStatus(result), "fail")
        else assertEquals(result, Right(()))
      }
    } yield ()
  }

  for (ending <- Vector("valid", "pseudo", "interleaved", "duplicate", "data-after"))
    test(s"closed-stream flow retains trailer HPACK state across responses ($ending)") {
      import Http2Wire._
      val run = runServer("h2.server.window-update-on-closed-stream") { peer =>
        val entry = HpackWire.encodeLiteral(Vector("x-trailer" -> "retained"))
        for {
          _ <- serverHandshake(peer)
          _ <- readUntil(peer, fromServer = false)(_.frameType == Headers)
          _ <- writeFrame(
            peer,
            Frame(Headers, EndHeaders, 1, HpackWire.encodeLiteral(Vector(":status" -> "200"))),
          )
          _ <- writeFrame(peer, data(1, Http1Wire.ascii("abcdefghijklmnop"), endStream = false))
          _ <- writeFrame(peer, Frame(Headers, EndStream, 1, ByteVector(0x40) ++ entry.drop(1)))
          _ <- writeFrame(
            peer,
            if (ending == "interleaved") Frame(Ping, 0, 0, ByteVector.fill(8)(0.toByte))
            else
              Frame(
                Continuation,
                EndHeaders,
                1,
                if (ending == "pseudo") ByteVector(0x88) else ByteVector.empty,
              ),
          )
          ping <- readUntil(peer, fromServer = false)(f => f.frameType == Ping && !f.hasFlag(Ack))
          _ <-
            if (ending == "duplicate") writeFrame(peer, headers(1, Vector.empty))
            else if (ending == "data-after") writeFrame(peer, data(1, ByteVector.empty))
            else IO.unit
          _ <- writeFrame(peer, ping.copy(flags = Ack))
          _ <- readUntil(peer, fromServer = false)(_.frameType == Headers)
          // The next response depends on the entry inserted by the first response's trailers.
          _ <- writeFrame(peer, Frame(Headers, EndHeaders, 3, ByteVector(0x88, 0xbe)))
          _ <- writeFrame(peer, data(3, Http1Wire.ascii("abcdefghijklmnop"), endStream = false))
          _ <- writeFrame(peer, headers(3, Vector.empty))
        } yield ()
      }
      if (ending == "valid") run
      else run.attempt.flatMap(result => IO(assertEquals(WireSuite.outcomeStatus(result), "fail")))
    }

  for (budget <- Vector("bytes", "frames"))
    test(s"flow trailer inspection retains the $budget observation bound") {
      import Http2Wire._
      runServer("h2.server.peer-concurrency-zero-allows-responses") { peer =>
        for {
          _ <- serverHandshake(peer)
          _ <- readUntil(peer, fromServer = false)(_.frameType == Headers)
          _ <- writeFrame(peer, headers(1, Vector(":status" -> "200"), endStream = false))
          _ <- writeFrame(peer, data(1, Http1Wire.ascii("abcdefghijklmnop"), endStream = false))
          _ <- writeFrame(peer, Frame(Headers, EndStream, 1, ByteVector.empty))
          _ <- IO
            .defer {
              writeFrame(
                peer,
                Frame(
                  Continuation,
                  0,
                  1,
                  if (budget == "bytes") ByteVector.fill(16384)(0.toByte) else ByteVector.empty,
                ),
              )
            }
            .replicateA_(if (budget == "bytes") 65 else 1024)
        } yield ()
      }.intercept[WireObservationLimit].void
    }

  for (
    ending <- Vector(
      "none",
      "reset",
      "empty-data",
      "empty-trailers",
      "dynamic-trailers",
      "nonempty-data",
      "padded-data",
      "post-barrier-data",
      "wrong-stream-reset",
      "short-reset",
      "bytes",
      "frames",
    )
  ) test(s"reset drain preserves the permitted race and observation bounds ($ending)") {
    import Http2Wire._
    val run = runServer("h2.server.reset-closes-blocked-stream") { peer =>
      val entry = HpackWire.encodeLiteral(Vector("x-trailer" -> "retained"))
      for {
        _ <- serverHandshake(peer)
        _ <- readUntil(peer, fromServer = false)(_.frameType == Headers)
        _ <- writeFrame(
          peer,
          Frame(Headers, EndHeaders, 1, HpackWire.encodeLiteral(Vector(":status" -> "200"))),
        )
        _ <- readUntil(peer, fromServer = false)(_.frameType == RstStream)
        ping <- readUntil(peer, fromServer = false)(f => f.frameType == Ping && !f.hasFlag(Ack))
        _ <- ending match {
          case "reset" => writeFrame(peer, Frame(RstStream, 0, 1, uint32(8)))
          case "wrong-stream-reset" => writeFrame(peer, Frame(RstStream, 0, 3, uint32(8)))
          case "short-reset" => writeFrame(peer, Frame(RstStream, 0, 1, ByteVector(8)))
          case "empty-data" => writeFrame(peer, data(1, ByteVector.empty))
          case "nonempty-data" => writeFrame(peer, data(1, ByteVector(1)))
          case "padded-data" => writeFrame(peer, Frame(Data, Padded | EndStream, 1, ByteVector(0)))
          case "empty-trailers" => writeFrame(peer, headers(1, Vector.empty))
          case "dynamic-trailers" =>
            writeFrame(peer, Frame(Headers, EndStream, 1, ByteVector(0x40))) *>
              writeFrame(peer, Frame(Continuation, EndHeaders, 1, entry.drop(1)))
          case "bytes" | "frames" =>
            writeFrame(peer, Frame(Headers, EndStream, 1, ByteVector.empty)) *>
              IO.defer {
                writeFrame(
                  peer,
                  Frame(
                    Continuation,
                    0,
                    1,
                    if (ending == "bytes") ByteVector.fill(16384)(0.toByte) else ByteVector.empty,
                  ),
                )
              }.replicateA_(if (ending == "bytes") 65 else 4096)
          case _ => IO.unit
        }
        _ <- writeFrame(peer, ping.copy(flags = Ack))
        _ <- readUntil(peer, fromServer = false)(_.frameType == Headers)
        _ <-
          if (ending == "post-barrier-data") writeFrame(peer, data(1, ByteVector(1))) else IO.unit
        _ <- writeFrame(
          peer,
          if (ending == "dynamic-trailers") Frame(Headers, EndHeaders, 3, ByteVector(0x88, 0xbe))
          else headers(3, Vector(":status" -> "200"), endStream = false),
        )
        _ <- readUntil(peer, fromServer = false)(f =>
          f.frameType == WindowUpdate && f.streamId == 3
        )
        _ <- writeFrame(peer, data(3, Http1Wire.ascii("abcdefghijklmnop")))
        lastPing <- readUntil(peer, fromServer = false)(f => f.frameType == Ping && !f.hasFlag(Ack))
        _ <- writeFrame(peer, lastPing.copy(flags = Ack))
      } yield ()
    }
    run.attempt.map { result =>
      val expected = ending match {
        case "bytes" | "frames" => "error"
        case "nonempty-data" | "padded-data" | "post-barrier-data" | "wrong-stream-reset" |
            "short-reset" =>
          "fail"
        case _ => "pass"
      }
      assertEquals(WireSuite.outcomeStatus(result), expected)
      if (expected == "error")
        assert(result.left.exists(_.isInstanceOf[WireObservationLimit]))
      if (ending == "nonempty-data" || ending == "padded-data")
        assert(result.left.exists(_.getMessage.contains("exceeds remaining credit")))
    }
  }

  for (exposure <- Vector("split", "joined", "rejected"))
    test(s"split Cookie wire acceptance does not prescribe an application context ($exposure)") {
      import Http2Wire._
      val run = runPublishedServer("h2.server.request-split-cookie") { (peer, publish) =>
        for {
          _ <- serverHandshake(peer)
          message <- readLiteralMessage(peer)
          (fields, body) = message
          _ <- IO(
            assertEquals(
              fields.collect { case ("cookie", value) => value },
              Vector("a=b", "c=d", "e=f"),
            )
          )
          _ <- publish(
            RequestData(
              method = fields.collectFirst { case (":method", value) => value }.get,
              path = fields.collectFirst { case (":path", value) => value }.get,
              headers =
                if (exposure == "joined") Vector("cookie" -> "a=b; c=d; e=f")
                else fields.filterNot(_._1.startsWith(":")),
              body = body,
            )
          )
          _ <- writeFrame(
            peer,
            headers(1, Vector(":status" -> (if (exposure == "rejected") "400" else "200"))),
          )
        } yield ()
      }
      run.attempt.map(result =>
        assertEquals(
          WireSuite.outcomeStatus(result),
          if (exposure == "rejected") "fail" else "pass",
        )
      )
    }

  test("admission counts both stream ends and permits existing streams after a reduction") {
    import Http2Wire._
    val first =
      Http2PeerSettings
        .State(concurrent = 2)
        .sending(headers(1, Vector.empty), true)
        .fold(error => fail(s"unexpected failure: $error"), identity)
    val two = first
      .sending(headers(3, Vector.empty, endStream = false), true)
      .fold(error => fail(s"unexpected failure: $error"), identity)
      .received(settings(Vector(3 -> 1L)))
    assertEquals(two.streams.size, 2)
    assert(two.sending(headers(5, Vector.empty), true).isLeft)
    val one = two.received(headers(1, Vector.empty))
    assertEquals(one.streams.keySet, Set(3))
    // Existing stream 3 can still send through END_STREAM under the reduced limit.
    val halfClosed = one
      .sending(data(3, ByteVector.empty), true)
      .fold(error => fail(s"unexpected failure: $error"), identity)
    assert(halfClosed.sending(headers(5, Vector.empty), true).isLeft)
    val closed = halfClosed.received(data(3, ByteVector.empty))
    assertEquals(
      closed
        .sending(headers(5, Vector.empty), true)
        .fold(error => fail(s"unexpected failure: $error"), identity)
        .streams
        .keySet,
      Set(5),
    )
    val reset = two
      .sending(Frame(RstStream, 0, 1, uint32(8)), true)
      .fold(error => fail(s"unexpected failure: $error"), identity)
      .received(Frame(RstStream, 0, 3, uint32(8)))
    assertEquals(
      reset
        .sending(headers(5, Vector.empty), true)
        .fold(error => fail(s"unexpected failure: $error"), identity)
        .streams
        .keySet,
      Set(5),
    )
    val zeroThenOne = closed.received(settings(Vector(3 -> 0L, 3 -> 1L)))
    assertEquals(zeroThenOne.concurrent, 1L)
    assertEquals(
      zeroThenOne
        .sending(headers(5, Vector.empty), true)
        .fold(error => fail(s"unexpected failure: $error"), identity)
        .streams
        .size,
      1,
    )
  }

  test("field-section accounting includes every UTF-8 octet and repeated occurrence") {
    val utf8 = ByteVector
      .encodeUtf8("é")
      .fold(error => fail(s"unexpected failure: $error"), identity)
      .toArray
      .map(b => (b & 0xff).toChar)
      .mkString
    val fields = Vector("x" -> utf8, "x" -> utf8, "empty" -> "")
    HpackWire.decoder[IO]().flatMap(_.decode(HpackWire.encodeLiteral(fields))).map { decoded =>
      assertEquals(Http2PeerSettings.fieldSectionSize(decoded), 35L + 35L + 37L)
    }
  }

  for (mode <- Vector("unchanged", "duplicate", "separate"))
    test(s"actual client HPACK reuse retains interval minimum ($mode)") {
      import Http2Wire._
      for {
        count <- Ref.of[IO, Int](0)
        heads <- Ref.of[IO, Int](0)
        decoder <- HpackWire.decoder[IO]()
        result <- runClient("h2.client.hpack-dynamic-stream-reuse") { peer =>
          for {
            index <- count.getAndUpdate(_ + 1)
            _ <-
              if (index == 0) clientHandshake(peer)
              else
                mode match {
                  case "duplicate" => writeFrame(peer, settings(Vector(1 -> 0L, 1 -> 4096L)))
                  case "separate" =>
                    writeFrame(peer, settings(Vector(1 -> 0L))) *>
                      writeFrame(peer, settings(Vector(1 -> 4096L)))
                  case _ => IO.unit
                }
            _ <- writeFrame(
              peer,
              headers(
                index * 2 + 1,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> "/",
                ),
              ),
            )
            first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
            _ <- heads.update(_ + 1)
            fields <- readHeaders(peer, first, decoder)
          } yield response(ByteVector.empty).copy(headers = fields.filterNot(_._1.startsWith(":")))
        }.attempt
        sent <- heads.get
        _ <- IO {
          if (mode == "unchanged") {
            assertEquals(result, Right(()))
            assertEquals(sent, 2)
          } else {
            assert(result.left.exists(_.isInstanceOf[WireObservationLimit]))
            assertEquals(sent, 1)
          }
        }
      } yield ()
    }

  for (
    id <- Vector(
      "h2.server.default-frame-size-splits-data",
      "h2.server.stream-credit-boundary",
      "h2.server.interaction-interleaved-request-data",
      "h2.server.hpack-literal-without-new-name",
      "h2.server.headers-fragmented-end-stream",
      "h2.server.message-trailers",
    )
  ) test(s"$id checks a zero advisory header budget before request bytes") {
    import Http2Wire._
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, _) =>
        peer.readExact(clientPreface.size.toInt) *> readFrame(peer) *>
          peer.write(encode(settings(Vector(6 -> 0L))) ++ encode(settings(ack = true))) *> IO.never
      }
      trace <- Transcript.create[IO]()
      error <- selected(id).run(CaseContext(fixture._1, trace)).intercept[WireObservationLimit]
      sent <- sentFrames(trace)
      _ <- IO {
        assert(error.getMessage.contains("MAX_HEADER_LIST_SIZE"))
        assert(!sent.exists(_.frameType == Headers))
      }
    } yield ()
  }

  test("a fragmented terminal field section releases admission only at END_HEADERS") {
    import Http2Wire._
    val open =
      Http2PeerSettings
        .State(concurrent = 1)
        .sending(headers(1, Vector.empty), true)
        .fold(error => fail(s"unexpected failure: $error"), identity)
    val pending = open.received(Frame(Headers, EndStream, 1, ByteVector.empty))
    assert(pending.sending(headers(3, Vector.empty), true).isLeft)
    val closed = pending.received(Frame(Continuation, EndHeaders, 1, ByteVector.empty))
    assertEquals(
      closed
        .sending(headers(3, Vector.empty), true)
        .fold(error => fail(s"unexpected failure: $error"), identity)
        .streams
        .keySet,
      Set(3),
    )
  }

  for (
    id <- Vector(
      "h2.client.reject-push-stream-zero",
      "h2.client.reject-push-odd-promised-stream",
      "h2.client.reject-push-unsafe-method",
      "h2.client.reject-push-request-content",
      "h2.client.reject-push-missing-method",
      "h2.client.reject-push-invalid-field",
      "h2.client.reject-push-unauthoritative-origin",
    )
  ) test(s"$id does not hide its intended fault behind an oversized promise") {
    import Http2Wire._
    runClient(id) { peer =>
      peer.write(clientPreface ++ encode(settings(Vector(6 -> 0L)))) *>
        readUntil(peer, fromServer = true)(f => f.frameType == Settings && !f.hasFlag(Ack)) *>
        writeFrame(peer, settings(ack = true)) *>
        writeFrame(
          peer,
          headers(
            1,
            Vector(
              ":method" -> "GET",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/",
            ),
          ),
        ) *> IO.never[ResponseData[IO]]
    }.intercept[WireObservationLimit]
      .map(error => assert(error.getMessage.contains("MAX_HEADER_LIST_SIZE")))
  }

  for {
    later <- Vector(false, true)
    limit <- Vector(0L, 41L, 42L, 43L)
  }
    test(s"actual client response advisory size is exact ($later/$limit)") {
      import Http2Wire._
      val run = runClient("h2.client.message-empty") { peer =>
        for {
          _ <- peer.write(
            clientPreface ++ encode(settings(if (later) Vector.empty else Vector(6 -> limit)))
          )
          _ <- readUntil(peer, fromServer = true)(f => f.frameType == Settings && !f.hasFlag(Ack))
          _ <- writeFrame(peer, settings(ack = true))
          _ <- if (later) writeFrame(peer, settings(Vector(6 -> limit))) else IO.unit
          _ <- writeFrame(
            peer,
            headers(
              1,
              Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> "/mapping",
              ),
            ),
          )
          first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
          decoder <- HpackWire.decoder[IO]()
          _ <- readHeaders(peer, first, decoder)
        } yield response(ByteVector.empty)
      }
      if (limit < 42L) run.intercept[WireObservationLimit].void else run
    }

  private val finiteFramingCases = Vector(
    ("headers-padding-equals-payload", (2, 1L)),
    ("headers-padding-exceeds-payload", (2, 1L)),
    ("headers-missing-pad-length", (2, 6L)),
    ("continuation-stream-zero", (2, 1L)),
    ("continuation-different-stream", (3, 1L)),
    ("headers-interrupted-by-ping", (3, 1L)),
    ("continuation-interrupted-by-settings", (4, 1L)),
  )

  private val finiteFramingTag = new munit.Tag("finite-framing")

  private def finiteFramingRun(
      role: String,
      suffix: String,
      count: Int,
      code: Long,
      limit: Long,
      mode: String = "connection",
  ): IO[(Either[Throwable, Unit], Vector[Http2Wire.Frame])] = {
    import Http2Wire._
    import scala.concurrent.duration._
    def stimulus(peer: RawPeer[IO], responded: Deferred[IO, Unit]): IO[Unit] = {
      def first: IO[Frame] = readFrame(peer).flatMap { frame =>
        if (frame.frameType == Headers) IO.pure(frame) else first
      }
      for {
        head <- first
        rest <- Vector.fill(count - 1)(readFrame(peer)).sequence
        decoder <- HpackWire.decoder[IO]()
        fields <- decoder.decode(head.payload)
        _ <- IO(
          assertEquals(
            Http2PeerSettings.fieldSectionSize(fields),
            if (role == "client") 42L else 181L,
          )
        )
        _ <- IO {
          assertEquals(
            rest.last.frameType,
            if (suffix.startsWith("headers-padding") || suffix == "headers-missing-pad-length")
              Headers
            else Continuation,
          )
          assert(rest.last.hasFlag(EndHeaders))
          assertEquals(
            head.hasFlag(EndHeaders),
            suffix.startsWith("headers-padding") || suffix == "headers-missing-pad-length",
          )
        }
        reply = mode match {
          case "stream-only" => Frame(RstStream, 0, 1, uint32(code))
          case "malformed-peer" => Frame(GoAway, 0, 1, uint32(0) ++ uint32(code))
          case "wrong-code" => Frame(GoAway, 0, 0, uint32(0) ++ uint32(7))
          case _ => Frame(GoAway, 0, 0, uint32(0) ++ uint32(code))
        }
        _ <- writeFrame(peer, reply)
        _ <- responded.complete(()).void
        _ <-
          if (mode == "stream-only" && role == "server") IO.never
          else if (mode == "stream-only") IO.unit
          else peer.endOfOutput
      } yield ()
    }
    for {
      responded <- Deferred[IO, Unit]
      target <-
        if (role == "server")
          ScriptedTarget
            .create[IO] { (peer, _) =>
              peer.readExact(clientPreface.size.toInt) *> readFrame(peer) *>
                peer.write(encode(settings(Vector(6 -> limit))) ++ encode(settings(ack = true))) *>
                stimulus(peer, responded)
            }
            .map(_._1: WireTarget[IO])
        else
          IO.pure(clientTarget { peer =>
            peer.write(clientPreface ++ encode(settings(Vector(6 -> limit)))) *>
              readUntil(peer, fromServer = true)(f => f.frameType == Settings && !f.hasFlag(Ack)) *>
              writeFrame(peer, settings(ack = true)) *>
              writeFrame(
                peer,
                headers(
                  1,
                  Vector(
                    ":method" -> "GET",
                    ":scheme" -> "http",
                    ":authority" -> "wire.test",
                    ":path" -> "/",
                  ),
                ),
              ) *>
              stimulus(peer, responded) *> IO
                .raiseError[ResponseData[IO]](new RuntimeException("native framing rejection"))
          })
      trace <- Transcript.create[IO]()
      run = selected(s"h2.$role.reject-$suffix").run(CaseContext(target, trace))
      result <- (if (mode != "stream-only") run
                 else
                   run.background.use { joined =>
                     val done = joined.flatMap(_.embedNever)
                     IO.race(responded.get, done).flatMap {
                       case Left(_) => done.timeout(200.millis)
                       case Right(value) => IO.pure(value)
                     }
                   }).attempt
      sent <- sentFrames(trace)
    } yield (result, sent)
  }

  for {
    role <- Vector("client", "server")
    entry <- finiteFramingCases
    exact = if (role == "client") 42L else 181L
    limit <- Vector(0L, exact - 1L, exact, 4096L, 0xffffffffL)
  } test(s"finite framing budget: $role/${entry._1}/$limit".tag(finiteFramingTag)) {
    finiteFramingRun(role, entry._1, entry._2._1, entry._2._2, limit).map { case (result, sent) =>
      if (limit >= exact) assertEquals(result, Right(()))
      else {
        assert(result.swap.exists(_.isInstanceOf[WireObservationLimit]), result.toString)
        assert(!sent.exists(_.frameType == Http2Wire.Headers))
      }
    }
  }

  for {
    role <- Vector("client", "server")
    entry <- finiteFramingCases
    mode <- Vector("stream-only", "malformed-peer", "wrong-code")
  } test(s"finite framing verdict: $role/${entry._1}/$mode".tag(finiteFramingTag)) {
    finiteFramingRun(role, entry._1, entry._2._1, entry._2._2, 4096L, mode).map {
      case (result, sent) =>
        if (mode == "stream-only")
          assert(
            result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]),
            result.toString,
          )
        else assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        assert(sent.exists(_.frameType == Http2Wire.Headers))
    }
  }

  for (entry <- finiteFramingCases)
    test(s"finite framing cleanup: ${entry._1}".tag(finiteFramingTag)) {
      messageCleanupLifetimeProbe(
        s"h2.client.reject-${entry._1}",
        entry._2._1,
        close = true,
        enabled = false,
        apiSuccess = false,
        headerLimit = Some(4096L),
      )
    }

  for (mode <- Vector("empty-trailer", "zero-admission", "compression-negative", "positive-warmup"))
    test(s"finite framing helper guards: $mode".tag(finiteFramingTag)) {
      import Http2Wire._
      for {
        writes <- Ref.of[IO, Int](0)
        trace <- Transcript.create[IO]()
        peer <- RawPeer.create[IO](
          (_: Int) => IO.pure(Option.empty[Chunk[Byte]]),
          (_: Chunk[Byte]) => writes.update(_ + 1),
          IO.unit,
          trace,
        )
        result <- Http2PeerSettings
          .scoped[IO, Unit](initiatesRequests = mode == "zero-admission") { implicit codecSettings =>
            codecSettings.received(peer, settings(Vector(6 -> 0L, 3 -> 0L))) *> (mode match {
              case "empty-trailer" =>
                Http2PeerSettings.writeFramingFault(
                  peer,
                  Vector(Frame(Headers, EndStream | EndHeaders | Padded, 1, ByteVector.empty)),
                  Vector.empty,
                )
              case "zero-admission" =>
                Http2PeerSettings.writeFramingFault(
                  peer,
                  Vector(
                    Frame(Headers, EndStream, 1, ByteVector.empty),
                    Frame(Continuation, EndHeaders, 0, ByteVector.empty),
                  ),
                  Vector.empty,
                )
              case _ =>
                (if (mode == "positive-warmup")
                   Http2PeerSettings.writeFrames(
                     peer,
                     Vector(headers(1, Vector(":status" -> "200"))),
                   )
                 else IO.unit) *>
                  Http2PeerSettings.writeFrames(
                    peer,
                    Vector(Frame(Headers, EndStream | EndHeaders, 1, ByteVector(0x80.toByte))),
                    requireFieldAcceptance = false,
                  )
            })
          }
          .attempt
        emitted <- writes.get
        _ <- IO {
          if (mode == "zero-admission" || mode == "positive-warmup") {
            assert(result.swap.exists(_.isInstanceOf[WireObservationLimit]), result.toString)
            assertEquals(emitted, 0)
          } else {
            assertEquals(result, Right(()))
            assertEquals(emitted, 1)
          }
        }
      } yield ()
    }

  // The complete independently enumerated positive odd-stream admission surface.
  for (
    id <- Vector(
      "h2.server.reject-rst-length-three",
      "h2.server.reject-rst-length-five",
      "h2.server.priority-does-not-open-stream",
      "h2.server.priority-on-closed-stream",
      "h2.server.default-frame-size-splits-data",
      "h2.server.settings-replace-frame-limit",
      "h2.server.rst-cancel-no-response",
      "h2.server.rst-unknown-error-no-response",
      "h2.server.message-empty",
      "h2.server.message-binary-data",
      "h2.server.headers-fragmented-end-stream",
      "h2.server.headers-empty-final-continuation",
      "h2.server.headers-zero-padding",
      "h2.server.headers-padding",
      "h2.server.headers-priority-fields",
      "h2.server.headers-padding-and-priority",
      "h2.server.reject-uppercase-field-name",
      "h2.server.reject-field-value-nul",
      "h2.server.reject-field-value-cr",
      "h2.server.reject-field-value-lf",
      "h2.server.reject-connection-field",
      "h2.server.reject-proxy-connection-field",
      "h2.server.reject-keep-alive-field",
      "h2.server.reject-transfer-encoding-field",
      "h2.server.reject-upgrade-field",
      "h2.server.reject-field-name-nul",
      "h2.server.reject-field-name-tab",
      "h2.server.reject-field-name-space",
      "h2.server.reject-field-name-control-boundary",
      "h2.server.reject-field-name-uppercase-boundary",
      "h2.server.reject-field-name-del",
      "h2.server.reject-field-name-obs-text-low",
      "h2.server.reject-field-name-obs-text-high",
      "h2.server.reject-field-name-colon",
      "h2.server.reject-field-value-leading-space",
      "h2.server.reject-field-value-trailing-space",
      "h2.server.reject-field-value-leading-tab",
      "h2.server.reject-field-value-trailing-tab",
      "h2.server.validate-field-empty-name",
      "h2.server.validate-field-separator-name",
      "h2.server.field-value-interior-tab-and-obs-text",
      "h2.server.field-value-empty",
      "h2.server.content-length-matching",
      "h2.server.content-length-padded-data",
      "h2.server.reject-content-length-underflow",
      "h2.server.reject-content-length-overflow",
      "h2.server.message-trailers",
      "h2.server.message-empty-trailers",
      "h2.server.message-fragmented-trailers",
      "h2.server.reject-trailers-without-end-stream",
      "h2.server.reject-trailer-pseudo-header",
      "h2.server.reject-trailer-uppercase-name",
      "h2.server.reject-trailer-prohibited-field",
      "h2.server.reject-undefined-pseudo-header",
      "h2.server.reject-pseudo-header-after-regular-field",
      "h2.server.reject-headers-padding-equals-payload",
      "h2.server.reject-headers-padding-exceeds-payload",
      "h2.server.reject-headers-missing-pad-length",
      "h2.server.reject-headers-short-priority",
      "h2.server.reject-continuation-stream-zero",
      "h2.server.reject-continuation-different-stream",
      "h2.server.reject-headers-interrupted-by-ping",
      "h2.server.reject-continuation-interrupted-by-settings",
      "h2.server.reject-missing-method",
      "h2.server.reject-missing-scheme",
      "h2.server.reject-missing-path",
      "h2.server.reject-repeated-method",
      "h2.server.reject-repeated-scheme",
      "h2.server.reject-repeated-authority",
      "h2.server.reject-repeated-path",
      "h2.server.reject-response-pseudo-header",
      "h2.server.reject-empty-method",
      "h2.server.reject-invalid-method-token",
      "h2.server.reject-empty-scheme",
      "h2.server.reject-invalid-scheme",
      "h2.server.reject-empty-path",
      "h2.server.reject-relative-path",
      "h2.server.reject-path-fragment",
      "h2.server.reject-path-space",
      "h2.server.reject-authority-userinfo",
      "h2.server.request-path-and-query",
      "h2.server.request-options-asterisk",
      "h2.server.request-host-without-authority",
      "h2.server.request-te-trailers",
      "h2.server.reject-te-gzip",
      "h2.server.reject-te-mixed",
      "h2.server.request-split-cookie",
      "h2.server.reject-connect-scheme",
      "h2.server.reject-connect-path",
      "h2.server.reject-connect-missing-authority",
      "h2.server.reject-connect-missing-port",
      "h2.server.reject-client-push-promise",
      "h2.server.response-lowercase-field-names",
      "h2.server.skipped-stream-cannot-open",
      "h2.server.half-closed-remote-data",
      "h2.server.half-closed-remote-headers",
      "h2.server.window-update-after-peer-end",
      "h2.server.stream-credit-boundary",
      "h2.server.connection-credit-independent",
      "h2.server.negative-stream-window",
      "h2.server.zero-stream-window-update",
      "h2.server.stream-window-overflow",
      "h2.server.settings-stream-window-overflow",
      "h2.server.maximum-stream-window-update",
      "h2.server.reset-closes-blocked-stream",
      "h2.server.window-update-on-closed-stream",
      "h2.server.goaway-includes-dispatched-stream",
      "h2.server.peer-concurrency-zero-allows-responses",
      "h2.server.hpack-literal-without-new-name",
      "h2.server.hpack-literal-without-static-name",
      "h2.server.hpack-literal-never-new-name",
      "h2.server.hpack-literal-never-static-name",
      "h2.server.hpack-literal-incremental-new-name",
      "h2.server.hpack-literal-incremental-static-name",
      "h2.server.hpack-static-last-index",
      "h2.server.hpack-static-name-prefix-boundary",
      "h2.server.hpack-huffman-value",
      "h2.server.hpack-huffman-name",
      "h2.server.hpack-huffman-name-value",
      "h2.server.hpack-huffman-empty",
      "h2.server.hpack-huffman-no-padding",
      "h2.server.hpack-huffman-seven-bit-padding",
      "h2.server.hpack-string-empty",
      "h2.server.hpack-string-raw-octets",
      "h2.server.hpack-string-length-126",
      "h2.server.hpack-string-length-127",
      "h2.server.hpack-string-length-128",
      "h2.server.hpack-string-length-255",
      "h2.server.hpack-string-name-length-127",
      "h2.server.hpack-indexed-same-block",
      "h2.server.hpack-dynamic-stream-reuse",
      "h2.server.hpack-dynamic-order",
      "h2.server.hpack-dynamic-duplicates",
      "h2.server.hpack-without-preserves-table",
      "h2.server.hpack-never-preserves-table",
      "h2.server.hpack-dynamic-name-evicted-source",
      "h2.server.hpack-dynamic-name-without",
      "h2.server.hpack-dynamic-name-never",
      "h2.server.hpack-name-index-six-bit-boundary",
      "h2.server.hpack-indexed-seven-bit-boundary",
      "h2.server.hpack-table-oldest-eviction",
      "h2.server.hpack-table-shrink-retains-newest",
      "h2.server.hpack-table-zero-restore-insert",
      "h2.server.hpack-table-oversized-entry-accepted",
      "h2.server.hpack-table-huffman-exact-size",
      "h2.server.hpack-reject-index-zero",
      "h2.server.hpack-reject-index-empty-dynamic",
      "h2.server.hpack-reject-index-integer-truncated",
      "h2.server.hpack-reject-incremental-name-integer-truncated",
      "h2.server.hpack-reject-without-name-integer-truncated",
      "h2.server.hpack-reject-never-name-integer-truncated",
      "h2.server.hpack-reject-index-integer-huge",
      "h2.server.hpack-reject-name-truncated",
      "h2.server.hpack-reject-name-length-truncated",
      "h2.server.hpack-reject-value-truncated",
      "h2.server.hpack-reject-value-missing",
      "h2.server.hpack-reject-string-length-truncated",
      "h2.server.hpack-reject-huffman-string-truncated",
      "h2.server.hpack-reject-incremental-name-index",
      "h2.server.hpack-reject-without-name-index",
      "h2.server.hpack-reject-never-name-index",
      "h2.server.hpack-reject-huffman-value-eos",
      "h2.server.hpack-reject-huffman-name-eos",
      "h2.server.hpack-reject-huffman-padding-eight",
      "h2.server.hpack-reject-huffman-name-padding-eight",
      "h2.server.hpack-reject-huffman-value-padding",
      "h2.server.hpack-reject-huffman-name-padding",
      "h2.server.hpack-reject-update-after-index",
      "h2.server.hpack-reject-update-after-literal",
      "h2.server.hpack-reject-update-integer-truncated",
      "h2.server.hpack-reject-update-above-advertised",
      "h2.server.hpack-reject-index-beyond-populated",
      "h2.server.hpack-reject-without-not-stored",
      "h2.server.hpack-reject-never-not-stored",
      "h2.server.hpack-reject-evicted-oldest-index",
      "h2.server.hpack-reject-shrink-evicted-index",
      "h2.server.hpack-reject-zero-restored-index",
      "h2.server.hpack-reject-oversize-clears-table",
      "h2.server.hpack-reject-huffman-compressed-size",
      "h2.server.hpack-reject-initial-dynamic-index",
      "h2.server.hpack-encoder-stateful-roundtrip",
      "h2.server.hpack-encoder-reduction-zero",
      "h2.server.hpack-encoder-minimum-before-restore",
    )
  ) test(s"$id stops before an inadmissible initial request") {
    import Http2Wire._
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, _) =>
        def controls: IO[Unit] = readFrame(peer).flatMap {
          case frame if frame.frameType == Ping && !frame.hasFlag(Ack) =>
            writeFrame(peer, frame.copy(flags = Ack)) *> controls
          case frame if frame.frameType == Settings && !frame.hasFlag(Ack) =>
            writeFrame(peer, settings(ack = true)) *> controls
          case frame if frame.frameType == Headers =>
            IO.raiseError(new AssertionError("fixture opened an inadmissible request"))
          case _ => controls
        }
        peer.readExact(clientPreface.size.toInt) *> readFrame(peer) *>
          peer.write(encode(settings(Vector(3 -> 0L))) ++ encode(settings(ack = true))) *> controls
      }
      trace <- Transcript.create[IO]()
      error <- selected(id).run(CaseContext(fixture._1, trace)).intercept[WireObservationLimit]
      sent <- sentFrames(trace)
      _ <- IO {
        assert(error.getMessage.contains("MAX_CONCURRENT_STREAMS"))
        assert(
          !sent.exists(_.frameType == Headers),
          "HEADERS were sent before the prerequisite check",
        )
      }
    } yield ()
  }

  for {
    limit <- Vector(0L, 180L, 181L, 182L)
  } test(s"actual message request checks exact advisory field-section size against $limit") {
    peerSettingsServer("h2.server.message-empty", Vector(6 -> limit)).flatMap {
      case (result, sent) =>
        if (limit >= 181L) IO(assertEquals(result, Right(())))
        else
          IO {
            assert(result.left.exists(_.isInstanceOf[WireObservationLimit]))
            assert(!sent.exists(_.frameType == Http2Wire.Headers))
          }
    }
  }

  for {
    tableMode <- Vector("unchanged", "duplicate", "separate")
    concurrency <- Vector(0L, 1L)
  } test(s"actual HPACK reuse retains admission and interval minimum: $tableMode/$concurrency") {
    import Http2Wire._
    val updates = tableMode match {
      case "duplicate" => Vector(settings(Vector(1 -> 0L, 1 -> 4096L)))
      case "separate" => Vector(settings(Vector(1 -> 0L)), settings(Vector(1 -> 4096L)))
      case _ => Vector.empty
    }
    peerSettingsServer(
      "h2.server.hpack-dynamic-stream-reuse",
      Vector(3 -> 1L),
      updates :+ settings(Vector(3 -> concurrency)),
    ).flatMap { case (result, sent) =>
      IO {
        val heads = sent.filter(_.frameType == Headers)
        if (tableMode == "unchanged" && concurrency == 1L) {
          assertEquals(result, Right(()))
          assertEquals(heads.size, 2) // The first response closes stream 1; one slot suffices.
        } else {
          assert(result.left.exists(_.isInstanceOf[WireObservationLimit]))
          assertEquals(heads.size, 1, "incompatible second HPACK block was sent")
        }
      }
    }
  }

  for (
    id <- Vector(
      "h2.client.headers-and-data",
      "h2.client.message-empty",
      "h2.client.unknown-connection-frame",
      "h2.client.hpack-literal-without-new-name",
      "h2.client.interaction-eager-server-settings",
    )
  ) test(s"$id checks advisory response size before response HEADERS") {
    import Http2Wire._
    runClient(id) { peer =>
      for {
        _ <- peer.write(clientPreface ++ encode(settings(Vector(6 -> 0L))))
        _ <- readUntil(peer, fromServer = true)(f => f.frameType == Settings && !f.hasFlag(Ack))
        _ <- writeFrame(peer, settings(ack = true))
        _ <- writeFrame(
          peer,
          headers(
            1,
            Vector(
              ":method" -> "GET",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> (if (id.contains("eager")) "/eager" else "/"),
            ),
          ),
        )
        result <- {
          def controls: IO[ResponseData[IO]] = readFrame(peer).flatMap {
            case frame if frame.frameType == Ping && !frame.hasFlag(Ack) =>
              writeFrame(peer, frame.copy(flags = Ack)) *> controls
            case _ => controls
          }
          controls
        }
      } yield result
    }.intercept[WireObservationLimit]
      .map(error => assert(error.getMessage.contains("MAX_HEADER_LIST_SIZE")))
  }

  private def sentFrames(trace: Transcript[IO]): IO[Vector[Http2Wire.Frame]] =
    trace.render.flatMap { rendered =>
      val bytes =
        rendered.linesIterator.filter(_.startsWith("sending ")).foldLeft(ByteVector.empty) {
          (all, line) => all ++ ByteVector.fromValidHex(line.split(" ")(1))
        }
      IO.fromEither(
        Http2Wire.decodeAll(
          if (bytes.startsWith(Http2Wire.clientPreface)) bytes.drop(Http2Wire.clientPreface.size)
          else bytes
        )
      )
    }

  // These targets deliberately consume malformed controls without invoking the strict observer.
  private def probeFrame(peer: RawPeer[IO], kind: Int): IO[Http2Wire.Frame] = {
    def loop(left: Int): IO[Http2Wire.Frame] =
      if (left == 0) IO.raiseError(new IllegalStateException("probe frame limit"))
      else
        Http2Wire
          .readFrame(peer)
          .flatMap(f => if (f.frameType == kind) IO.pure(f) else loop(left - 1))
    loop(1024)
  }

  for {
    id <- Vector(
      "h2.server.goaway-debug-data",
      "h2.server.goaway-reserved-last-stream",
      "h2.server.goaway-unknown-error",
      "h2.client.goaway-debug-data",
      "h2.client.goaway-unknown-error",
    )
    code <- Vector(0L, 1L, 2L, 7L)
  } test(s"followup GOAWAY closure generic alternatives: $id/$code") {
    import Http2Wire._
    def close(peer: RawPeer[IO]): IO[Unit] =
      probeFrame(peer, Ping) *>
        writeFrame(peer, Frame(GoAway, 0, 0, uint32(0) ++ uint32(code))) *> peer.endOfOutput
    val run =
      if (id.startsWith("h2.server.")) runServer(id)(peer => serverHandshake(peer) *> close(peer))
      else
        runClient(id)(peer =>
          startRequest(peer) *> close(peer) *> IO
            .raiseError[ResponseData[IO]](new RuntimeException("native shutdown"))
        )
    run.attempt.map { result =>
      if (code == 7L) assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
      else assertEquals(result, Right(()))
    }
  }

  for {
    id <- Vector("h2.client.goaway-debug-data", "h2.client.goaway-unknown-error")
    valid <- Vector(true, false)
  } test(s"followup GOAWAY preserves delivered response witness: $id/$valid") {
    import Http2Wire._
    runClient(id) { peer =>
      for {
        _ <- startRequest(peer)
        data <- probeFrame(peer, Data)
        _ <- probeFrame(peer, Ping)
        _ <- writeFrame(peer, Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))) *> peer.endOfOutput
      } yield response(if (valid) data.payload else ByteVector.empty)
    }.attempt.map { result =>
      if (valid) assertEquals(result, Right(()))
      else assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
    }
  }

  for {
    id <- Vector(
      "h2.server.reject-ping-stream-id",
      "h2.client.reject-ping-stream-id",
      "h2.server.reject-ping-length-nine",
      "h2.client.reject-ping-length-nine",
      "h2.server.reject-ping-ack-length",
      "h2.client.reject-ping-ack-length",
      "h2.client.reject-ping-length-seven",
    )
    acknowledge <- Vector(false, true)
    code <- Vector(None, Some(0L), Some(1L), Some(2L)) ++
      (if (id.endsWith("stream-id")) Vector.empty else Vector(Some(6L)))
  } test(s"malformed PING contradictory ACK guard: $id/ack=$acknowledge/close=$code") {
    import Http2Wire._
    def close(peer: RawPeer[IO]): IO[Unit] = for {
      malformed <- probeFrame(peer, Ping)
      _ <- IO {
        assertEquals(malformed.streamId, if (id.endsWith("stream-id")) 1 else 0)
        assertEquals(malformed.hasFlag(Ack), id.endsWith("ack-length"))
        assertEquals(
          malformed.payload.size,
          if (id.endsWith("stream-id")) 8L else if (id.endsWith("nine")) 9L else 7L,
        )
      }
      // No legitimate PING precedes this stimulus. Even a normalized eight-byte ACK
      // contradicts rejection, independently of how the target then closes.
      _ <-
        if (acknowledge)
          writeFrame(peer, Frame(Ping, Ack, 0, (malformed.payload ++ ByteVector(0)).take(8)))
        else IO.unit
      _ <- code.traverse_(value =>
        writeFrame(peer, Frame(GoAway, 0, 0, uint32(0) ++ uint32(value)))
      )
      _ <- peer.endOfOutput
    } yield ()
    val run =
      if (id.startsWith("h2.server.")) runServer(id)(peer => serverHandshake(peer) *> close(peer))
      else
        runClient(id)(peer =>
          startRequest(peer) *> close(peer) *>
            IO.raiseError[ResponseData[IO]](new RuntimeException("native shutdown"))
        )
    run.attempt.map { result =>
      if (acknowledge)
        assert(
          result.swap.exists(error =>
            error.isInstanceOf[ProtocolFailure] &&
              error.getMessage.contains("malformed PING must not be acknowledged")
          ),
          result.toString,
        )
      else assertEquals(result, Right(()))
    }
  }

  for (role <- Vector("server", "client"))
    test(s"malformed PING guard leaves unrelated ACK handling unchanged: $role") {
      import Http2Wire._
      val id = s"h2.$role.reject-settings-stream-id"
      def close(peer: RawPeer[IO]): IO[Unit] = {
        def stimulus: IO[Unit] = readFrame(peer).flatMap { frame =>
          if (frame.frameType == Settings && frame.streamId == 1) IO.unit else stimulus
        }
        stimulus *>
          writeFrame(peer, Frame(Ping, Ack, 0, ByteVector.fill(8)(0))) *> peer.endOfOutput
      }
      if (role == "server") runServer(id)(peer => serverHandshake(peer) *> close(peer))
      else
        runClient(id)(peer =>
          startRequest(peer) *> close(peer) *>
            IO.raiseError[ResponseData[IO]](new RuntimeException("native shutdown"))
        )
    }

  private val seed = ByteVector.fromValidHex("4001780161")
  private def settingsValues(reduced: Boolean): Vector[(Int, Long)] =
    if (reduced) Vector(1 -> 0L, 1 -> 4096L) else Vector.empty
  private def checkInitialState(
      reduced: Boolean,
      block: String,
      result: Either[Throwable, Unit],
  ): IO[Unit] = IO {
    if (reduced) {
      assert(result.swap.exists(_.isInstanceOf[WireObservationLimit]), result.toString)
      assertEquals(block, "", "initial-state witness must stop before a clearing prefix")
    } else {
      assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
      assert(block.endsWith("be"), block)
    }
  }
  for (reduced <- Vector(false, true)) {
    test(s"followup initial dynamic index preserves faulty server witness: $reduced") {
      initialStateServer(reduced)
    }
    test(s"followup initial dynamic index preserves faulty client witness: $reduced") {
      initialStateClient(reduced)
    }
  }
  private def initialStateServer(reduced: Boolean): IO[Unit] = {
    import Http2Wire._
    import scala.concurrent.duration._
    for {
      first <- Ref.of[IO, String]("")
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        for {
          d <- HpackWire.decoder[IO]()
          _ <- d.decode(seed)
          _ <- peer.readExact(clientPreface.size.toInt)
          _ <- readFrame(peer)
          _ <- peer.write(encode(settings(settingsValues(reduced))) ++ encode(settings(ack = true)))
          h <- readUntil(peer, fromServer = false)(_.frameType == Headers)
          _ <- first.set(h.payload.toHex)
          decoded <- d.decode(h.payload).attempt
          _ <- decoded match {
            case Left(_) =>
              writeFrame(peer, Frame(GoAway, 0, 0, uint32(1) ++ uint32(9))) *> peer.endOfOutput
            case Right(fields) =>
              publish(
                RequestData(
                  method = "GET",
                  path = "/",
                  headers = fields.filterNot(_._1.startsWith(":")),
                )
              ) *> writeFrame(peer, headers(1, Vector(":status" -> "200"))) *>
                // Retain the mutant table witness, but challenge its incorrect wire
                // error independently of progressive publication and cleanup EOF.
                writeFrame(peer, Frame(GoAway, 0, 0, uint32(1) ++ uint32(8)))
          }
        } yield ()
      }
      trace <- Transcript.create[IO]()
      r <- Catalog.all
        .find(_.id == "h2.server.hpack-reject-initial-dynamic-index")
        .get
        .run(CaseContext(fixture._1, trace))
        .timeout(3.seconds)
        .attempt
      b <- first.get
      _ <- checkInitialState(reduced, b, r)
      _ <-
        if (reduced) IO.unit
        else
          IO(
            assert(
              r.swap.exists(_.getMessage == "compression failure used error code 8"),
              r.toString,
            )
          )
    } yield ()
  }
  private def initialStateClient(reduced: Boolean): IO[Unit] = {
    import Http2Wire._
    import scala.concurrent.duration._
    for {
      first <- Ref.of[IO, String]("")
      target = ScriptedClient.network[IO] { (a, peer) =>
        Resource.pure[IO, ClientDriver[IO]](new ClientDriver[IO] {
          def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval(for {
            d <- HpackWire.decoder[IO]()
            _ <- d.decode(seed)
            _ <- peer.write(clientPreface ++ encode(Http2Wire.settings(settingsValues(reduced))))
            _ <- readUntil(peer, fromServer = true)(f => f.frameType == Settings && !f.hasFlag(Ack))
            _ <- writeFrame(peer, Http2Wire.settings(ack = true))
            _ <- writeFrame(
              peer,
              headers(
                1,
                Vector(
                  ":method" -> r.method,
                  ":scheme" -> "http",
                  ":authority" -> a.toString,
                  ":path" -> r.path,
                ),
              ),
            )
            h <- readUntil(peer, fromServer = true)(_.frameType == Headers)
            _ <- first.set(h.payload.toHex)
            decoded <- d.decode(h.payload).attempt
            response <- decoded match {
              case Left(_) =>
                writeFrame(
                  peer,
                  Frame(GoAway, 0, 0, uint32(0) ++ uint32(9)),
                ) *> peer.endOfOutput *> IO.raiseError[ResponseData[IO]](
                  new RuntimeException("native compression rejection")
                )
              case Right(fields) =>
                IO.pure(
                  ResponseData[IO](
                    200,
                    fields.filterNot(_._1.startsWith(":")),
                    Stream.empty,
                    IO.pure(Vector.empty),
                  )
                )
            }
          } yield response)
          def exchangeStream(r: RequestData, b: FixtureCleanup[IO] => Stream[IO, Byte]) = exchange(
            r
          )
        })
      }
      trace <- Transcript.create[IO]()
      r <- Catalog.all
        .find(_.id == "h2.client.hpack-reject-initial-dynamic-index")
        .get
        .run(CaseContext(target, trace))
        .timeout(3.seconds)
        .attempt
      b <- first.get
      _ <- checkInitialState(reduced, b, r)
    } yield ()
  }

  private val connectionControlIds = Vector(
    "h2.client.reject-settings-stream-id",
    "h2.client.reject-settings-ack-payload",
    "h2.client.reject-settings-length",
    "h2.client.reject-settings-enable-push-two",
    "h2.client.reject-settings-window-overflow",
    "h2.client.reject-settings-frame-size-below-minimum",
    "h2.client.reject-settings-frame-size-above-maximum",
    "h2.client.reject-ping-stream-id",
    "h2.client.reject-ping-length-nine",
    "h2.client.reject-ping-ack-length",
    "h2.client.reject-priority-stream-zero",
    "h2.client.reject-rst-stream-zero",
    "h2.client.reject-rst-idle-stream",
    "h2.client.reject-rst-length-three",
    "h2.client.reject-rst-length-five",
    "h2.client.reject-goaway-stream-id",
    "h2.client.reject-goaway-length-seven",
    "h2.client.reject-settings-server-enable-push-one",
    "h2.client.reject-ping-length-seven",
    "h2.client.reject-settings-duplicate-invalid-first",
    "h2.client.reject-settings-duplicate-invalid-last",
  )

  for {
    id <- connectionControlIds
    mode <- Vector(
      "api-error",
      "api-success",
      "wrong-code",
      "cleanup-only",
      "api-timeout",
      "api-adapter",
      "body-timeout",
    )
  } test(s"followup connection control holds exchange until wire verdict: $id/$mode") {
    import Http2Wire._
    import scala.concurrent.duration._
    for {
      bodyReady <- Deferred[IO, Unit]
      released <- Deferred[IO, Unit]
      responded <- Ref.of[IO, Boolean](false)
      nativeTimeout = new java.util.concurrent.TimeoutException("original target timeout")
      nativeAdapter = new AdapterFailure(
        "fixture",
        new RuntimeException("original fixture failure"),
      )
      target = new WireTarget[IO] {
        def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

        val name = "independent-control-client"
        val version = "oracle-challenge"
        def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
          Resource.eval(IO.raiseError(new UnsupportedOperationException("client only")))
        def client(p: Protocol, address: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
          for {
            socket <- Network[IO].connect(address)
            trace <- Resource.eval(Transcript.create[IO]())
            peer <- Resource.eval(RawPeer.create(socket, trace))
          } yield new ClientDriver[IO] {
            def exchange(request: RequestData): Resource[IO, ResponseData[IO]] =
              Resource
                .makeCase(IO.unit) { (_, exit) =>
                  released.complete(()).void *> (exit match {
                    // Normal exchange release models the false EOF witness. Cancellation is owned
                    // by the enclosing socket resource, and must not add a second shutdown path.
                    case Resource.ExitCase.Succeeded => peer.endOfOutput
                    case _ => IO.unit
                  })
                }
                .flatMap { _ =>
                  Resource.eval(for {
                    _ <- startRequest(peer)
                    _ <- probeFrame(peer, Headers)
                    data <- probeFrame(peer, Data)
                    _ <- responded.set(true)
                    _ <-
                      if (mode == "cleanup-only") IO.unit
                      else
                        writeFrame(
                          peer,
                          Frame(
                            GoAway,
                            0,
                            0,
                            uint32(0) ++ uint32(if (mode == "wrong-code") 7L else 1L),
                          ),
                        ) *> peer.endOfOutput
                    // Force the wire verdict to cancel this acquisition before any later witness.
                    _ <- if (mode == "wrong-code") IO.never[Unit] else IO.unit
                    result <-
                      if (mode == "api-error")
                        IO.raiseError[ResponseData[IO]](new RuntimeException("native rejection"))
                      else if (mode == "api-timeout") IO.raiseError[ResponseData[IO]](nativeTimeout)
                      else if (mode == "api-adapter") IO.raiseError[ResponseData[IO]](nativeAdapter)
                      else if (mode == "body-timeout")
                        IO.pure(
                          response(data.payload).copy(body = Stream.raiseError[IO](nativeTimeout))
                        )
                      else
                        IO.pure(
                          response(data.payload).copy(
                            body = Stream.chunk(fs2.Chunk.byteVector(data.payload)) ++
                              Stream.exec(bodyReady.complete(()).void)
                          )
                        )
                  } yield result)
                }
            def exchangeStream(
                request: RequestData,
                body: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] = exchange(request)
          }
      }
      trace <- Transcript.create[IO]()
      run = selected(id).run(CaseContext(target, trace))
      result <- (if (mode != "cleanup-only") run
                 else
                   run.background.use { joined =>
                     val completed = joined.flatMap {
                       case cats.effect.Outcome.Succeeded(value) => value
                       case cats.effect.Outcome.Errored(error) => IO.raiseError[Unit](error)
                       case cats.effect.Outcome.Canceled() => IO.canceled *> IO.never[Unit]
                     }
                     // Start the intentional blocked-state deadline only after the body was consumed.
                     // Early completion/errors still surface instead of waiting for an absent witness.
                     IO.race(bodyReady.get, completed).flatMap {
                       case Left(_) => completed.timeout(500.millis)
                       case Right(value) => IO.pure(value)
                     }
                   }).attempt
      _ <- IO {
        if (mode == "cleanup-only")
          assert(
            result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]),
            result.toString,
          )
        else if (mode == "wrong-code")
          assert(
            result.swap.exists {
              case error: ProtocolFailure =>
                error.getMessage.startsWith("unexpected GOAWAY error code:")
              case _ => false
            },
            result.toString,
          )
        else if (mode == "api-timeout" || mode == "body-timeout")
          assertEquals(result, Left(nativeTimeout))
        else if (mode == "api-adapter") assertEquals(result, Left(nativeAdapter))
        else assertEquals(result, Right(()))
      }
      done <- released.tryGet
      sawResponse <- responded.get
      _ <- IO {
        assertEquals(done, Some(())); assert(sawResponse, "probe never consumed the valid response")
      }
    } yield ()
  }

  for {
    id <- Vector(
      "h2.server.reject-trailer-prohibited-field",
      "h2.server.reject-trailer-pseudo-header",
      "h2.server.reject-trailer-uppercase-name",
      "h2.server.reject-trailers-without-end-stream",
    )
    reset <- Vector(true, false)
  } test(s"followup progressive trailer publication still requires rejection: $id/$reset") {
    import Http2Wire._
    import scala.concurrent.duration._
    runPublishedServer(id) { (peer, publish) =>
      for {
        _ <- serverHandshake(peer)
        _ <- probeFrame(peer, Headers)
        data <- probeFrame(peer, Data)
        _ <- publish(RequestData(path = "/mapping", body = data.payload))
        trailer <- probeFrame(peer, Headers)
        _ <-
          if (!trailer.hasFlag(EndStream))
            readUntil(peer, fromServer = false)(f =>
              f.frameType == Data && f.hasFlag(EndStream)
            ).void
          else IO.unit
        _ <- writeFrame(peer, headers(1, Vector(":status" -> "200"), endStream = false))
        _ <- if (reset) writeFrame(peer, Frame(RstStream, 0, 1, uint32(1))) else IO.never[Unit]
      } yield ()
    }.timeout(500.millis).attempt.map { result =>
      if (reset) assertEquals(result, Right(()))
      else
        assert(
          result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]),
          result.toString,
        )
    }
  }

  for {
    suffix <- Vector("underflow", "overflow")
    reset <- Vector(true, false)
  } test(
    s"message progressive content-length $suffix publication requires wire rejection: $reset"
  ) {
    import Http2Wire._
    val id = s"h2.server.reject-content-length-$suffix"
    val run = runPublishedServer(id) { (peer, publish) =>
      for {
        _ <- serverHandshake(peer)
        head <- readUntil(peer, fromServer = false)(_.frameType == Headers)
        decoder <- HpackWire.decoder[IO]()
        fields <- readHeaders(peer, head, decoder)
        data <- readUntil(peer, fromServer = false)(_.frameType == Data)
        _ <- IO {
          assertEquals(head.streamId, 1)
          assert(!head.hasFlag(EndStream))
          assertEquals(
            fields.collect { case ("content-length", value) => value },
            Vector(if (suffix == "underflow") "6" else "4"),
          )
          assertEquals(data.streamId, 1)
          assert(data.hasFlag(EndStream))
          assertEquals(data.payload, ByteVector(0, 1, 127, 128, 255))
        }
        _ <- publish(RequestData(path = "/mapping", headers = fields, body = data.payload))
        // Publication precedes the wire witness: section 8.1.1 permits progressive processing.
        _ <-
          if (reset) writeFrame(peer, Frame(RstStream, 0, 1, uint32(1)))
          else
            writeFrame(peer, headers(1, Vector(":status" -> "200"))) *>
              peer.write(boundedProgress)
        _ <- IO.never[Unit]
      } yield ()
    }
    if (reset) run
    else
      run.intercept[WireObservationLimit].map { error =>
        assert(error.getMessage.contains("without required protocol rejection"))
      }
  }

  for {
    id <- Vector(
      "h2.client.reject-push-stream-zero",
      "h2.client.reject-push-unsafe-method",
      "h2.client.reject-push-request-content",
      "h2.client.reject-push-missing-method",
      "h2.client.reject-push-invalid-field",
      "h2.client.reject-push-unauthoritative-origin",
      "h2.client.reject-push-odd-promised-stream",
    )
    pushEnabled <- Vector(false, true)
    (mode, resetCode) <- Vector(
      "goaway" -> 7L,
      "goaway" -> 8L,
      // These codes test observer scope, not their independent RFC legality.
      // Only the later validated connection error supplies the verdict.
      "goaway" -> 0L,
      "goaway" -> 0x1234L,
      "valid-response" -> 7L,
      "valid-response" -> 8L,
      "goaway" -> 1L,
      "goaway" -> 2L,
      "reset-only" -> 7L,
      "reset-only" -> 8L,
      "reset-only" -> 1L,
      "reset-only" -> 2L,
      "second-reset" -> 7L,
      "second-reset" -> 8L,
      "goaway-without-close" -> 7L,
      "goaway-without-close" -> 8L,
      "malformed-reset" -> 7L,
      "stream-zero-reset" -> 8L,
      "wrong-goaway-code" -> 7L,
      "wrong-goaway-code" -> 8L,
    )
    // Every caller/setting exercises refusal; shared observer transitions use representatives.
    if (id.endsWith("odd-promised-stream") && mode == "goaway" &&
      (resetCode == 1L || resetCode == 2L || resetCode == 7L || resetCode == 8L)) ||
      (!id.endsWith("odd-promised-stream") &&
        ((mode == "goaway" && (resetCode == 7L || resetCode == 8L)) ||
          (mode == "reset-only" && pushEnabled && !id.endsWith("stream-zero") &&
            (resetCode == 1L || resetCode == 2L)) ||
          (id.endsWith("stream-zero") && !pushEnabled) ||
          (id.endsWith("unsafe-method") && pushEnabled)))
  } test(
    s"incidental push reset requires connection termination: $id/$pushEnabled/$mode/$resetCode"
  ) {
    import Http2Wire._
    import scala.concurrent.duration._
    for {
      sent <- Deferred[IO, Unit]
      run = runClient(id) { peer =>
        for {
          _ <- peer.write(
            clientPreface ++ encode(settings(Vector(2 -> (if (pushEnabled) 1L else 0L))))
          )
          _ <- readUntil(peer, fromServer = true)(_.frameType == Settings)
          _ <- writeFrame(peer, settings(ack = true))
          _ <- writeFrame(
            peer,
            headers(
              1,
              Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> "/",
                "content-length" -> "0",
              ),
            ),
          )
          promise <- readUntil(peer, fromServer = true)(_.frameType == PushPromise)
          _ <- Check.equal(promise.streamId, if (id.endsWith("stream-zero")) 0 else 1)
          _ <- Check.equal(
            readUint32(promise.payload.take(4)),
            if (id.endsWith("odd-promised-stream")) 3L else 2L,
          )
          _ <- probeFrame(peer, Headers)
          _ <- writeFrame(
            peer,
            Frame(
              RstStream,
              0,
              if (mode == "stream-zero-reset") 0
              else if (id.endsWith("odd-promised-stream")) 3
              else 2,
              if (mode == "malformed-reset") ByteVector(7) else uint32(resetCode),
            ),
          )
          _ <-
            if (mode == "reset-only") IO.unit
            else if (mode == "second-reset")
              writeFrame(peer, Frame(RstStream, 0, 2, uint32(1L)))
            else
              writeFrame(
                peer,
                Frame(
                  GoAway,
                  0,
                  0,
                  uint32(0) ++ uint32(if (mode == "wrong-goaway-code") resetCode else 1L),
                ),
              )
          _ <-
            if (mode == "reset-only" || mode == "second-reset" || mode == "goaway-without-close")
              IO.unit
            else peer.endOfOutput
          _ <- sent.complete(())
          response <-
            if (mode == "valid-response")
              IO.pure(ResponseData[IO](200, Vector.empty, Stream.empty, IO.pure(Vector.empty)))
            else IO.raiseError[ResponseData[IO]](new RuntimeException("native rejection"))
        } yield response
      }
      initialStreamVerdict = mode == "reset-only" && pushEnabled &&
        !id.endsWith("stream-zero") && (resetCode == 1L || resetCode == 2L)
      result <- (if (
                   mode != "reset-only" && mode != "second-reset" && mode != "goaway-without-close"
                 ) run
                 else
                   run.background.use { joined =>
                     val completed = joined.flatMap {
                       case cats.effect.Outcome.Succeeded(value) => value
                       case cats.effect.Outcome.Errored(error) => IO.raiseError[Unit](error)
                       case cats.effect.Outcome.Canceled() => IO.canceled *> IO.never[Unit]
                     }
                     IO.race(sent.get, completed).flatMap {
                       case Left(_) => completed.timeout(500.millis)
                       case Right(value) => IO.pure(value)
                     }
                   }).attempt
      _ <- IO {
        if (id.endsWith("odd-promised-stream") && (resetCode == 7L || resetCode == 8L))
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        else if (mode == "goaway" || mode == "valid-response" || initialStreamVerdict)
          assertEquals(result, Right(()))
        else if (mode == "reset-only" || mode == "second-reset" || mode == "goaway-without-close")
          assert(
            result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]),
            result.toString,
          )
        else assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
      }
    } yield ()
  }

  for {
    c <- Catalog.all.filter(_.id.contains(".hpack-reject-"))
    limit <- Vector(None, Some(8192L), Some(0xffffffffL))
  } test(s"followup HPACK negative transmits with finite field policy: ${c.id}/$limit") {
    if (c.role == Role.Server) negativeServer(c, limit) else negativeClient(c, limit)
  }
  for (c <- Catalog.all.filter(_.id.contains(".hpack-reject-")))
    test(s"followup HPACK missing compression rejection fails: ${c.id}") {
      if (c.role == Role.Server) negativeServer(c, Some(8192L), reject = false)
      else negativeClient(c, Some(8192L), reject = false)
    }

  private def checkNegative(
      id: String,
      limit: Option[Long],
      seen: Ref[IO, Int],
      run: IO[Unit],
      reject: Boolean,
  ): IO[Unit] =
    run.attempt.flatMap(result =>
      seen.get.map { count =>
        if (reject) assertEquals(result, Right(()), s"$id/$limit")
        else assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        assert(count > 0, "malformed block was suppressed")
      }
    )
  private def negativeServer(c: WireCase, limit: Option[Long], reject: Boolean = true): IO[Unit] = {
    import Http2Wire._
    for {
      seen <- Ref.of[IO, Int](0)
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        for {
          _ <- peer.readExact(clientPreface.size.toInt)
          _ <- readFrame(peer)
          _ <- peer.write(
            encode(settings(limit.toVector.map(6 -> _))) ++ encode(settings(ack = true))
          )
          d <- HpackWire.decoder[IO]()
          _ <- {
            def loop: IO[Unit] =
              readUntil(peer, fromServer = false)(_.frameType == Headers).flatMap { h =>
                seen.update(_ + 1) *> d.decode(h.payload).attempt.flatMap {
                  case Left(_) if !reject =>
                    // An explicit CANCEL code is not applicable to a compression error. Keep the socket open
                    // so local fixture teardown cannot manufacture the required EOF.
                    writeFrame(peer, headers(h.streamId, Vector(":status" -> "200"))) *>
                      writeFrame(peer, Frame(GoAway, 0, 0, uint32(h.streamId.toLong) ++ uint32(8)))
                  case Left(_) =>
                    writeFrame(
                      peer,
                      Frame(GoAway, 0, 0, uint32(h.streamId.toLong) ++ uint32(9)),
                    ) *> peer.endOfOutput
                  case Right(fields) =>
                    publish(
                      RequestData(
                        method = "GET",
                        path = "/",
                        headers = fields.filterNot(_._1.startsWith(":")),
                      )
                    ) *> writeFrame(peer, headers(h.streamId, Vector(":status" -> "200"))) *> loop
                }
              };
            loop
          }
        } yield ()
      }
      trace <- Transcript.create[IO]()
      _ <- checkNegative(c.id, limit, seen, c.run(CaseContext(fixture._1, trace)), reject)
    } yield ()
  }
  private def negativeClient(c: WireCase, limit: Option[Long], reject: Boolean = true): IO[Unit] = {
    import Http2Wire._
    for {
      seen <- Ref.of[IO, Int](0)
      target = ScriptedClient.network[IO] { (a, peer) =>
        for {
          d <- Resource.eval(HpackWire.decoder[IO]())
          count <- Resource.eval(Ref.of[IO, Int](0))
        } yield new ClientDriver[IO] {
          def exchange(r: RequestData): Resource[IO, ResponseData[IO]] =
            Resource.eval(for {
              n <- count.getAndUpdate(_ + 1)
              _ <-
                if (n == 0)
                  peer.write(
                    clientPreface ++ encode(Http2Wire.settings(limit.toVector.map(6 -> _)))
                  ) *> readUntil(peer, fromServer = true)(f =>
                    f.frameType == Settings && !f.hasFlag(Ack)
                  ) *> writeFrame(peer, Http2Wire.settings(ack = true))
                else IO.unit
              _ <- writeFrame(
                peer,
                headers(
                  n * 2 + 1,
                  Vector(
                    ":method" -> r.method,
                    ":scheme" -> "http",
                    ":authority" -> a.toString,
                    ":path" -> r.path,
                  ),
                ),
              )
              h <- readUntil(peer, fromServer = true)(_.frameType == Headers)
              _ <- seen.update(_ + 1)
              decoded <- d.decode(h.payload).attempt
              response <- decoded match {
                case Left(_) if !reject => IO.pure(response(ByteVector.empty))
                case Left(_) =>
                  writeFrame(
                    peer,
                    Frame(GoAway, 0, 0, uint32(0) ++ uint32(9)),
                  ) *> peer.endOfOutput *> IO.raiseError[ResponseData[IO]](
                    new RuntimeException("native compression rejection")
                  )
                case Right(fields) =>
                  IO.pure(
                    ResponseData[IO](
                      200,
                      fields.filterNot(_._1.startsWith(":")),
                      Stream.empty,
                      IO.pure(Vector.empty),
                    )
                  )
              }
            } yield response)
          def exchangeStream(r: RequestData, b: FixtureCleanup[IO] => Stream[IO, Byte]) = exchange(
            r
          )
        }
      }
      trace <- Transcript.create[IO]()
      _ <- checkNegative(c.id, limit, seen, c.run(CaseContext(target, trace)), reject)
    } yield ()
  }

  private def peerSettingsServer(
      id: String,
      initial: Vector[(Int, Long)],
      later: Vector[Http2Wire.Frame] = Vector.empty,
  ): IO[(Either[Throwable, Unit], Vector[Http2Wire.Frame])] = {
    import Http2Wire._
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        for {
          _ <- peer.readExact(clientPreface.size.toInt)
          _ <- readFrame(peer)
          _ <- peer.write(encode(settings(initial)) ++ encode(settings(ack = true)))
          decoder <- HpackWire.decoder[IO]()
          _ <- (0 until (if (id.contains("reuse")) 2 else 1)).toVector.traverse_ { index =>
            for {
              first <- readUntil(peer, fromServer = false)(_.frameType == Headers)
              fields <- readHeaders(peer, first, decoder)
              _ <- publish(
                RequestData(
                  method = fields.collectFirst { case (":method", value) => value }.get,
                  path = fields.collectFirst { case (":path", value) => value }.get,
                  headers = fields.filterNot(_._1.startsWith(":")),
                )
              )
              _ <- (if (index == 0) later else Vector.empty).traverse_(writeFrame(peer, _))
              _ <- writeFrame(peer, headers(first.streamId, Vector(":status" -> "200")))
            } yield ()
          }
        } yield ()
      }
      trace <- Transcript.create[IO]()
      result <- selected(id).run(CaseContext(fixture._1, trace)).attempt
      sent <- sentFrames(trace)
    } yield (result, sent)
  }

  private def rejectionTraffic(
      mode: String,
      stream: Int,
      fromServer: Boolean,
      code: Long,
  ): Vector[Http2Wire.Frame] = {
    import Http2Wire._
    val last = if (fromServer) stream.toLong else 0L
    val error = Frame(GoAway, 0, 0, uint32(last) ++ uint32(code))
    val graceful = Frame(GoAway, 0, 0, uint32(if (fromServer) 0x7fffffffL else 0L) ++ uint32(0L))
    val badSettings = Frame(Settings, Ack, 0, ByteVector(0))
    val reset = Frame(RstStream, 0, stream, uint32(code))
    mode match {
      case "plain" => Vector(error)
      case "bad-initiator-before" =>
        Vector(error.copy(payload = uint32(if (fromServer) 2L else 1L) ++ uint32(code)))
      case "bad-initiator-after" =>
        Vector(error, error.copy(payload = uint32(if (fromServer) 2L else 1L) ++ uint32(code)))
      case "bare-eof" => Vector.empty
      case "graceful-prefix" => Vector(graceful, error)
      case "graceful-only" | "graceful-held" => Vector(graceful)
      case "error-held" => Vector(error)
      case "graceful-reset" => Vector(graceful, reset)
      case "reset" => Vector(reset)
      case "priority-before" => Vector(Frame(Priority, 255, 3, uint32(3L) ++ ByteVector(0)), error)
      case "bad-settings-before" => Vector(badSettings, error)
      case "bad-settings-after" => Vector(error, badSettings)
      case "bad-ping-before" => Vector(Frame(Ping, 0, 1, ByteVector.fill(8)(0)), error)
      case "bad-ping-after" => Vector(error, Frame(Ping, 0, 1, ByteVector.fill(8)(0)))
      case "bad-window-before" => Vector(Frame(WindowUpdate, 0, 0, uint32(0)), error)
      case "bad-window-after" => Vector(error, Frame(WindowUpdate, 0, 0, uint32(0)))
      case "bad-priority-before" => Vector(Frame(Priority, 0, 0, ByteVector.fill(5)(0)), error)
      case "bad-priority-after" => Vector(error, Frame(Priority, 0, 0, ByteVector.fill(5)(0)))
      case "bad-setting-value-before" => Vector(settings(Vector(4 -> 0x80000000L)), error)
      case "enable-push-zero" => Vector(settings(Vector(2 -> 0L)), error)
      case "enable-push-one" => Vector(settings(Vector(2 -> 1L)), error)
      case "bad-goaway-stream-after" => Vector(error, error.copy(streamId = 1))
      case "bad-goaway-length-after" => Vector(error, error.copy(payload = ByteVector.fill(7)(0)))
      case "bad-goaway-length-before" => Vector(error.copy(payload = ByteVector.fill(7)(0)), error)
      case "bad-reset-stream-after" => Vector(error, reset.copy(streamId = 0))
      case "bad-reset-length-after" => Vector(error, reset.copy(payload = ByteVector.fill(3)(0)))
      case "bad-reset-length-before" => Vector(reset.copy(payload = ByteVector.fill(3)(0)), error)
      case "increasing-goaway" =>
        Vector(error, error.copy(payload = uint32(last + 2L) ++ uint32(code)))
      case "wrong-error" => Vector(error.copy(payload = uint32(last) ++ uint32(8L)))
      case "wrong-error-after" => Vector(error, error.copy(payload = uint32(last) ++ uint32(8L)))
    }
  }

  private val hpackRejectionModes = Vector(
    "bad-initiator-before",
    "bad-initiator-after",
    "plain",
    "graceful-prefix",
    "graceful-only",
    "priority-before",
    "bad-settings-before",
    "bad-settings-after",
    "bad-ping-after",
    "bad-window-after",
    "bad-priority-after",
    "bad-goaway-stream-after",
    "bad-goaway-length-after",
    "bad-reset-stream-after",
    "bad-reset-length-after",
  )

  for {
    wireCase <- HpackCases.all.filter(_.id.contains(".hpack-reject-"))
    mode <- hpackRejectionModes
  } test(s"HPACK wire rejection census: ${wireCase.id}/$mode") {
    for {
      observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
      trace <- Transcript.create[IO]()
      target = hpackPresentationTarget(identity, observed, rejectMode = mode)
      result <- wireCase.run(CaseContext(target, trace)).attempt
      _ <- IO {
        if (mode.startsWith("bad-"))
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        else assertEquals(result, Right(()))
      }
    } yield ()
  }

  private val relatedRejectionCases = Vector(
    "h2.server.reject-uppercase-field-name",
    "h2.client.reject-uppercase-field-name",
    "h2.server.reject-ping-length-nine",
    "h2.client.reject-ping-length-nine",
    "h2.server.reject-priority-length-4",
    "h2.client.reject-priority-length-4",
    "h2.server.idle-data",
    "h2.client.idle-data",
    "h2.server.half-closed-remote-data",
    "h2.client.zero-stream-window-update",
    "h2.server.goaway-includes-dispatched-stream",
  )

  private val relatedRejectionModes = hpackRejectionModes ++ Vector(
    "bare-eof",
    "bad-ping-before",
    "bad-window-before",
    "bad-priority-before",
    "bad-setting-value-before",
    "enable-push-zero",
    "enable-push-one",
    "bad-goaway-length-before",
    "bad-reset-length-before",
    "increasing-goaway",
    "wrong-error",
    "wrong-error-after",
  )

  for {
    id <- relatedRejectionCases
    mode <- relatedRejectionModes
  } test(s"catalog wire rejection boundaries: $id/$mode") {
    val valid = validRelatedRejectionModes(mode) ||
      (mode == "enable-push-one" && id.startsWith("h2.client."))
    relatedRejectionProbe(id, mode).attempt.flatMap { result =>
      IO {
        if (valid) assertEquals(result, Right(()))
        else assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
      }
    }
  }

  for {
    id <- relatedRejectionCases.filter(id =>
      id.contains("priority-length") || id.contains("uppercase-field") ||
        id.contains("half-closed") || id.contains("zero-stream")
    )
    mode <- Vector("reset", "graceful-reset")
  } test(s"catalog wire rejection stream alternative: $id/$mode") {
    relatedRejectionProbe(id, mode)
  }

  for {
    role <- Vector("client", "server")
    mode <- relatedRejectionModes.diff(hpackRejectionModes)
  } test(s"HPACK wire rejection role and sequence: $role/$mode") {
    for {
      observed <- Ref.of[IO, Vector[(Int, HpackFields)]](Vector.empty)
      trace <- Transcript.create[IO]()
      target = hpackPresentationTarget(identity, observed, rejectMode = mode)
      result <- selected(s"h2.$role.hpack-reject-index-zero")
        .run(CaseContext(target, trace))
        .attempt
      _ <- IO {
        val valid =
          mode == "bare-eof" || mode == "enable-push-zero" || (mode == "enable-push-one" && role == "client")
        if (valid) assertEquals(result, Right(()))
        else assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
      }
    } yield ()
  }

  for {
    id <- relatedRejectionCases
    mode <- Vector("graceful-held", "error-held")
  } test(s"catalog wire rejection holds connection until native EOF: $id/$mode") {
    import scala.concurrent.duration._
    for {
      sent <- Deferred[IO, Unit]
      completed <- Deferred[IO, Either[Throwable, Unit]]
      run = relatedRejectionProbe(id, mode, sent.complete(()).void).attempt
        .flatMap(completed.complete)
        .void
      _ <- Resource.make(run.start)(_.cancel).use { _ =>
        for {
          _ <- sent.get.timeout(5.seconds)
          early <- completed.get.map(Option(_)).timeoutTo(200.millis, IO.pure(None))
          _ <- IO(
            assertEquals(early, None, "GOAWAY on an open connection cannot establish rejection")
          )
        } yield ()
      }
    } yield ()
  }

  private def relatedRejectionProbe(
      id: String,
      mode: String,
      onSent: IO[Unit] = IO.unit,
  ): IO[Unit] = {
    import Http2Wire._
    val fromServer = id.startsWith("h2.server.")
    def reject(peer: RawPeer[IO], stream: Int): IO[Unit] =
      rejectionTraffic(mode, stream, fromServer, 1L).traverse_(writeFrame(peer, _)) *> onSent *>
        (if (mode.endsWith("held")) IO.unit else peer.endOfOutput)
    if (fromServer) runPublishedServer(id) { (peer, publish) =>
      for {
        _ <- serverHandshake(peer)
        _ <- {
          def loop: IO[Unit] = readFrame(peer).flatMap { frame =>
            if (frame.frameType == Settings) loop
            else if (
              frame.frameType == Headers &&
              (id.endsWith("half-closed-remote-data") || id.endsWith(
                "goaway-includes-dispatched-stream"
              ))
            )
              (if (id.endsWith("goaway-includes-dispatched-stream")) publish(RequestData())
               else IO.unit) *>
                writeFrame(
                  peer,
                  headers(frame.streamId, Vector(":status" -> "200"), endStream = false),
                ) *> loop
            else reject(peer, frame.streamId) *> IO.never[Unit]
          }
          loop
        }
      } yield ()
    }
    else
      runClient(id) { peer =>
        for {
          _ <- clientHandshake(peer)
          _ <- writeFrame(
            peer,
            headers(
              1,
              Vector(
                ":method" -> (if (
                                id.endsWith("idle-data") || id.endsWith("zero-stream-window-update")
                              ) "POST"
                              else "GET"),
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> "/",
                "content-length" -> (if (id.endsWith("zero-stream-window-update")) "16" else "0"),
              ),
              endStream = !id.endsWith("zero-stream-window-update"),
            ),
          )
          malformed <- readUntil(peer, fromServer = true)(_.frameType != Settings)
          _ <- IO {
            if (id.contains("priority-length")) assertEquals(malformed.payload.size, 4L)
            else if (id.contains("ping-length")) assertEquals(malformed.payload.size, 9L)
            else if (id.endsWith("idle-data")) assertEquals(malformed.streamId, 2)
            else if (id.endsWith("zero-stream-window-update"))
              assertEquals(malformed.payload, uint32(0L))
          }
          _ <- reject(peer, 1)
        } yield ResponseData[IO](
          200,
          Vector.empty,
          Stream.raiseError[IO](new RuntimeException("native protocol rejection")),
          IO.pure(Vector.empty),
        )
      }
  }

  // Faithful variants of the two actual smoke cases: first consume the complete
  // malformed stimulus, then emit independently constructed target control traffic.
  private object SmokeRejectionVariants {
    import Http2Wire._
    final case class Variant(
        name: String,
        bytes: ByteVector,
        close: Boolean = true,
        expected: String = "pass",
    )
    def frame(f: Frame) = encode(f)
    def go(code: Long, last: Long = 0) = frame(Frame(GoAway, 0, 0, uint32(last) ++ uint32(code)))
    def rst(code: Long = 1, stream: Int = 1) = frame(Frame(RstStream, 0, stream, uint32(code)))
    val controls = Vector(
      "priority" -> Frame(Priority, 0, 1, uint32(0) ++ ByteVector(15)),
      "priority-zero-stream" -> Frame(Priority, 0, 0, uint32(0) ++ ByteVector(15)),
      "priority-short" -> Frame(Priority, 0, 1, ByteVector(0)),
      "settings" -> settings(),
      "settings-short" -> Frame(Settings, 0, 0, ByteVector(0)),
      "settings-wrong-stream" -> Frame(Settings, 0, 1, ByteVector.empty),
      "settings-ack-payload" -> settings(Vector(1 -> 0L), ack = true),
      "settings-invalid-max-frame" -> settings(Vector(5 -> 1L)),
      "ping" -> Frame(Ping, 0, 0, Http1Wire.ascii("abcdefgh")),
      "ping-short" -> Frame(Ping, 0, 0, Http1Wire.ascii("abcdefg")),
      "ping-wrong-stream" -> Frame(Ping, 0, 1, Http1Wire.ascii("abcdefgh")),
      "window-update" -> windowUpdate(0, 1),
      "window-update-zero" -> Frame(WindowUpdate, 0, 0, uint32(0)),
      "window-update-short" -> Frame(WindowUpdate, 0, 0, ByteVector(1)),
      "extension" -> Frame(255, 255, 17, ByteVector(1, 2, 3)),
    )
    val lawful = Set("priority", "settings", "ping", "window-update", "extension")
    val server = Vector(
      Variant("bare-eof", ByteVector.empty),
      Variant("specific", go(6)),
      Variant("generic1", go(1)),
      Variant("generic2", go(2)),
      Variant("goaway-held", go(6), false, "timeout"),
      Variant("lone-noerror", go(0), true, "pass"),
      Variant("graceful-then-error", go(0, 0x7fffffffL) ++ go(6)),
      Variant("wrong-error-code", go(9), true, "fail"),
      Variant("truncated", go(6).dropRight(1), true, "fail"),
      Variant(
        "wrong-goaway-stream",
        frame(Frame(GoAway, 0, 1, uint32(0) ++ uint32(6))),
        true,
        "fail",
      ),
    ) ++ controls.map { case (name, f) =>
      Variant(name, frame(f) ++ go(6), true, if (lawful(name)) "pass" else "fail")
    }
    val client = Vector(
      Variant("bare-eof", ByteVector.empty),
      Variant("rst1", rst(), false),
      Variant("rst2", rst(2), false),
      Variant("rst-wrong-stream", rst(1, 3), false, "fail"),
      Variant("rst-wrong-code", rst(6), false, "fail"),
      Variant("rst-short", frame(Frame(RstStream, 0, 1, ByteVector(1))), false, "fail"),
      Variant("goaway1", go(1)),
      Variant("goaway2", go(2)),
      Variant("goaway-held", go(1), false, "timeout"),
      Variant("lone-noerror", go(0), true, "pass"),
      Variant("graceful-then-error", go(0) ++ go(1)),
      Variant("graceful-then-rst", go(0) ++ rst(), false),
      Variant("wrong-goaway-code", go(6), true, "fail"),
      Variant(
        "wrong-goaway-stream",
        frame(Frame(GoAway, 0, 1, uint32(0) ++ uint32(1))),
        true,
        "fail",
      ),
      Variant("truncated", rst().dropRight(1), true, "fail"),
      Variant(
        "bad-settings-after-error-goaway",
        go(1) ++ frame(Frame(Settings, 0, 1, ByteVector.empty)),
        true,
        "fail",
      ),
      Variant(
        "bad-ping-after-error-goaway",
        go(1) ++ frame(Frame(Ping, 0, 0, ByteVector(1))),
        true,
        "fail",
      ),
      Variant(
        "wrong-goaway-after-error-goaway",
        go(1) ++ frame(Frame(GoAway, 0, 1, uint32(0) ++ uint32(1))),
        true,
        "fail",
      ),
    ) ++ controls.map { case (name, f) =>
      Variant(name, frame(f) ++ rst(), false, if (lawful(name)) "pass" else "fail")
    }

  }

  for {
    (role, variants) <- Vector(
      "server" -> SmokeRejectionVariants.server,
      "client" -> SmokeRejectionVariants.client,
    )
    variant <- variants
  } test(s"smoke wire rejection census: $role/${variant.name}") {
    smokeWireRejectionProbe(role, variant)
  }

  // RFC 9113 Sections 6.3, 6.5.2, 6.8, and 6.9: boundary values, role-specific
  // SETTINGS, undefined flags, and the independently observable GOAWAY sequence.
  private def smokeRejectionBoundaries(role: String): Vector[SmokeRejectionVariants.Variant] = {
    import Http2Wire._
    import SmokeRejectionVariants.{Variant, frame, go, rst}
    val validError = go(if (role == "server") 6L else 1L)
    val controls = Vector(
      ("enable-push-zero", settings(Vector(2 -> 0L)), true),
      ("enable-push-one", settings(Vector(2 -> 1L)), role == "client"),
      ("enable-push-two", settings(Vector(2 -> 2L)), false),
      ("initial-window-max", settings(Vector(4 -> 0x7fffffffL)), true),
      ("initial-window-overflow", settings(Vector(4 -> 0x80000000L)), false),
      ("max-frame-min", settings(Vector(5 -> 16384L)), true),
      ("max-frame-max", settings(Vector(5 -> 0xffffffL)), true),
      ("max-frame-overflow", settings(Vector(5 -> 0x1000000L)), false),
      ("invalid-before-valid-setting", settings(Vector(5 -> 1L, 5 -> 16384L)), false),
      ("unknown-setting", settings(Vector(65535 -> 0xffffffffL)), true),
      ("settings-undefined-flags", settings().copy(flags = 0xfe), true),
      ("settings-ack-undefined-flags", settings(ack = true).copy(flags = 0xff), true),
      (
        "priority-idle-exclusive",
        Frame(Priority, 255, 37, uint32(0x80000000L) ++ ByteVector(255)),
        true,
      ),
      ("priority-self-dependency", Frame(Priority, 0, 1, uint32(1) ++ ByteVector(0)), true),
      ("window-update-reserved", Frame(WindowUpdate, 255, 0, uint32(0x80000001L)), true),
      ("window-update-reserved-zero", Frame(WindowUpdate, 0, 0, uint32(0x80000000L)), false),
      ("ping-undefined-flags", Frame(Ping, 0xfe, 0, Http1Wire.ascii("abcdefgh")), true),
      ("ping-ack", Frame(Ping, Ack, 0, Http1Wire.ascii("abcdefgh")), role == "client"),
    ) ++ SmokeRejectionVariants.controls.map { case (name, f) =>
      (name, f, SmokeRejectionVariants.lawful(name))
    }
    controls.flatMap { case (name, control, valid) =>
      Vector("prefix" -> (frame(control) ++ validError), "tail" -> (validError ++ frame(control)))
        .map { case (position, bytes) =>
          Variant(s"$name-$position", bytes, expected = if (valid) "pass" else "fail")
        }
    } ++ Vector(
      Variant(
        "goaway-last-id-increase",
        go(0, 0) ++ go(if (role == "server") 6L else 1L, if (role == "server") 1L else 2L),
        expected = "fail",
      ),
      Variant(
        "goaway-last-id-increase-tail",
        validError ++ go(0, if (role == "server") 1L else 2L),
        expected = "fail",
      ),
      Variant(
        "goaway-reserved-last-id",
        go(0, if (role == "server") 0xffffffffL else 0xfffffffeL) ++ validError,
      ),
      Variant(
        "goaway-undefined-flags-debug",
        frame(
          Frame(
            GoAway,
            255,
            0,
            uint32(0) ++ uint32(if (role == "server") 6L else 1L) ++ ByteVector(1, 2),
          )
        ),
      ),
      Variant(
        "goaway-short-tail",
        validError ++ frame(Frame(GoAway, 0, 0, ByteVector(0))),
        expected = "fail",
      ),
      Variant("goaway-wrong-code-tail", validError ++ go(9), expected = "fail"),
      Variant("goaway-truncated-tail", validError ++ go(0).dropRight(1), expected = "fail"),
      Variant("goaway-graceful-tail", validError ++ go(0)),
    ) ++ (if (role == "client")
            Vector(
              Variant("rst-valid-tail", validError ++ rst()),
              Variant("rst-wrong-stream-tail", validError ++ rst(1, 3), expected = "fail"),
              Variant("rst-wrong-code-tail", validError ++ rst(6), expected = "fail"),
              Variant(
                "rst-short-tail",
                validError ++ frame(Frame(RstStream, 0, 1, ByteVector(1))),
                expected = "fail",
              ),
            )
          else Vector.empty)
  }

  for {
    role <- Vector("server", "client")
    variant <- smokeRejectionBoundaries(role)
  } test(s"smoke wire rejection boundary: $role/${variant.name}") {
    smokeWireRejectionProbe(role, variant)
  }

  private def smokeWireRejectionProbe(
      role: String,
      variant: SmokeRejectionVariants.Variant,
  ): IO[Unit] = {
    import Http2Wire._
    import scala.concurrent.duration._
    def emit(peer: RawPeer[IO]): IO[Unit] =
      peer.write(variant.bytes) *> (if (variant.close) peer.endOfOutput else IO.unit)
    val run =
      if (role == "server")
        runServer("h2.server.reject-ping-length") { peer =>
          for {
            _ <- serverHandshake(peer)
            healthy <- readUntil(peer, fromServer = false)(f =>
              f.frameType == Ping && !f.hasFlag(Ack)
            )
            _ <- Check.equal(healthy.payload.size, 8L)
            _ <- writeFrame(peer, healthy.copy(flags = Ack))
            bad <- readUntil(peer, fromServer = false)(f => f.frameType == Ping && !f.hasFlag(Ack))
            _ <- Check.equal(bad.payload.size, 7L)
            _ <- emit(peer)
            _ <- IO.never[Unit]
          } yield ()
        }
      else
        runClient("h2.client.reject-content-length-mismatch") { peer =>
          for {
            _ <- startRequest(peer)
            first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
            decoder <- HpackWire.decoder[IO]()
            fields <- readHeaders(peer, first, decoder)
            data <- readUntil(peer, fromServer = true)(_.frameType == Data)
            _ <- Check.equal(
              fields.collectFirst { case ("content-length", value) => value },
              Some("4"),
            )
            _ <- Check.equal(data.payload, Http1Wire.ascii("abc"))
            _ <- Check(data.hasFlag(EndStream), "response must be complete before target rejection")
            _ <- emit(peer)
          } yield ResponseData[IO](
            200,
            Vector.empty,
            Stream.raiseError[IO](new RuntimeException("native content-length rejection")),
            IO.pure(Vector.empty),
          )
        }
    run.timeout(3.seconds).attempt.flatMap { result =>
      val actual = result.fold(
        {
          case _: java.util.concurrent.TimeoutException => "timeout"
          case _: ProtocolFailure | _: WireDecodingFailure => "fail"
          case error => s"unexpected ${error.getClass.getName}: ${error.getMessage}"
        },
        _ => "pass",
      )
      IO(assertEquals(actual, variant.expected, s"$role/${variant.name}: $result"))
    }
  }

  // RFC 9113 sections 5.1, 6.10 and 8.4: completing a request leaves no
  // client message frames in flight, and a client cannot send PUSH_PROMISE.
  private val messageTailPolicyVectors = {
    import Http2Wire._
    Vector(
      ("no-extra-frame", Vector.empty[Frame], Option.empty[String]),
      (
        "completed-request-data",
        Vector(data(1, ByteVector(42))),
        Some("the client sent unexpected message frames after its complete request"),
      ),
      (
        "completed-request-headers",
        Vector(headers(1, Vector("x-trailer" -> "late"))),
        Some("the client sent unexpected message frames after its complete request"),
      ),
      (
        "orphan-continuation",
        Vector(Frame(Continuation, EndHeaders, 1, ByteVector.empty)),
        Some("the client sent unexpected message frames after its complete request"),
      ),
      (
        "client-push-promise",
        Vector(
          Frame(PushPromise, EndHeaders, 1, uint32(2) ++ literalFields(Vector(":method" -> "GET")))
        ),
        Some("unexpected frame while awaiting rejection"),
      ),
      (
        "valid-controls",
        Vector(
          settings(Vector(2 -> 1L)),
          Frame(Ping, 0, 0, Http1Wire.ascii("abcdefgh")),
          windowUpdate(0, 1),
          Frame(Priority, 0, 3, uint32(0) ++ ByteVector(15)),
        ),
        None,
      ),
      ("extension", Vector(Frame(0xff, 0, 0, ByteVector(42))), None),
      (
        "zero-window-update",
        Vector(Frame(WindowUpdate, 0, 0, uint32(0))),
        Some("zero WINDOW_UPDATE"),
      ),
    )
  }

  for {
    id <- Vector("h2.client.reject-uppercase-field-name", "h2.client.reject-headers-stream-zero")
    (name, extra, failure) <- messageTailPolicyVectors
    tail <- Vector(false, true)
  } test(s"message tail frame policy: $id/$name/tail=$tail") {
    import Http2Wire._
    val run = runClient(id) { peer =>
      for {
        _ <- startRequest(peer)
        stimulus <- readUntil(peer, fromServer = true)(_.frameType == Headers)
        _ <- Check.equal(stimulus.streamId, if (id.endsWith("stream-zero")) 0 else 1)
        _ <- Check(stimulus.hasFlag(EndStream), "consume the complete malformed response")
        goaway = Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))
        frames = if (tail) Vector(goaway) ++ extra else extra :+ goaway
        // One write fixes ordering. Half-close supplies independent EOF while the
        // client operation and response resources remain held by the actual case.
        _ <- peer.write(frames.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ encode(frame)))
        _ <- peer.endOfOutput
      } yield ResponseData[IO](
        200,
        Vector.empty,
        Stream.raiseError[IO](new RuntimeException("native malformed-response rejection")),
        IO.pure(Vector.empty),
      )
    }
    failure match {
      case Some(detail) =>
        run.attempt.flatMap(result =>
          IO {
            assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
            assert(result.swap.exists(_.getMessage.contains(detail)), result.toString)
          }
        )
      case None => run
    }
  }

  for (tail <- Vector(false, true))
    test(s"message tail frame policy preserves progressive response: tail=$tail") {
      import Http2Wire._
      runServer("h2.server.reject-uppercase-field-name") { peer =>
        for {
          _ <- serverHandshake(peer)
          request <- readUntil(peer, fromServer = false)(_.frameType == Headers)
          decoder <- HpackWire.decoder[IO]()
          fields <- readHeaders(peer, request, decoder)
          _ <- Check(fields.exists(_._1 == "X-Upper"), "consume the malformed request")
          block = literalFields(Vector(":status" -> "200"))
          progressive = Vector(
            Frame(Headers, 0, request.streamId, block.take(1)),
            Frame(Continuation, EndHeaders, request.streamId, block.drop(1)),
            data(request.streamId, ByteVector(42)),
          )
          goaway = Frame(GoAway, 0, 0, uint32(request.streamId.toLong) ++ uint32(1))
          frames = if (tail) Vector(goaway) ++ progressive else progressive :+ goaway
          _ <- peer.write(
            frames.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ encode(frame))
          )
          _ <- peer.endOfOutput
          _ <- IO.never[Unit]
        } yield ()
      }
    }

  // RFC 9113 sections 5, 6.5.2 and 6.5.3: apply the received push policy in
  // frame order, before processing the first request that could permit a push.
  for {
    id <- Vector(
      "h2.server.reject-content-length-underflow",
      "h2.server.reject-content-length-overflow",
      "h2.server.message-empty",
      "h2.server.message-binary-data",
    )
    mode <- Vector("obey-setting", "forbidden-live-promise") ++
      (if (id.contains("reject-")) Vector("forbidden-tail-promise") else Vector.empty)
  } test(s"message reference client push policy: $id/$mode") {
    import Http2Wire._
    val negative = id.contains("reject-")
    val promise = Frame(
      PushPromise,
      EndHeaders,
      1,
      uint32(2) ++ literalFields(
        Vector(
          ":method" -> "GET",
          ":scheme" -> "http",
          ":authority" -> "wire.test",
          ":path" -> "/pushed",
        )
      ),
    )
    for {
      policyAtRequest <- Ref.of[IO, Option[Long]](None)
      result <- runPublishedServer(id) { (peer, publish) =>
        def request(push: Long, remaining: Int): IO[(Frame, Long)] =
          Check(remaining > 0, "bounded request prelude") *> readFrame(peer).flatMap { frame =>
            frame.frameType match {
              case Settings =>
                IO.fromEither(Http2RejectionChecks.validate(frame, fromServer = false)) *>
                  (if (frame.hasFlag(Ack)) request(push, remaining - 1)
                   else {
                     val next = frame.payload.grouped(6).foldLeft(push) { (previous, entry) =>
                       val key = ((entry(0) & 0xff) << 8) | (entry(1) & 0xff)
                       if (key == 2) readUint32(entry.drop(2)) else previous
                     }
                     writeFrame(peer, settings(ack = true)) *> request(next, remaining - 1)
                   })
              case Headers => policyAtRequest.set(Some(push)).as(frame -> push)
              case _ => IO.raiseError(new ProtocolFailure("unexpected request prelude frame"))
            }
          }
        def content(bytes: ByteVector): IO[ByteVector] =
          readUntil(peer, fromServer = false)(_.frameType == Data).flatMap { frame =>
            val next = bytes ++ frame.payload
            if (frame.hasFlag(EndStream)) IO.pure(next) else content(next)
          }
        for {
          _ <- serverHandshake(peer)
          // The existing generic handshake consumes the initial empty SETTINGS.
          first <- request(1L, 16)
          (head, push) = first
          decoder <- HpackWire.decoder[IO]()
          fields <- readHeaders(peer, head, decoder)
          _ <- Check.equal(head.streamId, 1)
          _ <- Check.equal(fields.find(_._1 == ":authority").map(_._2), Some("wire.test"))
          // A responsive server would legally push under the old default. Send
          // this before reading DATA, before any content-length fault is known.
          _ <-
            if ((mode == "obey-setting" && push == 1L) || mode == "forbidden-live-promise")
              writeFrame(peer, promise)
            else IO.unit
          body <-
            if (head.hasFlag(EndStream)) IO.pure(ByteVector.empty)
            else content(ByteVector.empty)
          _ <-
            if (negative)
              Check.equal(body, ByteVector(0, 1, 127, 128, 255)) *>
                peer.write(
                  encode(Frame(GoAway, 0, 0, uint32(1) ++ uint32(1))) ++
                    (if (mode == "forbidden-tail-promise") encode(promise) else ByteVector.empty)
                ) *> peer.endOfOutput
            else
              publish(RequestData("GET", "/mapping", fields, body)) *>
                writeFrame(
                  peer,
                  headers(1, Vector(":status" -> "200"), endStream = body.isEmpty),
                ) *>
                (if (body.isEmpty) IO.unit else writeFrame(peer, data(1, body)))
          _ <- IO.never[Unit]
        } yield ()
      }.attempt
      policy <- policyAtRequest.get
      _ <- IO {
        assertEquals(policy, Some(0L), s"push must be disabled before HEADERS; result=$result")
        if (mode == "obey-setting") assertEquals(result, Right(()))
        else {
          assert(
            result.swap.exists {
              case _: ProtocolFailure | _: WireDecodingFailure => true
              case _ => false
            },
            result.toString,
          )
          val detail =
            if (negative) "unexpected frame while awaiting rejection: Frame(5,"
            else "unexpected HTTP/2 frame type 5 on stream 1"
          assert(result.swap.exists(_.getMessage.contains(detail)), result.toString)
        }
      }
    } yield ()
  }

  private val originalPushCancellationCases = Vector(
    "h2.client.reject-push-stream-zero",
    "h2.client.reject-push-odd-promised-stream",
    "h2.client.reject-push-unsafe-method",
    "h2.client.reject-push-request-content",
    "h2.client.reject-push-missing-method",
    "h2.client.reject-push-invalid-field",
    "h2.client.reject-push-unauthoritative-origin",
  )

  private def originalPushCancellationProbe(
      id: String,
      pushEnabled: Boolean,
      frames: Vector[Http2Wire.Frame],
      close: Boolean,
      onSent: IO[Unit] = IO.unit,
  ): IO[Unit] = {
    import Http2Wire._
    runClient(id) { peer =>
      for {
        _ <- peer.write(
          clientPreface ++ encode(settings(Vector(2 -> (if (pushEnabled) 1L else 0L))))
        )
        _ <- readUntil(peer, fromServer = true)(f => f.frameType == Settings && !f.hasFlag(Ack))
        _ <- writeFrame(peer, settings(ack = true))
        _ <- writeFrame(
          peer,
          headers(
            1,
            Vector(
              ":method" -> "GET",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/",
              "content-length" -> "0",
            ),
          ),
        )
        promise <- readUntil(peer, fromServer = true)(_.frameType == PushPromise)
        _ <- Check.equal(promise.streamId, if (id.endsWith("stream-zero")) 0 else 1)
        _ <- Check.equal(
          readUint32(promise.payload.take(4)),
          if (id.endsWith("odd-promised-stream")) 3L else 2L,
        )
        // Cancel while the original request is half-closed (local), before processing
        // its final response. Keep the rejection and incidental frames in one write.
        _ <- peer.write(frames.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ encode(frame)))
        _ <- readUntil(peer, fromServer = true)(_.frameType == Headers)
        _ <- if (close) peer.endOfOutput else IO.unit
        _ <- onSent
      } yield ResponseData[IO](
        200,
        Vector.empty,
        Stream.raiseError[IO](new RuntimeException("native push rejection")),
        IO.pure(Vector.empty),
      )
    }
  }

  for {
    id <- originalPushCancellationCases
    pushEnabled <- Vector(false, true)
    mode <- Vector(
      "goaway-only",
      "original-before",
      "original-after",
      "promised-reset",
      "original-only",
    )
  } test(s"original push cancellation: $id/$pushEnabled/$mode") {
    import Http2Wire._
    val original = Frame(RstStream, 0, 1, uint32(8))
    val goaway = Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))
    val promised = Frame(RstStream, 0, if (id.endsWith("odd-promised-stream")) 3 else 2, uint32(1))
    val streamVerdict =
      pushEnabled && !id.endsWith("stream-zero") && !id.endsWith("odd-promised-stream")
    val close = mode != "promised-reset" && mode != "original-only"
    val insufficient = mode == "original-only" || (mode == "promised-reset" && !streamVerdict)
    val frames = mode match {
      case "goaway-only" => Vector(goaway)
      case "original-before" => Vector(original, goaway)
      case "original-after" => Vector(goaway, original)
      case "promised-reset" => Vector(original, promised)
      case "original-only" => Vector(original)
    }
    // Exhaust a finite frame budget while keeping output open. A timeout or cleanup
    // never supplies the expected missing-verdict result.
    val bounded =
      if (insufficient) frames ++ Vector.fill(1024)(Frame(0xff, 0, 0, ByteVector.empty)) else frames
    val run = originalPushCancellationProbe(id, pushEnabled, bounded, close)
    if (insufficient)
      run
        .intercept[WireObservationLimit]
        .map(error => assert(error.getMessage.contains("without required protocol rejection")))
    else run
  }

  for {
    id <- Vector("h2.client.reject-push-stream-zero", "h2.client.reject-push-unsafe-method")
    pushEnabled <- Vector(false, true)
    tail <- Vector(false, true)
    mutation <- Vector(
      "short-reset",
      "long-reset",
      "stream-zero",
      "idle-stream",
      "wrong-goaway-code",
    )
  } test(s"original push cancellation invalid control: $id/$pushEnabled/$tail/$mutation") {
    import Http2Wire._
    val original = Frame(
      RstStream,
      0,
      if (mutation == "stream-zero") 0 else if (mutation == "idle-stream") 4 else 1,
      if (mutation == "short-reset") ByteVector(8)
      else if (mutation == "long-reset") uint32(8) ++ ByteVector(0)
      else uint32(8),
    )
    val goaway =
      Frame(GoAway, 0, 0, uint32(0) ++ uint32(if (mutation == "wrong-goaway-code") 9L else 1L))
    val frames = if (tail) Vector(goaway, original) else Vector(original, goaway)
    originalPushCancellationProbe(id, pushEnabled, frames, close = true)
      .intercept[ProtocolFailure]
      .map { error =>
        val expected = mutation match {
          case "wrong-goaway-code" => "incorrect GOAWAY error code"
          case "idle-stream" => "observed: 4"
          case _ => "invalid RST_STREAM"
        }
        assert(error.getMessage.contains(expected), error.getMessage)
      }
  }

  for {
    id <- originalPushCancellationCases.drop(2)
  } test(s"original push cancellation retains same-promised-stream refusal: $id") {
    import Http2Wire._
    val frames = Vector(
      Frame(RstStream, 0, 1, uint32(8)),
      Frame(RstStream, 0, 2, uint32(8)),
      Frame(RstStream, 0, 2, uint32(1)),
    ) ++ Vector.fill(1024)(Frame(0xff, 0, 0, ByteVector.empty))
    originalPushCancellationProbe(id, pushEnabled = true, frames, close = false)
      .intercept[WireObservationLimit]
      .map(error => assert(error.getMessage.contains("without required protocol rejection")))
  }

  for {
    pushEnabled <- Vector(false, true)
    tail <- Vector(false, true)
    code <- Vector(0L, 1L, 2L, 0x1234L)
  } test(
    s"original push cancellation reset code is not promise evidence: $pushEnabled/$tail/$code"
  ) {
    import Http2Wire._
    val original = Frame(RstStream, 0, 1, uint32(code))
    val goaway = Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))
    val frames = if (tail) Vector(goaway, original) else Vector(original, goaway)
    originalPushCancellationProbe(
      "h2.client.reject-push-unsafe-method",
      pushEnabled,
      frames,
      close = true,
    )
  }

  for {
    pushEnabled <- Vector(false, true)
    code <- Vector(1L, 2L)
  } test(s"original push cancellation valid error code alone is insufficient: $pushEnabled/$code") {
    import Http2Wire._
    val frames = Vector(Frame(RstStream, 0, 1, uint32(code))) ++
      Vector.fill(1024)(Frame(0xff, 0, 0, ByteVector.empty))
    originalPushCancellationProbe(
      "h2.client.reject-push-unsafe-method",
      pushEnabled,
      frames,
      close = false,
    ).intercept[WireObservationLimit]
      .map(error => assert(error.getMessage.contains("without required protocol rejection")))
  }

  for {
    id <- Vector("h2.client.reject-push-stream-zero", "h2.client.reject-push-unsafe-method")
    pushEnabled <- Vector(false, true)
    tail <- Vector(false, true)
  } test(s"original push cancellation still requires GOAWAY closure: $id/$pushEnabled/$tail") {
    import Http2Wire._
    import scala.concurrent.duration._
    val original = Frame(RstStream, 0, 1, uint32(8))
    val goaway = Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))
    val frames = if (tail) Vector(goaway, original) else Vector(original, goaway)
    for {
      sent <- Deferred[IO, Unit]
      completed <- Deferred[IO, Either[Throwable, Unit]]
      run = originalPushCancellationProbe(
        id,
        pushEnabled,
        frames,
        close = false,
        sent.complete(()).void,
      ).attempt.flatMap(completed.complete).void
      _ <- Resource.make(run.start)(_.cancel).use { _ =>
        for {
          _ <- sent.get.timeout(5.seconds)
          early <- completed.get.map(Option(_)).timeoutTo(200.millis, IO.pure(None))
          _ <- IO(assertEquals(early, None, "original cancellation cannot replace GOAWAY closure"))
        } yield ()
      }
    } yield ()
  }

  // Observer boundaries: these scripts exercise the registered cases, including setup.
  for {
    wireCase <- Http2MessageCases.all.filter(_.id.startsWith("h2.client.reject-push-"))
    enabled <- Vector(false, true)
    value <- Vector(0L, 1L, 2L)
    tail <- Vector(false, true)
  } test(s"observer boundaries push role: ${wireCase.id}/$enabled/$value/$tail") {
    import Http2Wire._
    val run = runClient(wireCase.id) { peer =>
      for {
        _ <- peer.write(clientPreface ++ encode(settings(Vector(2 -> (if (enabled) 1L else 0L)))))
        _ <- readUntil(peer, fromServer = true)(f => f.frameType == Settings && !f.hasFlag(Ack))
        _ <- writeFrame(peer, settings(ack = true))
        _ <- writeFrame(
          peer,
          headers(
            1,
            Vector(
              ":method" -> "GET",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> "/",
              "content-length" -> "0",
            ),
          ),
        )
        promise <- readUntil(peer, fromServer = true)(_.frameType == PushPromise)
        _ <- Check.equal(promise.streamId, if (wireCase.id.endsWith("stream-zero")) 0 else 1)
        _ <- readUntil(peer, fromServer = true)(_.frameType == Headers)
        go = Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))
        update = settings(Vector(2 -> value))
        _ <- (if (tail) Vector(go, update) else Vector(update, go)).traverse_(writeFrame(peer, _))
        _ <- peer.endOfOutput
      } yield response(ByteVector.empty)
    }
    if (value <= 1L) run else run.intercept[ProtocolFailure].void
  }

  private val observerControlVectors = {
    import Http2Wire._
    Vector(
      "valid-priority" -> Frame(Priority, 0, 3, uint32(3) ++ ByteVector(15)),
      "bad-priority-stream" -> Frame(Priority, 0, 0, uint32(0) ++ ByteVector(15)),
      "bad-priority-length" -> Frame(Priority, 0, 3, ByteVector(0)),
      "valid-window" -> windowUpdate(0, 1),
      "bad-window-zero" -> Frame(WindowUpdate, 0, 0, uint32(0)),
      "bad-window-length" -> Frame(WindowUpdate, 0, 0, ByteVector(1)),
      "valid-settings" -> settings(Vector(4 -> 0x7fffffffL, 5 -> 16384L)),
      "bad-frame-size" -> settings(Vector(5 -> 16383L)),
      "bad-window-setting" -> settings(Vector(4 -> 0x80000000L)),
      "bad-overwritten-setting" -> settings(Vector(5 -> 16383L, 5 -> 16384L)),
      "bad-ping-ack" -> Frame(Ping, Ack, 1, ByteVector.fill(8)(0)),
    )
  }

  for {
    id <- Vector(
      "h2.client.message-empty",
      "h2.client.reject-uppercase-field-name",
      "h2.client.request-pseudo-header-mapping",
    )
    (name, control) <- observerControlVectors
  } test(s"observer boundaries Message prelude: $id/$name") {
    import Http2Wire._
    val negative = id.contains("reject-")
    val run = runClient(id) { peer =>
      for {
        _ <- clientHandshake(peer)
        _ <- writeFrame(peer, control)
        _ <- writeFrame(
          peer,
          headers(
            1,
            Vector(
              ":method" -> "GET",
              ":scheme" -> "http",
              ":authority" -> "wire.test",
              ":path" -> (if (id.endsWith("request-pseudo-header-mapping")) "/mapping" else "/"),
              "content-length" -> "0",
            ),
          ),
        )
        first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
        decoder <- HpackWire.decoder[IO]()
        fields <- readHeaders(peer, first, decoder)
        _ <-
          if (negative)
            Check(fields.exists(_._1 == "X-Upper"), "fault consumed") *> writeFrame(
              peer,
              Frame(RstStream, 0, 1, uint32(1)),
            )
          else IO.unit
      } yield ResponseData[IO](
        200,
        Vector.empty,
        if (negative) Stream.raiseError[IO](new RuntimeException("native field rejection"))
        else Stream.empty,
        IO.pure(Vector.empty),
      )
    }
    if (name.startsWith("valid")) run else run.intercept[ProtocolFailure].void
  }

  private def observerServerControls(
      id: String,
      extra: Vector[Http2Wire.Frame],
      phase: String,
  ): IO[Unit] = {
    import Http2Wire._
    runServer(id) { peer =>
      for {
        _ <- peer.readExact(24)
        _ <- readFrame(peer)
        _ <- writeFrame(peer, if (phase == "initial") extra.head else settings())
        _ <- if (phase == "prelude") extra.traverse_(writeFrame(peer, _)) else IO.unit
        _ <- writeFrame(peer, settings(ack = true))
        _ <- {
          def loop: IO[Unit] = readFrame(peer).flatMap { f =>
            if (f.frameType == Settings) loop
            else if (phase == "terminal" && (f.frameType == GoAway || f.frameType == RstStream))
              extra.traverse_(writeFrame(peer, _)) *> peer.endOfOutput *> IO.never[Unit]
            else if (f.frameType == Ping) writeFrame(peer, f.copy(flags = Ack)) *> loop
            else if (f.frameType == Headers) loop
            else if (f.frameType == Data && id == "h2.server.idle-data")
              writeFrame(
                peer,
                Frame(GoAway, 0, 0, uint32(1) ++ uint32(1)),
              ) *> peer.endOfOutput *> IO.never[Unit]
            else loop
          }
          loop
        }
      } yield ()
    }
  }

  for {
    id <- Vector(
      "h2.server.prior-knowledge-preface",
      "h2.server.idle-data",
      "h2.server.goaway-debug-data",
      "h2.server.rst-cancel-no-response",
    )
    (name, control) <- observerControlVectors ++ Vector(
      "bad-server-push" -> Http2Wire.settings(Vector(2 -> 1L))
    )
    phase = if (id.contains("goaway") || id.contains("rst-cancel")) "terminal" else "prelude"
  } test(s"observer boundaries Control State: $id/$name/$phase") {
    val run = observerServerControls(id, Vector(control), phase)
    if (name.startsWith("valid")) run else run.intercept[ProtocolFailure].void
  }

  for {
    id <- Vector("h2.server.goaway-debug-data", "h2.server.rst-cancel-no-response")
    increasing <- Vector(false, true)
  } test(s"observer boundaries GOAWAY sequence: $id/$increasing") {
    import Http2Wire._
    val ids = if (increasing) Vector(1L, 3L) else Vector(0x7fffffffL, 3L, 3L, 0L)
    val run = observerServerControls(
      id,
      ids.map(n => Frame(GoAway, 0, 0, uint32(n) ++ uint32(0))),
      "terminal",
    )
    if (increasing) run.intercept[ProtocolFailure].void else run
  }

  for {
    id <- Vector(
      "h2.client.hpack-reject-index-zero",
      "h2.client.message-empty",
      "h2.client.prior-knowledge-preface",
      "h2.client.reject-preface-first-ping",
      "h2.client.reject-ping-length-nine",
      "h2.client.default-frame-size-splits-data",
      "h2.client.idle-data",
      "h2.client.stream-credit-boundary",
    )
    count <- Vector(0, 1, 5, 23, 24)
  } test(s"observer boundaries typed preface: $id/$count") {
    val run = runClient(id)(peer =>
      peer.write(
        if (count == 24) ByteVector.fill(24)(0) else Http2Wire.clientPreface.take(count.toLong)
      ) *> peer.endOfOutput *> IO.never[ResponseData[IO]]
    )
    if (count == 24) run.intercept[ProtocolFailure].void
    else run.intercept[WireDecodingFailure].void
  }

  test("observer boundaries missing request HEADERS is a protocol failure") {
    import Http2Wire._
    runClient("h2.client.default-frame-size-splits-data") { peer =>
      for {
        _ <- peer.write(clientPreface ++ encode(settings()))
        _ <- readFrame(peer)
        _ <- readFrame(peer)
        _ <- writeFrame(peer, settings(ack = true))
        _ <- ByteVector.fill(40000)(42).grouped(16384).toVector.zipWithIndex.traverse_ {
          case (bytes, index) => writeFrame(peer, data(1, bytes, endStream = index == 2))
        }
        result <- IO.never[ResponseData[IO]]
      } yield result
    }.intercept[ProtocolFailure].void
  }

  private def observerSocket(
      prefix: ByteVector,
      suffix: ByteVector,
      writeError: Option[Throwable],
      heldOpen: Boolean,
      trace: Transcript[IO],
      eofReads: Ref[IO, Int],
      failedWrites: Ref[IO, Int],
      terminalRead: Deferred[IO, Unit],
  ): IO[RawPeer[IO]] = for {
    input <- Ref.of[IO, ByteVector](prefix ++ suffix)
    reached <- Ref.of[IO, Boolean](false)
    read = (n: Int) =>
      input
        .modify { bytes =>
          val (head, tail) = bytes.splitAt(n.toLong)
          (
            tail,
            (bytes.size <= suffix.size, if (head.isEmpty) None else Some(Chunk.byteVector(head))),
          )
        }
        .flatMap { case (terminal, bytes) =>
          (if (terminal) reached.set(true) *> terminalRead.complete(()).void else IO.unit) *>
            (if (bytes.nonEmpty) IO.pure(bytes)
             else if (heldOpen) IO.never[Option[Chunk[Byte]]]
             else eofReads.update(_ + 1).as(None))
        }
    write = (_: Chunk[Byte]) =>
      reached.get.flatMap { terminal =>
        if (terminal)
          writeError.fold(IO.unit)(error =>
            failedWrites.update(_ + 1) *> IO.raiseError[Unit](error)
          )
        else IO.unit
      }
    peer <- RawPeer.create[IO](read, write, IO.unit, trace)
  } yield peer

  private val observerSocketCases = Vector(
    "h2.server.hpack-reject-index-zero",
    "h2.server.reject-uppercase-field-name",
    "h2.server.reject-priority-length-4",
    "h2.server.half-closed-remote-data",
    "h2.server.rst-cancel-no-response",
    "h2.server.goaway-debug-data",
  )

  for {
    id <- observerSocketCases
    ping <- Vector(false, true)
    terminal <- Vector("error-eof", "bare-eof", "malformed-tail", "reset-held")
    failReply <- Vector(false, true)
  } test(
    s"observer boundaries failed reply requires independent closure: $id/$ping/$terminal/$failReply"
  ) {
    import Http2Wire._
    import scala.concurrent.duration._
    for {
      trace <- Transcript.create[IO]()
      eofReads <- Ref.of[IO, Int](0)
      failedWrites <- Ref.of[IO, Int](0)
      terminalRead <- Deferred[IO, Unit]
      responsePrefix =
        if (id.endsWith("half-closed-remote-data"))
          encode(headers(1, Vector(":status" -> "200"), endStream = false))
        else ByteVector.empty
      prefix = encode(settings()) ++ encode(settings(ack = true)) ++ responsePrefix
      control = if (ping) Frame(Ping, 0, 0, ByteVector.fill(8)(0)) else settings()
      code = if (id.contains("hpack")) 9L else 1L
      end = terminal match {
        case "bare-eof" => ByteVector.empty
        case "reset-held" =>
          encode(Frame(RstStream, 0, if (id.contains("priority-length")) 17 else 1, uint32(code)))
        case _ =>
          encode(
            Frame(GoAway, 0, 0, uint32(1) ++ uint32(code))
          ) ++ (if (terminal == "malformed-tail") encode(Frame(WindowUpdate, 0, 0, uint32(0)))
                else ByteVector.empty)
      }
      // Two queued replies ensure the observer stops writing after the first recognized failure.
      writeError <- Http2WriteFailureFixture.closedWrite
      peer <- observerSocket(
        prefix,
        encode(control) ++ encode(control) ++ end,
        if (failReply) Some(writeError) else None,
        terminal == "reset-held",
        trace,
        eofReads,
        failedWrites,
        terminalRead,
      )
      target = ScriptedServer[IO](
        "independent-observer-socket",
        Resource.pure(
          ServerHandle[IO](
            SocketAddress(ip"127.0.0.1", port"1"),
            IO.never[RequestData],
            IO.pure(None),
          )
        ),
      )
      result <- selected(id)
        .run(CaseContext(target, trace, (_, _) => Resource.pure(peer)))
        .timeout(300.millis)
        .attempt
      eof <- eofReads.get
      writes <- failedWrites.get
      _ <- IO {
        if (terminal == "malformed-tail")
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        else if (terminal == "reset-held") {
          val streamVerdict = !failReply && (id.contains("uppercase") || id.contains(
            "priority-length"
          ) || id.contains("half-closed"))
          if (streamVerdict) assertEquals(result, Right(()))
          else
            assert(
              result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]),
              result.toString,
            )
          assertEquals(eof, 0)
        } else { assertEquals(result, Right(())); assertEquals(eof, 1) }
        assertEquals(writes, if (failReply) 1 else 0)
      }
    } yield ()
  }

  for (
    (name, frame) <- observerControlVectors.filter(_._2.frameType == Http2Wire.Settings) ++ Vector(
      "bad-server-push" -> Http2Wire.settings(Vector(2 -> 1L))
    )
  )
    test(s"observer boundaries State initial settings: $name") {
      val run = observerServerControls("h2.server.idle-data", Vector(frame), "initial")
      if (name.startsWith("valid")) run else run.intercept[ProtocolFailure].void
    }

  for {
    id <- Vector(
      "h2.client.hpack-reject-index-zero",
      "h2.client.reject-uppercase-field-name",
      "h2.client.reject-content-length-mismatch",
    )
    malformedTail <- Vector(false, true)
    repeat <- 0 until 3
  } test(s"observer boundaries actual client full close: $id/$malformedTail/$repeat") {
    import Http2Wire._
    val target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "full-close-client"
      val version = "1"
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException("client only")))
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] = for {
        allocated <- Resource.make(Network[IO].connect(address).allocated.flatMap {
          case (socket, release) =>
            Ref
              .of[IO, Boolean](false)
              .map(closed =>
                (socket, closed.getAndSet(true).flatMap(done => if (done) IO.unit else release))
              )
        })(_._2)
        trace <- Resource.eval(Transcript.create[IO]())
        peer <- Resource.eval(RawPeer.create(allocated._1, trace))
      } yield new ClientDriver[IO] {
        def exchange(request: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval {
          for {
            _ <- clientHandshake(peer)
            _ <- writeFrame(
              peer,
              headers(
                1,
                Vector(
                  ":method" -> request.method,
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> request.path,
                ) ++ request.headers.map { case (n, v) => (n.toLowerCase, v) },
              ),
            )
            first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
            _ <-
              if (id.contains("content-length"))
                readUntil(peer, fromServer = true)(_.frameType == Data).void
              else IO.unit
            _ <- Check.equal(first.streamId, 1)
            code = if (id.contains("hpack")) 9L else 1L
            frames = Vector.fill(8)(Http2Wire.settings()) ++ Vector.fill(8)(
              Frame(Ping, 0, 0, ByteVector.fill(8)(0))
            ) ++ Vector(
              Frame(GoAway, 0, 0, uint32(0) ++ uint32(code))
            ) ++ (if (malformedTail) Vector(Frame(WindowUpdate, 0, 0, uint32(0))) else Vector.empty)
            _ <- peer.write(
              frames.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ encode(frame))
            )
            // A native protocol action, before the held exchange or driver is released.
            _ <- allocated._2
          } yield ResponseData[IO](
            200,
            Vector.empty,
            Stream.raiseError[IO](new RuntimeException("native protocol rejection")),
            IO.pure(Vector.empty),
          )
        }
        def exchangeStream(
            request: RequestData,
            body: FixtureCleanup[IO] => Stream[IO, Byte],
        ): Resource[IO, ResponseData[IO]] = exchange(request)
      }
    }
    for {
      trace <- Transcript.create[IO]()
      result <- selected(id).run(CaseContext(target, trace)).attempt
      _ <- IO(
        if (malformedTail)
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        else assertEquals(result, Right(()))
      )
    } yield ()
  }

  test("observer boundaries fixture journal survives recognized reply failure and EOF") {
    import Http2Wire._
    val failure = new AdapterFailure("fixture cleanup", new RuntimeException("cleanup failed"))
    def target(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = new WireTarget[IO] {
      val name = "journaled-close"
      val version = "1"
      override def withFailureObserver(next: AdapterFailure => IO[Unit]): WireTarget[IO] =
        target(next)
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.make(
          IO.pure(
            ServerHandle[IO](
              SocketAddress(ip"127.0.0.1", port"1"),
              IO.never[RequestData],
              IO.pure(None),
            )
          )
        )(_ => observe(failure))
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException("server only")))
    }
    for {
      trace <- Transcript.create[IO]()
      eof <- Ref.of[IO, Int](0)
      writes <- Ref.of[IO, Int](0)
      terminal <- Deferred[IO, Unit]
      writeError <- Http2WriteFailureFixture.closedWrite
      peer <- observerSocket(
        encode(settings()) ++ encode(settings(ack = true)),
        encode(settings()),
        Some(writeError),
        false,
        trace,
        eof,
        writes,
        terminal,
      )
      result <- AdapterFailure
        .observe(target(_ => IO.unit)) { monitored =>
          selected("h2.server.reject-priority-length-4").run(
            CaseContext(monitored, trace, (_, _) => Resource.pure(peer))
          )
        }
        .attempt
      eofCount <- eof.get
      _ <- IO { assert(result.swap.exists(_ eq failure)); assertEquals(eofCount, 1) }
    } yield ()
  }

  for {
    id <- Vector(
      "h2.server.message-empty",
      "h2.server.message-binary-data",
      "h2.server.response-lowercase-field-names",
      "h2.server.ping",
      "h2.server.reject-ping-length",
      "h2.server.half-closed-remote-data",
    )
    placement <- Vector("initial", "before-headers") ++
      (if (id.endsWith("message-binary-data")) Vector("content") else Vector.empty)
    value <- Vector(0L, 1L, 2L)
  } test(s"generic SETTINGS actual server: $id/$placement/$value") {
    import Http2Wire._
    val update = settings(Vector(2 -> value))
    val run = runPublishedServer(id) { (peer, publish) =>
      for {
        preface <- peer.readExact(clientPreface.size.toInt)
        _ <- Check.equal(preface, clientPreface)
        initial <- readFrame(peer)
        _ <- Check.equal(initial.frameType, Settings)
        _ <- peer.write(
          encode(if (placement == "initial") update else settings()) ++ encode(settings(ack = true))
        )
        _ <-
          if (id.contains("ping")) {
            for {
              ping <- readUntil(peer, fromServer = false)(_.frameType == Ping)
              _ <- if (placement == "before-headers") writeFrame(peer, update) else IO.unit
              _ <- writeFrame(peer, ping.copy(flags = Ack))
              _ <-
                if (id.contains("reject"))
                  readUntil(peer, fromServer = false)(_.frameType == Ping) *>
                    writeFrame(
                      peer,
                      Frame(GoAway, 0, 0, uint32(0) ++ uint32(6)),
                    ) *> peer.endOfOutput
                else IO.unit
            } yield ()
          } else {
            for {
              first <- readUntil(peer, fromServer = false)(_.frameType == Headers)
              decoder <- HpackWire.decoder[IO]()
              fields <- readHeaders(peer, first, decoder)
              body <-
                if (first.hasFlag(EndStream)) IO.pure(ByteVector.empty)
                else {
                  def collect(bytes: ByteVector): IO[ByteVector] =
                    readUntil(peer, fromServer = false)(_.frameType == Data).flatMap { frame =>
                      val next = bytes ++ unpad(frame)
                      if (frame.hasFlag(EndStream)) IO.pure(next) else collect(next)
                    }
                  collect(ByteVector.empty)
                }
              _ <- publish(
                RequestData(
                  method = fields.collectFirst { case (":method", method) => method }.get,
                  path = fields.collectFirst { case (":path", path) => path }.get,
                  headers = fields.filterNot(_._1.startsWith(":")),
                  body = body,
                )
              )
              _ <- if (placement == "before-headers") writeFrame(peer, update) else IO.unit
              _ <-
                if (id.contains("half-closed"))
                  writeFrame(
                    peer,
                    headers(first.streamId, Vector(":status" -> "200"), endStream = false),
                  ) *>
                    readUntil(peer, fromServer = false)(_.frameType == Data) *>
                    writeFrame(peer, Frame(RstStream, 0, first.streamId, uint32(5)))
                else {
                  val responseFields = Vector(":status" -> "200") ++
                    (if (id.contains("lowercase")) Vector("x-mixed" -> "value") else Vector.empty)
                  writeFrame(
                    peer,
                    headers(first.streamId, responseFields, endStream = body.isEmpty),
                  ) *>
                    (if (placement == "content") writeFrame(peer, update) else IO.unit) *>
                    (if (body.nonEmpty) writeFrame(peer, data(first.streamId, body)) else IO.unit)
                }
            } yield ()
          }
      } yield ()
    }
    if (value == 0L) run
    else
      run.intercept[ProtocolFailure].flatMap(error => IO(assert(error.getMessage.contains("PUSH"))))
  }

  for {
    id <- Vector(
      "h2.server.message-empty",
      "h2.server.message-binary-data",
      "h2.server.response-lowercase-field-names",
    )
    variant <- Vector("100", "103", "100-103", "101", "099", "600", "ended-100", "wrong-stream-103")
  } test(s"response framing informational sections: $id/$variant") {
    import Http2Wire._
    val run = runPublishedServer(id) { (peer, publish) =>
      for {
        _ <- serverHandshake(peer)
        first <- readUntil(peer, fromServer = false)(_.frameType == Headers)
        decoder <- HpackWire.decoder[IO]()
        fields <- readHeaders(peer, first, decoder)
        body <-
          if (first.hasFlag(EndStream)) IO.pure(ByteVector.empty)
          else {
            def collect(bytes: ByteVector): IO[ByteVector] =
              readUntil(peer, fromServer = false)(_.frameType == Data).flatMap { frame =>
                val next = bytes ++ unpad(frame)
                if (frame.hasFlag(EndStream)) IO.pure(next) else collect(next)
              }
            collect(ByteVector.empty)
          }
        _ <- publish(
          RequestData(
            method = fields.collectFirst { case (":method", value) => value }.get,
            path = fields.collectFirst { case (":path", value) => value }.get,
            headers = fields.filterNot(_._1.startsWith(":")),
            body = body,
          )
        )
        // Insert into the connection's HPACK table in the informational section;
        // the final response depends on the same decoder retaining entry 62.
        entry = HpackWire.encodeLiteral(Vector("x-mixed" -> "value"))
        statuses =
          if (variant == "100-103") Vector("100", "103")
          else Vector(variant.split("-").last)
        interim = statuses.zipWithIndex.map { case (status, index) =>
          Frame(
            Headers,
            EndHeaders | (if (variant == "ended-100") EndStream else 0),
            if (variant == "wrong-stream-103") first.streamId + 2 else first.streamId,
            HpackWire.encodeLiteral(Vector(":status" -> status)) ++
              (if (index == 0) ByteVector(0x40) ++ entry.drop(1) else ByteVector(0xbe)),
          )
        }
        finalHead = Frame(
          Headers,
          EndHeaders | (if (body.isEmpty) EndStream else 0),
          first.streamId,
          ByteVector(0x88, 0xbe),
        )
        _ <- peer.write(
          (interim :+ finalHead).foldLeft(ByteVector.empty)((bytes, frame) =>
            bytes ++ encode(frame)
          ) ++
            (if (body.isEmpty) ByteVector.empty else encode(data(first.streamId, body)))
        )
      } yield ()
    }
    if (variant == "100" || variant == "103" || variant == "100-103") run
    else
      run.intercept[ProtocolFailure].flatMap { error =>
        IO(
          assert(
            error.getMessage.contains(
              if (variant == "101") "101"
              else if (variant == "ended-100") "informational response ended"
              else if (variant == "wrong-stream-103") "expected: 1\nobserved: 3"
              else "invalid response :status"
            )
          )
        )
      }
  }

  for {
    id <- Vector("h2.client.reject-preface-first-ping", "h2.client.default-frame-size-splits-data")
    initialAck <- Vector(false, true)
  } test(s"response framing initial client SETTINGS: $id/ack=$initialAck") {
    val run = responseFramingClient(id, "valid", initialAck)
    if (initialAck) run.intercept[ProtocolFailure].flatMap { error =>
      IO(assert(error.getMessage.contains("client preface must advertise settings")))
    }
    else run
  }

  for (
    variant <- Vector(
      "valid",
      "fragmented",
      "padded",
      "trailers",
      "fragmented-trailers",
      "controls",
      "early-ack",
      "different-stream-data",
      "zero-data",
      "data-before-head",
      "all-data-before-head",
      "even-head",
      "zero-head",
      "ended-head",
      "data-after-end",
      "wrong-stream-trailers",
      "unterminated-trailers",
      "data-after-trailers",
      "interleaved-fields",
      "oversized",
    )
  ) test(s"response framing request stream and order: $variant") {
    val run = responseFramingClient("h2.client.default-frame-size-splits-data", variant, false)
    if (validResponseFramingVariants(variant)) run
    else
      run.intercept[ProtocolFailure].flatMap { error =>
        val expected = variant match {
          case "different-stream-data" | "zero-data" => "request DATA used a different stream"
          case "data-before-head" | "all-data-before-head" => "without initial HEADERS"
          case "even-head" | "zero-head" => "stream identifier must be positive and odd"
          case "ended-head" | "data-after-end" | "data-after-trailers" =>
            "request DATA arrived after END_STREAM"
          case "wrong-stream-trailers" => "request trailers used a different stream"
          case "unterminated-trailers" => "request trailers must terminate the stream"
          case "interleaved-fields" => "expected an uninterrupted CONTINUATION"
          case "oversized" => "frame exceeded default maximum"
        }
        IO(assert(error.getMessage.contains(expected), error.getMessage))
      }
  }

  private def responseFramingClient(id: String, variant: String, initialAck: Boolean): IO[Unit] =
    runClient(id) { peer =>
      import Http2Wire._
      for {
        // Both implicated cases read this frame before writing any server SETTINGS.
        _ <- peer.write(clientPreface ++ encode(settings(ack = initialAck)))
        result <-
          if (id.contains("reject-preface"))
            readFrame(peer) *> readFrame(peer) *> readFrame(peer) *>
              writeFrame(peer, settings(ack = true)) *>
              writeFrame(peer, Frame(GoAway, 0, 0, uint32(0) ++ uint32(1))) *>
              peer.endOfOutput *> IO.raiseError[ResponseData[IO]](
                new RuntimeException("native rejection")
              )
          else
            for {
              _ <- readFrame(peer)
              _ <- readFrame(peer)
              streamId = if (variant == "even-head") 2 else if (variant == "zero-head") 0 else 1
              head = headers(
                streamId,
                Vector(
                  ":method" -> "POST",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> "/",
                  "content-length" -> "40000",
                ),
                endStream = variant == "ended-head",
              )
              ping = Frame(Ping, 0, 0, ByteVector.fill(8)(1))
              heads =
                if (variant == "fragmented" || variant == "interleaved-fields")
                  Vector(head.copy(flags = 0, payload = head.payload.take(3))) ++
                    (if (variant == "interleaved-fields") Vector(ping) else Vector.empty) ++
                    Vector(Frame(Continuation, EndHeaders, streamId, head.payload.drop(3)))
                else Vector(head)
              trailers = variant.contains("trailers")
              chunks = ByteVector
                .fill(40000)(42)
                .grouped(if (variant == "oversized") 20000 else 16000)
                .toVector
              content = chunks.zipWithIndex.map { case (bytes, index) =>
                val frame = data(
                  if (variant == "different-stream-data") 3
                  else if (variant == "zero-data") 0
                  else streamId,
                  bytes,
                  endStream =
                    if (variant == "data-after-end") index == 0
                    else !trailers && index == chunks.size - 1,
                )
                if (variant == "padded")
                  frame.copy(
                    flags = frame.flags | Padded,
                    payload = ByteVector(1) ++ bytes ++ ByteVector(0),
                  )
                else frame
              }
              trailer = headers(
                if (variant == "wrong-stream-trailers") 3 else streamId,
                Vector("x-trailer" -> "value"),
                endStream = variant != "unterminated-trailers",
              )
              tail =
                if (variant == "fragmented-trailers")
                  Vector(
                    trailer.copy(flags = EndStream, payload = trailer.payload.take(2)),
                    Frame(Continuation, EndHeaders, streamId, trailer.payload.drop(2)),
                  )
                else if (trailers)
                  Vector(trailer) ++
                    (if (variant == "unterminated-trailers" || variant == "data-after-trailers")
                       Vector(data(streamId, ByteVector.empty))
                     else Vector.empty)
                else Vector.empty
              frames =
                if (variant == "data-before-head") content.take(1) ++ heads ++ content.drop(1)
                else if (variant == "all-data-before-head") content ++ heads
                else
                  heads ++ (if (variant == "controls") Vector(ping, settings())
                            else Vector.empty) ++ content ++ tail
              ack = Vector(settings(ack = true))
              _ <- peer.write(
                (if (variant == "early-ack") ack ++ frames else frames ++ ack)
                  .foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ encode(frame))
              )
              first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
              decoder <- HpackWire.decoder[IO]()
              fields <- readHeaders(peer, first, decoder)
              body <- readUntil(peer, fromServer = true)(_.frameType == Data).map(unpad)
            } yield ResponseData[IO](
              fields.collectFirst { case (":status", value) => value.toInt }.get,
              Vector.empty,
              Stream.chunk(Chunk.byteVector(body)),
              IO.pure(Vector.empty),
            )
      } yield result
    }

  // The fixture never sends PUSH_PROMISE: even stream 2 is idle regardless of
  // the target's first odd request ID, including the maximum legal identifier.
  for {
    initialStream <- Vector(1, 17, 19, 0x7fffffff)
    suffix <- Vector("reject-rst-idle-stream", "priority-idle", "priority-exclusive-max-dependency")
    errorCode <-
      if (suffix.startsWith("reject")) Vector(None, Some(0L), Some(1L), Some(2L), Some(5L))
      else Vector(None)
  } test(s"client idle control exact stimulus: $suffix/$initialStream/$errorCode") {
    import Http2Wire._
    val reset = suffix.startsWith("reject")
    val expected =
      if (reset) Frame(RstStream, 0, 2, uint32(8L))
      else if (suffix == "priority-idle") Frame(Priority, 0xff, 2, uint32(0L) ++ ByteVector(0))
      else Frame(Priority, 0xff, 2, uint32(0xffffffffL) ++ ByteVector(255.toByte))
    for {
      observed <- Ref.of[IO, Option[Frame]](None)
      result <- runClient(s"h2.client.$suffix") { peer =>
        for {
          _ <- startRequest(peer, streamId = initialStream)
          // readUntil rejects unexpected stream traffic, including PUSH_PROMISE.
          stimulus <- readUntil(peer, fromServer = true)(_.frameType == expected.frameType)
          _ <- observed.set(Some(stimulus))
          _ <-
            if (reset) IO.unit
            else
              readUntil(peer, fromServer = true)(_.frameType == Ping).flatMap(ping =>
                writeFrame(peer, ping.copy(flags = Ack))
              )
          head <- readUntil(peer, fromServer = true)(_.frameType == Headers)
          body <- readUntil(peer, fromServer = true)(_.frameType == Data)
          _ <- Check.equal(head.streamId, initialStream)
          _ <- Check.equal(body.streamId, initialStream)
          _ <-
            if (reset)
              errorCode.traverse_(code =>
                writeFrame(peer, Frame(GoAway, 0, 0, uint32(0L) ++ uint32(code)))
              ) *> peer.endOfOutput
            else IO.unit
        } yield response(body.payload)
      }.attempt
      stimulus <- observed.get
      _ <- IO {
        assertEquals(stimulus, Some(expected))
        if (errorCode.contains(5L))
          assert(
            result.swap.exists(error =>
              error.isInstanceOf[ProtocolFailure] &&
                error.getMessage.contains("unexpected GOAWAY error code")
            ),
            result.toString,
          )
        else assertEquals(result, Right(()))
      }
    } yield ()
  }

  private def runPublishedServer(id: String)(script: ScriptedTarget.Script[IO]): IO[Unit] = for {
    fixture <- ScriptedTarget.create[IO](script)
    transcript <- Transcript.create[IO]()
    _ <- selected(id).run(CaseContext(fixture._1, transcript))
  } yield ()

  private def replace(bytes: ByteVector, from: String, to: String): ByteVector = {
    val original = ByteVector.fromValidHex(from)
    assert(bytes.endsWith(original))
    bytes.dropRight(original.size) ++ ByteVector.fromValidHex(to)
  }

  private def response(body: ByteVector): ResponseData[IO] =
    ResponseData[IO](
      200,
      Vector.empty,
      Stream.chunk(fs2.Chunk.byteVector(body)),
      IO.pure(Vector.empty),
    )

  // This target fixture retains the default HPACK capacity for encoder-reduction controls.
  private def startRequest(
      peer: RawPeer[IO],
      fields: Vector[(String, String)] = Vector.empty,
      streamId: Int = 1,
  ): IO[Unit] =
    Http2Wire.clientHandshake(peer) *> Http2Wire.writeFrame(
      peer,
      Http2Wire.Frame(
        Http2Wire.Headers,
        Http2Wire.EndHeaders | Http2Wire.EndStream,
        streamId,
        HpackWire.encodeLiteral(
          Vector(
            ":method" -> "GET",
            ":scheme" -> "http",
            ":authority" -> "wire.test",
            ":path" -> "/",
          ) ++ fields
        ),
      ),
    )

  private def selected(id: String): WireCase =
    Catalog.all.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"missing case $id"))

  private def runServer(
      id: String,
      checkTranscript: Transcript[IO] => IO[Unit] = _ => IO.unit,
  )(script: RawPeer[IO] => IO[Unit]): IO[Unit] = for {
    scriptTranscript <- Ref.of[IO, Option[Transcript[IO]]](None)
    fixture <- ScriptedTarget.create[IO]((peer, _) =>
      scriptTranscript.set(Some(peer.transcript)) *> script(peer)
    )
    transcript <- Transcript.create[IO]()
    _ <- selected(id).run(CaseContext(fixture._1, transcript)).onCancel {
      // Only cancellation emits diagnostics; expected oracle failures stay quiet.
      // Keep both bounded prefixes and the omission count without dumping large payloads.
      def summarize(trace: Transcript[IO]): IO[String] =
        trace.render.map(value => value.take(2048) + "\n...\n" + value.takeRight(128))
      (summarize(transcript), scriptTranscript.get.flatMap(_.traverse(summarize))).tupled
        .flatMap { case (observer, scripted) =>
          IO.println(
            s"Canceled H2 oracle $id\nObserver:\n$observer\nScript:\n${scripted.getOrElse("not connected")}"
          )
        }
    }
    _ <- checkTranscript(transcript)
  } yield ()

  private def runClient(id: String)(script: RawPeer[IO] => IO[ResponseData[IO]]): IO[Unit] = for {
    transcript <- Transcript.create[IO]()
    scripted <- Transcript.create[IO]()
    _ <- MemoryPeer.pair(transcript, scripted).use { case (server, client) =>
      val address = SocketAddress(ip"127.0.0.1", port"1")
      val target = clientTarget(script, connection = (_, _) => Resource.pure(client))
      selected(id).run(
        CaseContext(
          target,
          transcript,
          clientListener =
            _ => Resource.pure(WirePeerTransport.Listener(address, Resource.pure(server))),
        )
      )
    }
  } yield ()

  private def clientTarget(
      script: RawPeer[IO] => IO[ResponseData[IO]],
      background: RawPeer[IO] => Resource[IO, Unit] = _ => Resource.unit[IO],
      onRelease: IO[Unit] = IO.unit,
      connection: (SocketAddress[IpAddress], Transcript[IO]) => Resource[IO, RawPeer[IO]] =
        (address, transcript) => WirePeerTransport.connect[IO](address, transcript),
  ): WireTarget[IO] = ScriptedClient[IO](connection) { (_, peer) =>
    Resource.onFinalize(onRelease) *> background(peer).as(
      ScriptedClient.ignoringUpload[IO](_ => Resource.eval(script(peer)))
    )
  }
}
