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

import scala.concurrent.duration._
class Http2ObserverHandoffSuite extends munit.CatsEffectSuite {
  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  import Http2Wire._
  val binary = ByteVector(0, 1, 127, 128, 255)
  def wire(fs: Frame*): ByteVector = fs.foldLeft(ByteVector.empty)((b, f) => b ++ encode(f))
  def go(last: Int) = Frame(GoAway, 0, 0, uint32(last.toLong) ++ uint32(0))
  def server(
      id: String,
      lateZero: Boolean,
      initialZero: Boolean,
      beforeSendZero: Boolean = false,
      coalesced: Boolean = false,
  ): IO[Unit] = for {
    tr <- Transcript.create[IO]()
    queue <- Ref.of[IO, ByteVector](
      if (coalesced) ByteVector.empty
      else encode(Http2Wire.settings(if (beforeSendZero) Vector(4 -> 0L) else Vector.empty))
    )
    delivered <- Ref.of[IO, ByteVector](ByteVector.empty)
    log <- Ref.of[IO, Vector[String]](Vector.empty)
    released <- Ref.of[IO, Boolean](false)
    peer <- RawPeer.create[IO](
      (n: Int) =>
        queue.modify(b =>
          (b.drop(n.toLong), if (b.isEmpty) None else Some(Chunk.byteVector(b.take(n.toLong))))
        ),
      (c: Chunk[Byte]) => {
        val b = c.toByteVector
        val rest = if (b.startsWith(clientPreface)) b.drop(clientPreface.size) else b
        IO.fromEither(decodeAll(rest))
          .flatMap(_.traverse_ { f =>
            if (f.frameType == Settings && !f.hasFlag(Ack) && !coalesced)
              queue.update(_ ++ encode(settings(ack = true)))
            else if (f.frameType == Data)
              delivered.update(
                _ ++ unpad(f)
              ) *> (if (f.hasFlag(EndStream))
                      delivered.get.flatMap { body =>
                        log.update(_ :+ s"request END_STREAM delivered: ${body.toHex}") *> queue
                          .update(
                            _ ++
                              (if (coalesced)
                                 wire(
                                   settings(if (initialZero) Vector(4 -> 0L) else Vector.empty),
                                   settings(ack = true),
                                 )
                               else ByteVector.empty) ++
                              (if (lateZero) encode(settings(Vector(4 -> 0L)))
                               else ByteVector.empty) ++
                              wire(headers(1, Vector(":status" -> "200"), false), data(1, body))
                          ) *> log.update(_ :+ "response queued only after complete request DATA")
                      }
                    else IO.unit)
            else IO.unit
          })
      },
      IO.unit,
      tr,
    )
    target = ScriptedServer[IO](
      "causal-mapping",
      Resource.pure[IO, ServerHandle[IO]](
        ServerHandle[IO](
          SocketAddress(ip"127.0.0.1", port"1"),
          delivered.get.map(b =>
            RequestData(
              method = if (coalesced) "POST" else "GET",
              path = if (coalesced) "/early" else "/mapping",
              body = b,
            )
          ),
          IO.pure(None),
        )
      ),
    )
    result <- Catalog.all
      .find(_.id == id)
      .get
      .run(CaseContext(target, tr, (_, _) => Resource.make(IO.pure(peer))(_ => released.set(true))))
      .attempt
      .timeout(10.seconds)
    body <- delivered.get
    rel <- released.get
    _ <- IO {
      assert(rel)
      if (beforeSendZero) {
        assert(result.swap.exists(_.isInstanceOf[WireObservationLimit]), result.toString)
        assert(body.isEmpty)
      } else {
        assertEquals(result, Right(()))
        assertEquals(
          body,
          if (coalesced) ByteVector(0, 13, 10, 127, 128, 255) else binary,
        )
      }
    }
  } yield ()
  def handoff(last: Int, samePhase: Boolean = false): IO[Unit] = for {
    tr <- Transcript.create[IO]()
    clientTrace <- Transcript.create[IO]()
    delivered <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "causal-negative-handoff"; val version = "1"
      def server(p: Protocol, s: ServerScenario) =
        Resource.eval(IO.raiseError[ServerHandle[IO]](new Exception("unused")))
      def client(p: Protocol, a: SocketAddress[IpAddress]) =
        WirePeerTransport.connect[IO](a, clientTrace).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(r: RequestData, b: FixtureCleanup[IO] => fs2.Stream[IO, Byte]) =
              Resource.eval(IO.raiseError[ResponseData[IO]](new Exception("unused")))
            def exchange(r: RequestData) = Resource.eval {
              implicit val codecSettings: Http2PeerSettings[IO] = Http2PeerSettings.untracked[IO]
              val fields = Vector(
                ":method" -> r.method,
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> r.path,
              )
              def response: IO[Frame] =
                readFrame(peer).flatMap(f => if (f.frameType == Headers) IO.pure(f) else response)
              for {
                _ <- peer.write(clientPreface ++ encode(Http2Wire.settings()))
                _ <- readFrame(peer)
                _ <- peer.write(wire(Http2Wire.settings(ack = true), go(0), headers(1, fields)))
                bad <- response
                decoder <- HpackWire.decoder[IO]()
                fields <- readHeaders(peer, bad, decoder)
                _ <- Check(
                  fields.exists(_._1 == "X-Upper"),
                  "uppercase negative fixture must actually arrive: " + fields,
                )
                _ <- delivered.set(true)
                _ <- peer.write(
                  (if (samePhase) encode(go(0)) else ByteVector.empty) ++ encode(
                    Frame(GoAway, 0, 0, uint32(last.toLong) ++ uint32(1))
                  )
                )
                _ <- peer.endOfOutput
                result <- IO.raiseError[ResponseData[IO]](
                  new IllegalStateException("native rejected uppercase")
                )
              } yield result
            }
          }
        }
    }
    result <- Catalog.all
      .find(_.id == "h2.client.reject-uppercase-field-name")
      .get
      .run(CaseContext(target, tr))
      .attempt
      .timeout(10.seconds)
    sent <- delivered.get
    _ <- IO {
      assert(sent, "malformed response must be independently read and decoded before rejection")
      if (last == 0) assertEquals(result, Right(()))
      else
        assert(
          result.swap
            .exists(e => e.isInstanceOf[ProtocolFailure] && e.getMessage.contains("increased")),
          result.toString,
        )
    }
  } yield ()
  def earlyResponse(zero: Boolean, bodyResponse: Boolean): IO[Unit] = for {
    tr <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    delivered <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "no-data-client"; val version = "1"
      def server(p: Protocol, s: ServerScenario) =
        Resource.eval(IO.raiseError[ServerHandle[IO]](new Exception("unused")))
      def client(p: Protocol, a: SocketAddress[IpAddress]) =
        WirePeerTransport.connect[IO](a, own).map { peer =>
          new ClientDriver[IO] {
            def exchange(r: RequestData) =
              Resource.eval(IO.raiseError[ResponseData[IO]](new Exception("unused")))
            def exchangeStream(r: RequestData, b: FixtureCleanup[IO] => fs2.Stream[IO, Byte]) =
              Resource.eval {
                implicit val codecSettings: Http2PeerSettings[IO] = Http2PeerSettings.untracked[IO]
                def head: IO[Frame] =
                  readFrame(peer).flatMap(f => if (f.frameType == Headers) IO.pure(f) else head)
                for {
                  _ <- peer.write(clientPreface ++ encode(Http2Wire.settings()))
                  _ <- readFrame(peer)
                  prefix <- b(new FixtureCleanup[IO](identity)).take(2).compile.to(Chunk)
                  _ <- peer.write(
                    wire(
                      Http2Wire.settings(ack = true)
                    ) ++ (if (zero) encode(Http2Wire.settings(Vector(4 -> 0L)))
                          else ByteVector.empty) ++ wire(
                      headers(
                        1,
                        Vector(
                          ":method" -> "POST",
                          ":scheme" -> "http",
                          ":authority" -> "wire.test",
                          ":path" -> "/",
                          "content-length" -> "6",
                        ),
                        false,
                      ),
                      data(1, prefix.toByteVector, false),
                    )
                  )
                  h <- head
                  decoder <- HpackWire.decoder[IO]()
                  fields <- readHeaders(peer, h, decoder)
                  body <-
                    if (h.hasFlag(EndStream)) IO.pure(ByteVector.empty)
                    else readFrame(peer).map(unpad)
                  _ <- delivered.set(true)
                } yield ResponseData[IO](
                  fields.collectFirst { case (":status", v) => v.toInt }.get,
                  Vector.empty,
                  fs2.Stream.chunk(Chunk.byteVector(body)),
                  IO.pure(Vector.empty),
                )
              }
          }
        }
    }
    result <- Catalog.all
      .find(
        _.id == (if (bodyResponse) "h2.client.interaction-complete-data-response-no-error-reset"
                 else "h2.client.interaction-complete-headers-response-no-error-reset")
      )
      .get
      .run(CaseContext(target, tr))
      .attempt
      .timeout(10.seconds)
    sent <- delivered.get
    _ <- IO {
      if (zero && bodyResponse) {
        assert(result.swap.exists(_.isInstanceOf[WireObservationLimit]), result.toString)
        assert(!sent, "future DATA prerequisite must fail before response delivery")
      } else {
        assertEquals(result, Right(()))
        assert(sent)
      }
    }
  } yield ()
  private def eagerWindow(window: Long): IO[Unit] = for {
    trace <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    delivered <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "eager-window-control"
      val version = "1"
      def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new IllegalStateException("unused")))
      def client(p: Protocol, address: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
        WirePeerTransport.connect[IO](address, own).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(
                r: RequestData,
                b: FixtureCleanup[IO] => fs2.Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.raiseError(new IllegalStateException("unused")))
            def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval {
              def nextHead: IO[Frame] = readFrame(peer).flatMap { frame =>
                if (frame.frameType == Headers) IO.pure(frame) else nextHead
              }
              for {
                _ <- readFrame(peer)
                _ <- peer.write(
                  clientPreface ++ wire(
                    Http2Wire.settings(),
                    Http2Wire.settings(ack = true),
                    Http2Wire.settings(Vector(4 -> window)),
                    headers(
                      1,
                      Vector(
                        ":method" -> r.method,
                        ":scheme" -> "http",
                        ":authority" -> "wire.test",
                        ":path" -> r.path,
                      ),
                    ),
                  )
                )
                head <- nextHead
                decoder <- HpackWire.decoder[IO]()
                fields <- readHeaders(peer, head, decoder)
                body <- readFrame(peer)
                _ <- Check.equal(body.frameType, Data)
                _ <- Check(body.hasFlag(EndStream), "response DATA must complete")
                _ <- Check.equal(unpad(body), ByteVector(0, 13, 10, 127, 128, 255))
                _ <- delivered.set(true)
              } yield ResponseData[IO](
                fields.collectFirst { case (":status", value) => value.toInt }.get,
                Vector.empty,
                fs2.Stream.chunk(Chunk.byteVector(unpad(body))),
                IO.pure(Vector.empty),
              )
            }
          }
        }
    }
    result <- Catalog.all
      .find(_.id == "h2.client.interaction-eager-server-settings")
      .get
      .run(CaseContext(target, trace))
      .attempt
    sent <- delivered.get
    _ <- IO {
      if (window >= 6L) {
        assertEquals(result, Right(()))
        assert(sent)
      } else {
        assert(result.swap.exists(_.isInstanceOf[WireObservationLimit]), result.toString)
        assert(!sent)
      }
    }
  } yield ()

  for (window <- Vector(5L, 6L, 9L))
    test(s"eager response requires its six pending DATA bytes window=$window") {
      eagerWindow(window)
    }

  for {
    last <- Vector(0, 2)
    samePhase <- Vector(false, true)
  }
    test(s"actual malformed response handoff last=$last samePhase=$samePhase") {
      handoff(last, samePhase)
    }

  for ((late, initial) <- Vector((false, false), (false, true), (true, false)))
    test(s"coalesced request SETTINGS after delivered DATA late=$late initial=$initial") {
      server("h2.server.interaction-coalesced-preface-request", late, initial, coalesced = true)
    }

  for (late <- Vector(false, true))
    test(s"Message SETTINGS after delivered DATA late=$late") {
      server("h2.server.message-binary-data", late, false)
    }

  test("Message constrained before future DATA retains prerequisite") {
    server("h2.server.message-binary-data", false, false, beforeSendZero = true)
  }

  for {
    zero <- Vector(false, true)
    body <- Vector(false, true)
  }
    test(s"early response pending DATA body=$body windowZero=$zero") {
      earlyResponse(zero, body)
    }
  for (
    (scenario, before, after) <- Vector(
      (Http2ControlHandoffFixture.settingsCase, Some(0L), Vector(0L)),
      (Http2ControlHandoffFixture.settingsCase, Some(0L), Vector(1L)),
      (Http2ControlHandoffFixture.settingsCase, None, Vector(0L, 1L)),
      (Http2ControlHandoffFixture.halfClosed, Some(1L), Vector(1L)),
      (Http2ControlHandoffFixture.halfClosed, Some(1L), Vector(3L)),
      (Http2ControlHandoffFixture.halfClosed, Some(1L), Vector(0L)),
      (Http2ControlHandoffFixture.halfClosed, None, Vector(0L)),
      (Http2ControlHandoffFixture.halfClosed, None, Vector(1L, 3L)),
      (Http2ControlHandoffFixture.dispatched, Some(7L), Vector(7L)),
      (Http2ControlHandoffFixture.dispatched, Some(7L), Vector(9L)),
      (Http2ControlHandoffFixture.dispatched, None, Vector(0L)),
    )
  ) test(s"history handoff $scenario before=$before after=$after") {
    Http2ControlHandoffFixture.probe(scenario, before, after)
  }

  for {
    (id, kind, stream, value) <- Vector(
      ("h2.client.idle-data", Http2Wire.Data, 2, None),
      ("h2.client.idle-window-update", Http2Wire.WindowUpdate, 2, None),
      ("h2.client.unpromised-server-headers", Http2Wire.Headers, 2, None),
      ("h2.client.zero-connection-window-update", Http2Wire.WindowUpdate, 0, Some(0L)),
      ("h2.client.connection-window-overflow", Http2Wire.WindowUpdate, 0, Some(1L)),
    )
    before <- Vector(false, true)
  }
    test(s"request HEADERS GOAWAY placement $id before=$before") {
      Http2StateRequestHeadFixture.probe(
        id,
        before,
        frame =>
          frame.frameType == kind && frame.streamId == stream &&
            value.forall(_ == Http2Wire.readUint32(frame.payload)),
      )
    }

  for {
    (id, errorCode) <- Vector(
      "h2.server.zero-stream-window-update" -> 1L,
      "h2.server.stream-window-overflow" -> 3L,
    )
    late <- Vector(false, true)
  }
    test(s"State SETTINGS after complete fault DATA $id late=$late") {
      Http2StateDataBudgetFixture.probe(id, late, errorCode)
    }

  for {
    id <- Vector("h2.client.goaway-debug-data", "h2.client.goaway-unknown-error")
    late <- Vector(false, true)
  }
    test(s"Control SETTINGS after complete response DATA $id late=$late") {
      Http2ControlDataBudgetFixture.probe(id, late)
    }

  for (before <- Vector(false, true))
    test(s"Smoke full response with GOAWAY before request HEADERS=$before") {
      Http2BoundaryHandoffFixture.smokeClient(before)
    }

  for ((hpack, last) <- Vector((false, 0L), (false, 1L), (true, 1L), (true, 0L)))
    test(s"Smoke/HPACK actual delivered fault handoff hpack=$hpack last=$last") {
      Http2BoundaryHandoffFixture.serverProbe(hpack, last)
    }

  for (increased <- Vector(false, true))
    test(s"HPACK client delivered index-zero fault handoff increase=$increased") {
      Http2HpackClientHandoffFixture.runOne("h2.client.hpack-reject-index-zero", increased)
    }

  for {
    reduce <- Vector(false, true)
    before <- Vector(false, true)
  }
    test(
      s"Smoke malformed response delivered before window reduction=$reduce beforeHEADERSgoaway=$before"
    ) {
      Http2SmokeDataBudgetFixture.one(reduce, before)
    }

}

private object Http2ControlHandoffFixture {
  import Http2Wire._
  def goaway(last: Long, code: Long) = Frame(GoAway, 0, 0, uint32(last) ++ uint32(code))
  final case class Scenario(wireCase: WireCase, stateCase: Boolean, dispatched: Boolean) {
    override def toString: String = wireCase.id
  }
  private def scenario(id: String, state: Boolean, dispatch: Boolean): Scenario =
    Scenario(Catalog.all.find(_.id == id).get, state, dispatch)
  val settingsCase = scenario("h2.server.reject-settings-stream-id", false, false)
  val halfClosed = scenario("h2.server.half-closed-remote-data", true, false)
  val dispatched = scenario("h2.server.goaway-includes-dispatched-stream", true, true)

  def probe(scenario: Scenario, before: Option[Long], after: Vector[Long]): IO[Unit] = {
    val dispatched = scenario.dispatched
    val stateCase = scenario.stateCase
    val requestStream = if (dispatched) 7 else 1
    val initial = encode(settings())
    val readyFrames =
      if (stateCase)
        Vector(settings(ack = true)) ++ before.toVector.map(goaway(_, 0)) ++
          Vector(headers(requestStream, Vector(":status" -> "200"), endStream = false))
      else before.toVector.map(goaway(_, 0)) ++ Vector(settings(ack = true))
    val warmupBytes =
      if (stateCase) readyFrames.drop(1).foldLeft(ByteVector.empty)(_ ++ encode(_))
      else ByteVector.empty
    val prefixFrames = if (stateCase) readyFrames.take(1) else readyFrames
    val prefix = initial ++ prefixFrames.foldLeft(ByteVector.empty)(_ ++ encode(_))
    val tail = after.map(goaway(_, 1)).foldLeft(ByteVector.empty)(_ ++ encode(_))
    for {
      trace <- Transcript.create[IO]()
      prefixLeft <- Ref.of[IO, ByteVector](prefix)
      tailLeft <- Ref.of[IO, ByteVector](tail)
      warmupLeft <- Ref.of[IO, ByteVector](warmupBytes)
      warmup <- Deferred[IO, Unit]
      fault <- Deferred[IO, Unit]
      writes <- Ref.of[IO, Vector[Frame]](Vector.empty)
      released <- Ref.of[IO, Boolean](false)
      peer <- RawPeer.create[IO](
        (n: Int) =>
          prefixLeft
            .modify { bytes =>
              if (bytes.nonEmpty) (bytes.drop(n.toLong), Some(bytes.take(n.toLong)))
              else (bytes, None)
            }
            .flatMap {
              case Some(bytes) => IO.pure(Some(Chunk.byteVector(bytes)))
              case None =>
                (if (stateCase) warmup.get *> warmupLeft.modify { bytes =>
                   (
                     bytes.drop(n.toLong),
                     if (bytes.isEmpty) None else Some(Chunk.byteVector(bytes.take(n.toLong))),
                   )
                 }
                 else IO.pure(Option.empty[Chunk[Byte]])).flatMap {
                  case some @ Some(_) => IO.pure(some)
                  case None =>
                    fault.get *> tailLeft.modify { bytes =>
                      (
                        bytes.drop(n.toLong),
                        if (bytes.isEmpty) None else Some(Chunk.byteVector(bytes.take(n.toLong))),
                      )
                    }
                }
            },
        (chunk: Chunk[Byte]) => {
          val raw = chunk.toByteVector
          val bytes = if (raw.startsWith(clientPreface)) raw.drop(clientPreface.size) else raw
          IO.fromEither(decodeAll(bytes)).flatMap { frames =>
            writes.update(
              _ ++ frames
            ) *> (if (frames.exists(f => f.frameType == Headers && f.streamId == requestStream))
                    warmup.complete(()).void
                  else IO.unit) *> (if (
                                      frames.exists(f =>
                                        if (stateCase)
                                          f.frameType == Data && f.streamId == (if (dispatched) 9
                                                                                else 1)
                                        else f.frameType == Settings && f.streamId == 1
                                      )
                                    ) fault.complete(()).void
                                    else IO.unit)
          }
        },
        IO.unit,
        trace,
      )
      target = ScriptedServer[IO](
        "handoff-probe",
        Resource.pure(
          ServerHandle[IO](
            SocketAddress(ip"127.0.0.1", port"1"),
            IO.pure(RequestData()),
            IO.pure(Option.empty[RequestData]),
          )
        ),
      )
      selected = scenario.wireCase
      result <- selected
        .run(
          CaseContext(
            target,
            trace,
            (_, _) => Resource.make(IO.pure(peer))(_ => released.set(true)),
          )
        )
        .timeout(5.seconds)
        .attempt
      seen <- fault.tryGet
      opened <- warmup.tryGet
      done <- released.get
      _ <- IO {
        assert(seen.nonEmpty, "exact malformed frame must arrive before rejection")
        assert(!stateCase || opened.nonEmpty, "response warmup must follow delivered request")
        assert(done)
        val invalidIncrease =
          (before.toVector ++ after).sliding(2).exists(pair => pair.size == 2 && pair(1) > pair(0))
        val invalidCutoff = stateCase && after.exists(_ < requestStream.toLong)
        if (invalidIncrease || invalidCutoff)
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
        else assert(result == Right(()), result.toString)
      }
    } yield ()
  }
}

private object Http2StateRequestHeadFixture {
  import Http2Wire._
  def goaway(code: Long) = Frame(GoAway, 0, 0, uint32(0) ++ uint32(code))
  def probe(id: String, before: Boolean, expectedFault: Frame => Boolean): IO[Unit] = for {
    trace <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    fault <- Ref.of[IO, Boolean](false)
    closed <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "request-head-probe"; val version = "1"
      def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new Exception("unused")))
      def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
        WirePeerTransport.connect[IO](a, own).onFinalize(closed.set(true)).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(
                r: RequestData,
                b: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.raiseError(new Exception("unused")))
            def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval(IO.defer {
              implicit val ps: Http2PeerSettings[IO] = Http2PeerSettings.untracked[IO]
              val req = headers(
                1,
                Vector(
                  ":method" -> "POST",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> "/",
                  "content-length" -> "0",
                ),
              )
              def awaitFault: IO[Unit] = readFrame(peer).flatMap { f =>
                val hit = expectedFault(f)
                if (hit) fault.set(true) else awaitFault
              }
              for {
                _ <- peer.write(clientPreface ++ encode(Http2Wire.settings()))
                _ <- readFrame(peer)
                _ <- peer.write(
                  encode(Http2Wire.settings(ack = true)) ++ (if (before)
                                                               encode(goaway(0)) ++ encode(req)
                                                             else encode(req) ++ encode(goaway(0)))
                )
                _ <- awaitFault
                _ <- peer.write(encode(goaway(1)))
                _ <- peer.endOfOutput
                r <- IO.raiseError[ResponseData[IO]](
                  new java.io.IOException("target rejected delivered malformed stimulus")
                )
              } yield r
            })
          }
        }
    }
    c = Http2StateCases.all.find(_.id == id).get
    r <- c.run(CaseContext(target, trace)).timeout(5.seconds).attempt
    f <- fault.get
    done <- closed.get
    _ <- IO {
      assert(f, "intended fault must arrive"); assert(done); assert(r == Right(()), r.toString)
    }
  } yield ()
}

private object Http2StateDataBudgetFixture {
  import Http2Wire._
  def probe(id: String, lateReduction: Boolean, errorCode: Long): IO[Unit] = for {
    tr <- Transcript.create[IO]()
    in <- Ref.of[IO, ByteVector](encode(settings()))
    sent <- Ref.of[IO, Vector[Frame]](Vector.empty)
    delivered <- Ref.of[IO, Long](0L)
    released <- Ref.of[IO, Boolean](false)
    peer <- RawPeer.create[IO](
      (n: Int) =>
        in.modify(b =>
          (b.drop(n.toLong), if (b.isEmpty) None else Some(Chunk.byteVector(b.take(n.toLong))))
        ),
      (c: Chunk[Byte]) => {
        val raw = c.toByteVector;
        val b = if (raw.startsWith(clientPreface)) raw.drop(clientPreface.size) else raw
        IO.fromEither(decodeAll(b))
          .flatMap(_.traverse_ { f =>
            sent.update(_ :+ f) *> (f.frameType match {
              case Settings if !f.hasFlag(Ack) => in.update(_ ++ encode(settings(ack = true)))
              case Ping if !f.hasFlag(Ack) => in.update(_ ++ encode(f.copy(flags = Ack)))
              case Data =>
                delivered.update(_ + f.payload.size) *> in.update(
                  _ ++
                    (if (lateReduction) encode(settings(Vector(4 -> 0L))) else ByteVector.empty) ++
                    encode(Frame(RstStream, 0, 1, uint32(errorCode)))
                )
              case _ => IO.unit
            })
          })
      },
      IO.unit,
      tr,
    )
    target = ScriptedServer[IO](
      "data-budget-probe",
      Resource.pure(
        ServerHandle[IO](
          SocketAddress(ip"127.0.0.1", port"1"),
          IO.never[RequestData],
          IO.pure(None),
        )
      ),
    )
    c = Http2StateCases.all.find(_.id == id).get
    r <- c
      .run(CaseContext(target, tr, (_, _) => Resource.make(IO.pure(peer))(_ => released.set(true))))
      .timeout(5.seconds)
      .attempt
    d <- delivered.get
    done <- released.get
    _ <- IO { assert(d == 1L); assert(done); assert(r == Right(()), r.toString) }
  } yield ()
}

private object Http2ControlDataBudgetFixture {
  import Http2Wire._
  def probe(id: String, late: Boolean): IO[Unit] = for {
    tr <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    delivered <- Ref.of[IO, Long](0L)
    released <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "control-budget-probe"; val version = "1"
      def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new Exception("unused")))
      def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
        WirePeerTransport.connect[IO](a, own).onFinalize(released.set(true)).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(
                r: RequestData,
                b: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.raiseError(new Exception("unused")))
            def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval(IO.defer {
              implicit val ps: Http2PeerSettings[IO] = Http2PeerSettings.untracked[IO]
              def loop(body: ByteVector): IO[ByteVector] = readFrame(peer).flatMap { f =>
                f.frameType match {
                  case Settings if !f.hasFlag(Ack) =>
                    peer.write(encode(Http2Wire.settings(ack = true))) *> loop(body)
                  case Data =>
                    delivered.update(
                      _ + f.payload.size
                    ) *> (if (late && f.hasFlag(EndStream))
                            peer.write(encode(Http2Wire.settings(Vector(4 -> 0L))))
                          else IO.unit) *> loop(body ++ unpad(f))
                  case Ping if !f.hasFlag(Ack) => peer.write(encode(f.copy(flags = Ack))).as(body)
                  case _ => loop(body)
                }
              }
              for {
                _ <- peer.write(clientPreface ++ encode(Http2Wire.settings()))
                _ <- readFrame(peer)
                _ <- peer.write(
                  encode(Http2Wire.settings(ack = true)) ++ encode(
                    headers(
                      1,
                      Vector(
                        ":method" -> r.method,
                        ":scheme" -> "http",
                        ":authority" -> "wire.test",
                        ":path" -> "/",
                        "content-length" -> "0",
                      ),
                    )
                  )
                )
                b <- loop(ByteVector.empty)
              } yield ResponseData[IO](
                200,
                Vector.empty,
                Stream.chunk(Chunk.byteVector(b)),
                IO.pure(Vector.empty),
              )
            })
          }
        }
    }
    c = Http2ControlCases.all.find(_.id == id).get
    r <- c.run(CaseContext(target, tr)).timeout(5.seconds).attempt
    d <- delivered.get
    rel <- released.get
    _ <- IO { assert(d == 7L); assert(rel); assert(r == Right(()), r.toString) }
  } yield ()
}

private object Http2BoundaryHandoffFixture {
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  import Http2Wire._
  def goaway(last: Long, code: Long) = Frame(GoAway, 0, 0, uint32(last) ++ uint32(code))
  def smokeClient(sendGoaway: Boolean): IO[Unit] = for {
    trace <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    complete <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "complete-RFC-client"; val version = "1"
      def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new Exception("unused")))
      def client(p: Protocol, address: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
        WirePeerTransport.connect[IO](address, own).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(
                r: RequestData,
                b: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.raiseError(new Exception("unused")))
            def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval {
              val request = headers(
                1,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":path" -> "/",
                  ":authority" -> "wire.test",
                  "content-length" -> "0",
                ),
              )
              def consume(status: Int, body: ByteVector): IO[(Int, ByteVector)] =
                readFrame[IO](peer).flatMap { f =>
                  f.frameType match {
                    case Settings =>
                      (if (f.hasFlag(Ack)) IO.unit
                       else peer.write(encode(Http2Wire.settings(ack = true)))) *> consume(
                        status,
                        body,
                      )
                    case Headers =>
                      HpackWire.decoder[IO]().flatMap(_.decode(f.payload)).flatMap { hs =>
                        val s = hs.collectFirst { case (":status", v) => v.toInt }.get
                        if (f.hasFlag(EndStream)) IO.pure((s, body)) else consume(s, body)
                      }
                    case Data =>
                      val b = body ++ unpad(f);
                      if (f.hasFlag(EndStream)) IO.pure((status, b)) else consume(status, b)
                    case _ => consume(status, body)
                  }
                }
              for {
                _ <- peer.write(clientPreface ++ encode(Http2Wire.settings()))
                initial <- readFrame[IO](peer)
                _ <- IO(assert(initial.frameType == Settings && !initial.hasFlag(Ack)))
                _ <- peer.write(
                  encode(Http2Wire.settings(ack = true)) ++ (if (sendGoaway) encode(goaway(0, 0))
                                                             else
                                                               ByteVector.empty) ++ encode(request)
                )
                result <- consume(0, ByteVector.empty)
                _ <- IO(assert(result == ((200, Http1Wire.ascii("abc")))))
                _ <- complete.set(true)
              } yield ResponseData[IO](
                result._1,
                Vector.empty,
                Stream.chunk(Chunk.byteVector(result._2)),
                IO.pure(Vector.empty),
              )
            }
          }
        }
    }
    result <- SmokeCases.all
      .find(_.id == "h2.client.headers-and-data")
      .get
      .run(CaseContext(target, trace))
      .attempt
      .timeout(8.seconds)
    done <- complete.get
    _ <- IO {
      assert(done, "complete response must arrive"); assert(result == Right(()), result.toString)
    }
  } yield ()
  def serverProbe(hpack: Boolean, later: Long): IO[Unit] = for {
    trace <- Transcript.create[IO]()
    queue <- Ref.of[IO, ByteVector](encode(Http2Wire.settings()))
    requests <- Ref.of[IO, Vector[RequestData]](Vector.empty)
    faultDelivered <- Ref.of[IO, Boolean](false)
    peer <- RawPeer.create[IO](
      (n: Int) =>
        queue.modify(b =>
          (b.drop(n.toLong), if (b.isEmpty) None else Some(Chunk.byteVector(b.take(n.toLong))))
        ),
      (c: Chunk[Byte]) => {
        val in = c.toByteVector;
        val bytes = if (in.startsWith(clientPreface)) in.drop(clientPreface.size) else in
        IO.fromEither(decodeAll(bytes))
          .flatMap(_.traverse_ { f =>
            val reply: IO[ByteVector] = f.frameType match {
              case Settings if !f.hasFlag(Ack) => IO.pure(encode(Http2Wire.settings(ack = true)))
              case Ping if !hpack && f.payload.size == 8 =>
                IO.pure(encode(goaway(0, 0)) ++ encode(f.copy(flags = Ack)))
              case Ping if !hpack && f.payload.size == 7 =>
                faultDelivered.set(true).as(encode(goaway(later, 6)))
              case Headers if hpack && f.streamId == 1 =>
                IO(assert(f.payload.toHex == "3f038286840109776972652e746573744001780161")) *>
                  requests
                    .update(_ :+ RequestData(headers = Vector("x" -> "a")))
                    .as(encode(headers(1, Vector(":status" -> "200"))))
              case Headers if hpack && f.streamId == 3 =>
                IO(assert(f.payload.toHex == "8286840109776972652e74657374bf")) *> faultDelivered
                  .set(true)
                  .as(encode(goaway(later, 9)))
              case _ => IO.pure(ByteVector.empty)
            }
            reply.flatMap(b => queue.update(_ ++ b))
          })
      },
      IO.unit,
      trace,
    )
    target = ScriptedServer[IO](
      "causal-server-history",
      Resource.pure(
        ServerHandle(
          SocketAddress(ip"127.0.0.1", port"1"),
          requests.modify(v => (v.drop(1), v.head)),
          requests.get.map(_.headOption),
        )
      ),
    )
    id =
      if (hpack) "h2.server.hpack-reject-index-beyond-populated" else "h2.server.reject-ping-length"
    selected = (HpackCases.all ++ SmokeCases.all).find(_.id == id).get
    result <- selected
      .run(CaseContext(target, trace, (_, _) => Resource.pure(peer)))
      .attempt
      .timeout(8.seconds)
    delivered <- faultDelivered.get
    _ <- IO {
      assert(delivered, "actual malformed HPACK or PING must arrive")
      val invalid = if (hpack) later == 0L else later > 0L
      if (invalid) assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
      else assert(result == Right(()), result.toString)
    }
  } yield ()
}

private object Http2HpackClientHandoffFixture {
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  import Http2Wire._
  def goaway(last: Long, code: Long) = encode(Frame(GoAway, 0, 0, uint32(last) ++ uint32(code)))
  def runOne(id: String, increased: Boolean): IO[Unit] = for {
    trace <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    delivered <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "RFC-first-handoff-probe"; val version = "1"
      def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new Exception("unused")))
      def client(p: Protocol, address: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
        WirePeerTransport.connect[IO](address, own).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(
                r: RequestData,
                b: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.raiseError(new Exception("unused")))
            def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval {
              val request = headers(
                1,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":path" -> "/",
                  ":authority" -> "wire.test",
                  "content-length" -> "0",
                ),
              )
              def readResponse: IO[ByteVector] = readFrame[IO](peer).flatMap { f =>
                f.frameType match {
                  case Settings =>
                    (if (f.hasFlag(Ack)) IO.unit
                     else peer.write(encode(Http2Wire.settings(ack = true)))) *> readResponse
                  case Headers => IO.pure(f.payload)
                  case _ => readResponse
                }
              }
              for {
                _ <- peer.write(
                  clientPreface ++ encode(Http2Wire.settings()) ++ goaway(0, 0) ++ encode(request)
                )
                block <- readResponse
                _ <- delivered.set(true)
                _ <-
                  if (id.contains("reject-index-zero")) {
                    IO(assert(block == ByteVector.fromValidHex("208880"))) *> peer.write(
                      goaway(if (increased) 2 else 0, 9)
                    ) *> peer.endOfOutput *> IO.raiseError[Unit](
                      new java.io.IOException(
                        "native compression rejection after actual malformed block"
                      )
                    )
                  } else IO.unit
              } yield ResponseData[IO](
                200,
                Vector.empty,
                Stream.chunk(Chunk.byteVector(Http1Wire.ascii("abc"))),
                IO.pure(Vector.empty),
              )
            }
          }
        }
    }
    selected = (HpackCases.all ++ SmokeCases.all).find(_.id == id).get
    result <- selected.run(CaseContext(target, trace)).attempt.timeout(8.seconds)
    got <- delivered.get
    _ <- IO {
      assert(got, "actual malformed HPACK must arrive")
      if (increased) assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
      else assert(result == Right(()), result.toString)
    }
  } yield ()
}

private object Http2SmokeDataBudgetFixture {
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  import Http2Wire._
  def one(reduce: Boolean, beforeGoaway: Boolean): IO[Unit] = for {
    trace <- Transcript.create[IO]()
    own <- Transcript.create[IO]()
    delivered <- Ref.of[IO, Boolean](false)
    target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "smoke-post-delivery-window"; val version = "1"
      def server(p: Protocol, s: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new Exception("unused")))
      def client(p: Protocol, a: SocketAddress[IpAddress]): Resource[IO, ClientDriver[IO]] =
        WirePeerTransport.connect[IO](a, own).map { peer =>
          new ClientDriver[IO] {
            def exchangeStream(
                r: RequestData,
                b: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.raiseError(new Exception("unused")))
            def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval {
              def loop(declared: Option[String], body: ByteVector): IO[Unit] =
                readFrame[IO](peer).flatMap { f =>
                  f.frameType match {
                    case Settings =>
                      (if (f.hasFlag(Ack)) IO.unit
                       else
                         peer.write(encode(Http2Wire.settings(ack = true)))) *> loop(declared, body)
                    case Headers =>
                      HpackWire
                        .decoder[IO]()
                        .flatMap(_.decode(f.payload))
                        .flatMap(h =>
                          loop(h.collectFirst { case ("content-length", s) => s }, body)
                        )
                    case Data =>
                      val b = body ++ unpad(f);
                      if (f.hasFlag(EndStream)) {
                        IO(
                          assert(declared.contains("4") && b == Http1Wire.ascii("abc"))
                        ) *> delivered.set(true) *>
                          peer.write(
                            encode(
                              Http2Wire.settings(Vector(4 -> (if (reduce) 0L else 3L)))
                            ) ++ encode(Frame(RstStream, 0, 1, uint32(1)))
                          ) *>
                          IO.raiseError[Unit](
                            new java.io.IOException("native content-length mismatch")
                          )
                      } else loop(declared, b)
                    case _ => loop(declared, body)
                  }
                }
              peer.write(
                clientPreface ++ encode(
                  Http2Wire.settings()
                ) ++ (if (beforeGoaway) encode(Frame(GoAway, 0, 0, uint32(0) ++ uint32(0)))
                      else ByteVector.empty) ++ encode(
                  headers(
                    1,
                    Vector(
                      ":method" -> "GET",
                      ":scheme" -> "http",
                      ":path" -> "/",
                      ":authority" -> "wire.test",
                      "content-length" -> "0",
                    ),
                  )
                )
              ) *> loop(None, ByteVector.empty) *> IO.raiseError[ResponseData[IO]](
                new Exception("unreachable")
              )
            }
          }
        }
    }
    result <- SmokeCases.all
      .find(_.id == "h2.client.reject-content-length-mismatch")
      .get
      .run(CaseContext(target, trace))
      .attempt
      .timeout(8.seconds)
    got <- delivered.get
    _ <- IO {
      assert(got, "complete malformed response must arrive");
      assert(result == Right(()), result.toString)
    }
  } yield ()
}
