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
import scodec.bits.ByteVector
import munit.CatsEffectSuite

private object Http2PositiveCompletionFixture {
  final case class Mode(
      name: String,
      graceful: Boolean = false,
      beforeHead: Boolean = false,
      trailers: Boolean = false,
      control: String = "",
      failWrite: Boolean = false,
      missing: Boolean = false,
      wrong: Boolean = false,
      badTail: Boolean = false,
  )
}

class Http2PositiveCompletionSuite extends CatsEffectSuite {

  import Http2PositiveCompletionFixture.Mode

  private val pingBytes = ByteVector.fromValidHex("0102030405060708")
  private def bytes(fs: Vector[Http2Wire.Frame]): ByteVector =
    fs.foldLeft(ByteVector.empty)(_ ++ Http2Wire.encode(_))
  private def goaway(last: Int, code: Long = 0L): Http2Wire.Frame =
    Http2Wire.Frame(Http2Wire.GoAway, 0, 0, Http2Wire.uint32(last.toLong) ++ Http2Wire.uint32(code))
  private val families =
    Http2MessageCases.all ++ HpackCases.all ++ InteractionCases.all ++ SmokeCases.all
  private val serverIds = Vector(
    "h2.server.message-binary-data",
    "h2.server.hpack-literal-without-new-name",
    "h2.server.interaction-coalesced-preface-request",
    "h2.server.ping",
  )
  private val clientIds = Vector(
    "h2.client.message-empty",
    "h2.client.hpack-literal-without-new-name",
    "h2.client.interaction-eager-server-settings",
    "h2.client.headers-and-data",
  )
  private val modes = Vector(
    Mode("control-data"),
    Mode("control-trailers", trailers = true),
    Mode("graceful-before-head", graceful = true, beforeHead = true),
    Mode("graceful-data", graceful = true),
    Mode("graceful-trailers", graceful = true, trailers = true),
    Mode("missing-end", missing = true),
    Mode("wrong-body", wrong = true),
    Mode("ping-writable", control = "ping"),
    Mode("ping-closed", control = "ping", failWrite = true),
    Mode("settings-writable", control = "settings"),
    Mode("settings-closed", control = "settings", failWrite = true),
    Mode("ping-closed-missing", control = "ping", failWrite = true, missing = true),
    Mode("ping-closed-wrong", control = "ping", failWrite = true, wrong = true),
    Mode("ping-closed-graceful", graceful = true, control = "ping", failWrite = true),
    Mode("ping-closed-bad-tail", control = "ping", failWrite = true, badTail = true),
  )
  private def serverProbe(
      id: String,
      mode: Mode,
      hold: Option[(Deferred[IO, Unit], Deferred[IO, Unit])] = None,
  ): IO[Either[Throwable, Unit]] = for {
    closedWrite <- Http2WriteFailureFixture.closedWrite
    transcript <- Transcript.create[IO]()
    inbound <- Ref.of[IO, ByteVector](Http2Wire.encode(Http2Wire.settings()))
    received <- Ref.of[IO, Vector[RequestData]](Vector.empty)
    fields <- Ref.of[IO, Map[Int, Vector[(String, String)]]](Map.empty)
    bodies <- Ref.of[IO, Map[Int, ByteVector]](Map.empty)
    active <- Ref.of[IO, Boolean](false)
    decoder <- HpackWire.decoder[IO]()
    peer <- {
      def enqueue(fs: Vector[Http2Wire.Frame]): IO[Unit] = inbound.update(_ ++ bytes(fs))
      def complete(sid: Int): IO[Unit] = for {
        f <- fields.get.map(_(sid))
        b <- bodies.get.map(_.getOrElse(sid, ByteVector.empty))
        request = RequestData(
          f.collectFirst { case (":method", x) => x }.get,
          f.collectFirst { case (":path", x) => x }.get,
          f.filterNot(_._1.startsWith(":")),
          b,
        )
        _ <- received.update(_ :+ request)
        _ <- active.set(true)
        body = if (mode.wrong) b ++ ByteVector(99.toByte) else b
        head = Http2Wire.headers(
          sid,
          Vector(":status" -> "200", "content-length" -> b.size.toString),
          endStream = false,
        )
        trailer = Http2Wire.headers(sid, Vector("x-finish" -> "yes"), endStream = true)
        end =
          if (mode.missing) Vector.empty
          else if (mode.trailers) Vector(Http2Wire.data(sid, body, endStream = false), trailer)
          else Vector(Http2Wire.data(sid, body))
        incidental = mode.control match {
          case "ping" => Vector(Http2Wire.Frame(Http2Wire.Ping, 0, 0, pingBytes))

          case "settings" => Vector(Http2Wire.settings())
          case _ => Vector.empty
        }
        gs = (if (mode.graceful) Vector(goaway(sid)) else Vector.empty) ++
          (if (mode.badTail) Vector(Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector(1)))
           else Vector.empty)
        response = (if (mode.beforeHead) gs else Vector.empty) ++ Vector(
          head
        ) ++ incidental ++ (if (mode.beforeHead) Vector.empty else gs) ++ end
        _ <- enqueue(response)
      } yield ()
      def onFrame(f: Http2Wire.Frame): IO[Unit] = for {
        started <- active.get
        fail =
          mode.failWrite && started && ((mode.control == "ping" && f.frameType == Http2Wire.Ping && f
            .hasFlag(
              Http2Wire.Ack
            )) || (mode.control == "settings" && f.frameType == Http2Wire.Settings && f.hasFlag(
            Http2Wire.Ack
          )))
        _ <-
          if (fail) IO.raiseError[Unit](closedWrite)
          else
            f.frameType match {
              case Http2Wire.Settings if !f.hasFlag(Http2Wire.Ack) =>
                enqueue(Vector(Http2Wire.settings(ack = true)))
              case Http2Wire.Ping if !f.hasFlag(Http2Wire.Ack) && id == "h2.server.ping" =>
                val incidental = mode.control match {
                  case "ping" => Vector(Http2Wire.Frame(Http2Wire.Ping, 0, 0, pingBytes))

                  case "settings" => Vector(Http2Wire.settings())
                  case _ => Vector.empty
                }
                val gs = (if (mode.graceful) Vector(goaway(0)) else Vector.empty) ++
                  (if (mode.badTail) Vector(Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector(1)))
                   else Vector.empty)
                val response =
                  incidental ++ gs ++ (if (mode.missing) Vector.empty
                                       else
                                         Vector(
                                           f.copy(
                                             flags = Http2Wire.Ack,
                                             payload = if (mode.wrong) pingBytes else f.payload,
                                           )
                                         ))
                active.set(true) *> enqueue(response)
              case Http2Wire.Headers =>
                decoder
                  .decode(f.payload)
                  .flatMap(x =>
                    fields.update(_ + (f.streamId -> x))
                  ) *> (if (f.hasFlag(Http2Wire.EndStream)) complete(f.streamId) else IO.unit)
              case Http2Wire.Data =>
                bodies.update(m =>
                  m.updated(
                    f.streamId,
                    m.getOrElse(f.streamId, ByteVector.empty) ++ Http2Wire.unpad(f),
                  )
                ) *> (if (f.hasFlag(Http2Wire.EndStream)) complete(f.streamId) else IO.unit)
              case _ => IO.unit
            }
      } yield ()
      RawPeer.create[IO](
        (n: Int) =>
          inbound
            .modify(b =>
              (b.drop(n.toLong), if (b.isEmpty) None else Some(Chunk.byteVector(b.take(n.toLong))))
            )
            .flatMap {
              case None if hold.nonEmpty => hold.get._1.complete(()).void *> IO.never
              case result => IO.pure(result)
            },
        (c: Chunk[Byte]) => {
          val b = c.toByteVector

          val fs =
            if (b.startsWith(Http2Wire.clientPreface)) b.drop(Http2Wire.clientPreface.size) else b

          IO.fromEither(Http2Wire.decodeAll(fs)).flatMap(_.traverse_(onFrame))
        },
        IO.unit,
        transcript,
      )
    }
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "independent-causal-server"
      val version = "review"
      def client(p: Protocol, a: SocketAddress[IpAddress]) =
        Resource.eval(IO.raiseError[ClientDriver[IO]](new Exception("not client")))
      def server(p: Protocol, s: ServerScenario) = Resource.pure[IO, ServerHandle[IO]](
        ServerHandle(
          SocketAddress(ip"127.0.0.1", port"1"),
          received.modify(v => (v.drop(1), v.head)),
          received.get.map(_.headOption),
        )
      )
    }
    c = families.find(_.id == id).get
    result <- c
      .run(
        CaseContext(
          target,
          transcript,
          (_, _) => Resource.make(IO.pure(peer))(_ => hold.traverse_(_._2.complete(()).void)),
        )
      )
      .attempt
  } yield result

  private def clientProbe(
      id: String,
      graceful: Boolean,
      trailers: Boolean,
  ): IO[Either[Throwable, Unit]] = for {
    transcript <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "independent-causal-client"
      val version = "review"
      def server(p: Protocol, s: ServerScenario) =
        Resource.eval(IO.raiseError[ServerHandle[IO]](new Exception("not server")))
      def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
        WirePeerTransport.connect[IO](a, own).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(r: RequestData, b: FixtureCleanup[IO] => Stream[IO, Byte]) =
              Resource.eval(IO.raiseError[ResponseData[IO]](new Exception("not stream")))
            def exchange(r: RequestData): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.defer {
                implicit val settings: Http2PeerSettings[IO] = Http2PeerSettings.untracked[IO]
                for {
                  _ <- peer.write(Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings()))
                  serverSettings <- Http2Wire.readFrame(peer)
                  _ <- Check.equal(serverSettings.frameType, Http2Wire.Settings)
                  _ <- peer.write(Http2Wire.encode(Http2Wire.settings(ack = true)))
                  requestFields = Vector(
                    ":method" -> r.method,
                    ":scheme" -> "http",
                    ":authority" -> "wire.test",
                    ":path" -> r.path,
                  ) ++ r.headers.map { case (k, v) => (k.toLowerCase, v) }
                  _ <- peer
                    .write(Http2Wire.encode(Http2Wire.headers(1, requestFields, endStream = false)))
                  tail =
                    (if (graceful) Vector(goaway(0))
                     else Vector.empty) ++ (if (trailers)
                                              Vector(Http2Wire.headers(1, Vector("x-end" -> "yes")))
                                            else Vector(Http2Wire.data(1, r.body)))
                  _ <- peer.write(bytes(tail))
                  decoder <- HpackWire.decoder[IO]()
                  captured <- {
                    def loop(
                        headers: Vector[(String, String)],
                        body: ByteVector,
                    ): IO[(Vector[(String, String)], ByteVector)] =
                      Http2Wire.readFrame(peer).flatMap { f =>
                        f.frameType match {
                          case Http2Wire.Settings =>
                            (if (f.hasFlag(Http2Wire.Ack)) IO.unit
                             else
                               peer.write(
                                 Http2Wire.encode(Http2Wire.settings(ack = true))
                               )) *> loop(headers, body)
                          case Http2Wire.Headers =>
                            Http2Wire
                              .readHeaders(peer, f, decoder)
                              .flatMap(h =>
                                if (f.hasFlag(Http2Wire.EndStream)) IO.pure((h, body))
                                else loop(h, body)
                              )
                          case Http2Wire.Data =>
                            if (f.hasFlag(Http2Wire.EndStream))
                              IO.pure((headers, body ++ Http2Wire.unpad(f)))
                            else loop(headers, body ++ Http2Wire.unpad(f))
                          case _ => loop(headers, body)
                        }
                      }
                    loop(Vector.empty, ByteVector.empty)
                  }
                } yield ResponseData[IO](
                  captured._1.collectFirst { case (":status", s) => s.toInt }.get,
                  captured._1.filterNot(_._1.startsWith(":")),
                  Stream.chunk(Chunk.byteVector(captured._2)),
                  IO.pure(Vector.empty),
                )
              })
          }
        }
    }
    c = families.find(_.id == id).get
    result <- c.run(CaseContext(target, transcript)).attempt
  } yield result

  for {
    id <- serverIds
    mode <- modes
  }
    test(s"positive buffered response $id / ${mode.name}") {
      serverProbe(id, mode).map { result =>
        if (mode.missing)
          assert(result.swap.exists(_.isInstanceOf[WireDecodingFailure]), result.toString)
        else if (mode.wrong || mode.badTail)
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        else assertEquals(result, Right(()))
      }
    }

  for {
    id <- clientIds
    graceful <- Vector(false, true)
    trailers <- Vector(false, true)
  }
    test(s"positive client request $id / graceful=$graceful trailers=$trailers") {
      clientProbe(id, graceful, trailers).map(result => assertEquals(result, Right(())))
    }
  import Http2Wire._
  private case class State(
      queue: ByteVector = encode(settings()),
      initial: Long = 65535,
      stream: Int = 0,
      credit: Long = 65535,
      connection: Long = 65535,
      remaining: ByteVector = ByteVector.empty,
      prefix: Boolean = false,
      ended: Boolean = false,
      closed: Boolean = false,
      failed: Int = 0,
  )
  private def stateProbe(
      name: String,
      mode: String,
      ending: String,
      phase: String,
      graceful: Boolean,
  ): IO[Either[Throwable, Unit]] = {
    import Http2Wire._
    val id = "h2.server." + name
    val body =
      if (name == "connection-credit-independent" || name == "negative-stream-window")
        ByteVector.fill(65536)(0x61.toByte)
      else Http1Wire.ascii("abcdefghijklmnop")
    def number(b: ByteVector): Long = b.toArray.foldLeft(0L)((n, x) => (n << 8) | (x & 255L))
    def add(s: State, fs: Vector[Frame]): State =
      s.copy(queue = s.queue ++ fs.foldLeft(ByteVector.empty)((b, f) => b ++ encode(f)))
    def emit(s: State): State = if (s.stream == 0 || s.ended || s.remaining.isEmpty) s
    else {
      val available = math.max(0L, math.min(s.credit, s.connection))
      if (available >= s.remaining.size) {
        val chunks = s.remaining.grouped(16384).toVector
        val ds = chunks.zipWithIndex.map { case (b, i) =>
          data(s.stream, b, endStream = ending == "data" && i == chunks.size - 1)
        }
        val tail =
          if (ending == "data") Vector.empty
          else Vector(settings(), headers(s.stream, Vector("x-finished" -> "yes"), true))
        add(
          s.copy(
            credit = s.credit - s.remaining.size,
            connection = s.connection - s.remaining.size,
            remaining = ByteVector.empty,
            ended = true,
            closed =
              phase != "barrier" && mode != "writable" && (name != "window-update-on-closed-stream" || s.stream == 3),
          ),
          (if (phase == "data" && (name != "window-update-on-closed-stream" || s.stream == 3))
             Vector(settings()) ++ (if (graceful) Vector(goaway(s.stream)) else Vector.empty)
           else Vector.empty) ++ ds ++ tail,
        )
      } else if (!s.prefix && available > 0) {
        val n = if (phase == "barrier") available else 1L
        add(
          s.copy(
            credit = s.credit - n,
            connection = s.connection - n,
            remaining = s.remaining.drop(n),
            prefix = true,
          ),
          s.remaining.take(n).grouped(16384).toVector.map(b => data(s.stream, b, endStream = false)),
        )
      } else s
    }
    def receive(s: State, f: Frame): State = f.frameType match {
      case Settings if !f.hasFlag(Ack) =>
        val next = f.payload.grouped(6).foldLeft(s) { (cur, b) =>
          if (number(b.take(2)) == 4) {
            val v = number(b.drop(2))
            cur.copy(initial = v, credit = cur.credit + v - cur.initial)
          } else cur
        }
        emit(add(next, Vector(settings(ack = true))))
      case Headers =>
        emit(
          add(
            s.copy(
              stream = f.streamId,
              credit = s.initial,
              remaining = body,
              prefix = false,
              ended = false,
            ),
            (if (phase == "head" && (name != "window-update-on-closed-stream" || f.streamId == 3))
               Vector(settings()) ++ (if (graceful) Vector(goaway(f.streamId)) else Vector.empty)
             else Vector.empty) ++ Vector(headers(f.streamId, Vector(":status" -> "200"), false)),
          )
        )
      case WindowUpdate =>
        val v = number(f.payload) & 0x7fffffffL
        emit(
          if (f.streamId == 0) s.copy(connection = s.connection + v)
          else if (f.streamId == s.stream && !s.ended) s.copy(credit = s.credit + v)
          else s
        )
      case RstStream => s.copy(ended = true)
      case Ping if !f.hasFlag(Ack) =>
        if (
          phase == "barrier" && s.ended && (name != "reset-closes-blocked-stream" || s.stream == 3)
        )
          add(
            s.copy(closed = mode != "writable"),
            Vector(settings()) ++ (if (graceful) Vector(goaway(s.stream))
                                   else Vector.empty) ++ Vector(f.copy(flags = Ack)),
          )
        else add(s, Vector(f.copy(flags = Ack)))
      case _ => s
    }
    for {
      closedWrite <- Http2WriteFailureFixture.closedWrite
      trace <- Transcript.create[IO]()
      state <- Ref.of[IO, State](State())
      released <- Ref.of[IO, Boolean](false)
      peer <- RawPeer.create[IO](
        (n: Int) =>
          state
            .modify { s =>
              val b = s.queue.take(n.toLong)
              (s.copy(queue = s.queue.drop(n.toLong)), b)
            }
            .map(b => if (b.isEmpty) None else Some(Chunk.byteVector(b))),
        (c: Chunk[Byte]) => {
          val b = ByteVector(c.toArray)

          val wire = if (b.startsWith(clientPreface)) b.drop(clientPreface.size) else b
          IO.fromEither(decodeAll(wire))
            .flatMap(_.traverse_ { f =>
              state
                .modify { s =>
                  val reject = s.closed && (mode == "first" || f.frameType != WindowUpdate)
                  if (reject) (s.copy(failed = s.failed + 1), true) else (receive(s, f), false)
                }
                .flatMap(failed => IO.raiseWhen(failed)(closedWrite))
            })
        },
        IO.unit,
        trace,
      )
      address = SocketAddress(ip"127.0.0.1", port"1")
      target = new WireTarget[IO] {
        def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

        val name = "causal-flow-completion"
        val version = "1"
        def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
          Resource.pure(ServerHandle[IO](address, IO.never[RequestData], IO.pure(None)))
        def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
          Resource.eval(IO.raiseError(new Exception("unused")))
      }
      result <- Http2StateCases.all
        .find(_.id == id)
        .get
        .run(
          CaseContext(
            target,
            trace,
            (_, _) => Resource.make(IO.pure(peer))(_ => released.set(true)),
          )
        )
        .attempt
      isReleased <- released.get
      _ <- IO(assert(isReleased))
    } yield result
  }

  private val flowCases = Vector(
    "window-update-after-peer-end",
    "stream-credit-boundary",
    "connection-credit-independent",
    "negative-stream-window",
    "maximum-stream-window-update",
    "window-update-on-closed-stream",
    "peer-concurrency-zero-allows-responses",
  )
  private val flowBoundaries = flowCases.map(_ -> "data") ++
    Vector("window-update-on-closed-stream", "peer-concurrency-zero-allows-responses").map(
      _ -> "head"
    ) ++
    Vector("negative-stream-window", "reset-closes-blocked-stream").map(_ -> "barrier")
  for {
    (name, phase) <- flowBoundaries
    mode <- Vector("writable", "first", "control")
    ending <- Vector("data", "trailers")
    graceful <- Vector(false, true)
  }
    test(s"positive State completion $name / $phase $mode $ending graceful=$graceful") {
      stateProbe(name, mode, ending, phase, graceful).map(result => assertEquals(result, Right(())))
    }

  private val controlNames = Vector(
    "prior-knowledge-preface",
    "unknown-connection-frame",
    "unknown-stream-frame",
    "unknown-empty-frame",
    "unknown-max-default-frame",
    "ignore-reserved-frame-bit",
    "ignore-ping-unused-flags",
    "ignore-settings-unused-flags",
    "settings-empty-update",
    "settings-unknown-identifier",
    "settings-max-frame-minimum",
    "settings-max-frame-maximum",
    "settings-enable-push-zero",
    "settings-header-list-advisory",
    "settings-ordered-acknowledgments",
    "ping-ack-no-response",
    "ping-all-octets",
    "settings-client-enable-push-one",
    "settings-header-table-zero",
    "settings-header-table-uint32",
    "settings-window-zero",
    "settings-window-maximum",
    "settings-concurrency-zero",
    "settings-concurrency-uint32",
    "settings-header-list-zero",
    "priority-idle",
    "priority-exclusive-max-dependency",
    "priority-does-not-open-stream",
    "priority-on-closed-stream",
    "default-frame-size-splits-data",
    "settings-replace-frame-limit",
  )
  private def controlProbe(name: String, mode: String): IO[Either[Throwable, Unit]] = {
    val responseEnd = Set(
      "priority-does-not-open-stream",
      "default-frame-size-splits-data",
      "settings-replace-frame-limit",
    )(name)
    val body =
      if (name.contains("frame-size") || name == "settings-replace-frame-limit")
        ByteVector.fill(40000)(42.toByte)
      else Http1Wire.ascii("accepted")
    case class State(
        queue: ByteVector = encode(settings()),
        closed: Boolean = false,
        terminalDone: Boolean = false,
        stream: Int = 0,
        failed: Int = 0,
    )
    def add(s: State, fs: Vector[Frame]): State =
      s.copy(queue = s.queue ++ fs.foldLeft(ByteVector.empty)((b, f) => b ++ encode(f)))
    def terminal(s: State, fs: Vector[Frame]): State = {
      val go =
        if (mode == "goaway") Vector(Frame(GoAway, 0, 0, uint32(s.stream.toLong) ++ uint32(0)))
        else Vector.empty
      add(
        s.copy(closed = mode == "closed" || mode == "unissued", terminalDone = true),
        go ++ Vector(settings()) ++ fs,
      )
    }
    def receive(s: State, f: Frame): State = f.frameType match {
      case Settings if !f.hasFlag(Ack) =>
        if (name == "prior-knowledge-preface") s else add(s, Vector(settings(ack = true)))
      case Settings if f.hasFlag(Ack) && name == "prior-knowledge-preface" && !s.terminalDone =>
        terminal(s, Vector(settings(ack = true)))
      case Headers =>
        val next = add(
          s.copy(stream = f.streamId),
          Vector(headers(f.streamId, Vector(":status" -> "200"), false)),
        )
        val ds = body.grouped(16384).toVector.zipWithIndex.map { case (b, i) =>
          data(f.streamId, b, endStream = (i + 1) * 16384 >= body.size)
        }
        if (responseEnd) terminal(next, ds) else add(next, ds)
      case Ping if !f.hasFlag(Ack) && f.payload != ByteVector(0, 1, 2, 3, 4, 5, 6, 7) =>
        if (mode == "unissued") terminal(s, Vector(f.copy(flags = Ack)))
        else add(s, Vector(f.copy(flags = Ack)))
      case Ping if !f.hasFlag(Ack) => terminal(s, Vector(f.copy(flags = Ack)))
      case _ => s
    }
    for {
      closedWrite <- Http2WriteFailureFixture.closedWrite
      tr <- Transcript.create[IO]()
      st <- Ref.of[IO, State](State())
      released <- Ref.of[IO, Boolean](false)
      peer <- RawPeer.create[IO](
        (n: Int) =>
          st.modify(s => (s.copy(queue = s.queue.drop(n.toLong)), s.queue.take(n.toLong)))
            .map(b => if (b.isEmpty) None else Some(Chunk.byteVector(b))),
        (c: Chunk[Byte]) => {
          val b = ByteVector(c.toArray)

          val wire = if (b.startsWith(clientPreface)) b.drop(clientPreface.size) else b
          IO.fromEither(decodeAll(wire))
            .flatMap(
              _.traverse_(f =>
                st.modify(s =>
                  if (s.closed) (s.copy(failed = s.failed + 1), true) else (receive(s, f), false)
                ).flatMap(x => IO.raiseWhen(x)(closedWrite))
              )
            )
        },
        IO.unit,
        tr,
      )
      target = new WireTarget[IO] {
        def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

        val name = "causal-control-buffered"
        val version = "1"
        def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] = Resource.pure(
          ServerHandle[IO](
            SocketAddress(ip"127.0.0.1", port"1"),
            IO.pure(RequestData(method = "POST", path = "/control")),
            IO.pure(None),
          )
        )
        def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
          Resource.eval(IO.raiseError(new Exception("unused")))
      }
      outcome <- Http2ControlCases.all
        .find(_.id == "h2.server." + name)
        .get
        .run(
          CaseContext(target, tr, (_, _) => Resource.make(IO.pure(peer))(_ => released.set(true)))
        )
        .attempt
      rel <- released.get
      _ <- IO(assert(rel))
    } yield outcome
  }

  for {
    name <- controlNames
    mode <- Vector("writable", "closed", "goaway")
  }
    test(s"positive Control completion $name / $mode") {
      controlProbe(name, mode).map(result => assertEquals(result, Right(())))
    }

  for (name <- Vector("ignore-reserved-frame-bit", "ignore-ping-unused-flags", "ping-all-octets"))
    test(s"unissued required PING cannot pass $name") {
      controlProbe(name, "unissued").map(result => assert(result.isLeft))
    }

  for (id <- Vector("h2.server.hpack-dynamic-stream-reuse", "h2.server.hpack-dynamic-order"))
    test(s"GOAWAY prevents subsequent HPACK request $id") {
      serverProbe(id, Mode("graceful", graceful = true)).map { result =>
        assert(result.swap.exists(_.isInstanceOf[WireObservationLimit]), result.toString)
        assert(result.swap.exists(_.getMessage.contains("later-stream obligation unexercised")))
      }
    }
  for (id <- serverIds)
    test(s"closed incidental write cannot complete a held missing tail: $id") {
      for {
        blocked <- Deferred[IO, Unit]
        released <- Deferred[IO, Unit]
        fiber <- serverProbe(
          id,
          Mode("held", control = "ping", failWrite = true, missing = true),
          Some(blocked -> released),
        ).start
        _ <- blocked.get
        _ <- fiber.cancel
        outcome <- fiber.join
        _ <- released.get
        _ <- IO(assert(outcome.isCanceled))
      } yield ()
    }

}
