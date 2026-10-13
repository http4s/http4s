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
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class Http2PositiveObservationSuite extends CatsEffectSuite {
  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  import Http2Wire._

  private def goaway(last: Long, code: Long = 0L): Frame =
    Frame(GoAway, 0, 0, uint32(last) ++ uint32(code))

  test("positive observation preserves role-specific stream ownership") {
    val client = Http2PositiveObservation
      .State()
      .observe(goaway(0), fromServer = false)
      .flatMap(_.observe(headers(1, Vector(":method" -> "POST")), fromServer = false))
    assert(client.isRight)
    val server = Http2PositiveObservation
      .State()
      .observe(goaway(1), fromServer = true)
      .flatMap(_.observe(headers(1, Vector(":status" -> "200")), fromServer = true))
    assert(server.isRight)
  }

  for (
    (name, frames, fromServer) <- Vector(
      ("bad envelope", Vector(goaway(1).copy(streamId = 1)), true),
      ("short payload", Vector(goaway(1).copy(payload = ByteVector(0))), true),
      ("wrong server role", Vector(goaway(2)), true),
      ("wrong client role", Vector(goaway(1)), false),
      ("error code", Vector(goaway(1, 1)), true),
      ("increasing last ID", Vector(goaway(1), goaway(3)), true),
      (
        "response after excluded ID",
        Vector(goaway(0), headers(1, Vector(":status" -> "200"))),
        true,
      ),
      ("excluded prior response", Vector(headers(3, Vector(":status" -> "200")), goaway(1)), true),
    )
  )
    test(s"positive GOAWAY rejects $name") {
      assert(
        frames
          .foldLeft[Either[Throwable, Http2PositiveObservation.State]](
            Right(Http2PositiveObservation.State())
          )((state, frame) => state.flatMap(_.observe(frame, fromServer)))
          .isLeft
      )
    }

  test("GOAWAY state persists and prevents only later request admission") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      _ <- positive.openingStream(1)
      _ <- positive.observe(goaway(3), fromServer = true)
      _ <- positive.observe(goaway(1), fromServer = true)
      later <- positive.openingStream(3).attempt
      _ <- IO(assert(later.swap.exists(_.isInstanceOf[WireObservationLimit])))
      _ <- positive.observe(data(1, ByteVector.empty), fromServer = true)
    } yield ()
  }

  test("rejection observes positive GOAWAY history without applying positive verdict policy") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      _ <- positive.observe(goaway(0), fromServer = false)
      _ <- positive.observeControl(goaway(0, 1), fromServer = false)
      increased <- positive.observeControl(goaway(2, 1), fromServer = false).attempt
      _ <- IO(assert(increased.swap.exists(_.getMessage.contains("increased"))))
    } yield ()
  }

  test("rejection retains observed response cutoff across the policy handoff") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      _ <- positive.expecting(Set(1))
      _ <- positive.observe(headers(1, Vector(":status" -> "200")), fromServer = true)
      _ <- positive.completed(Set(1))
      excluded <- positive.observeControl(goaway(0, 1), fromServer = true).attempt
      _ <- IO(assert(excluded.swap.exists(_.getMessage.contains("observed response"))))
    } yield ()
  }

  test("rejection does not confuse required response with an observed response") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      _ <- positive.expecting(Set(1))
      _ <- positive.observeControl(goaway(0, 1), fromServer = true)
    } yield ()
  }

  test("positive observation retains neutral control history and its stricter error policy") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      _ <- positive.observeControl(goaway(1), fromServer = true)
      increased <- positive.observe(goaway(3), fromServer = true).attempt
      error <- positive.observe(goaway(1, 1), fromServer = true).attempt
      _ <- IO {
        assert(increased.swap.exists(_.getMessage.contains("increased")))
        assert(error.swap.exists(_.getMessage.contains("error GOAWAY")))
      }
    } yield ()
  }

  test("neutral history rejects response after cutoff and retains state after invalid updates") {
    for {
      observation <- Http2PositiveObservation.create[IO]
      _ <- observation.observeControl(goaway(1, 1), fromServer = true)
      increased <- observation.observeControl(goaway(5, 1), fromServer = true).attempt
      stillIncreased <- observation.observeControl(goaway(3, 1), fromServer = true).attempt
      excluded <- observation
        .observeControl(headers(3, Vector(":status" -> "200")), fromServer = true)
        .attempt
      _ <- observation.observeControl(headers(1, Vector(":status" -> "200")), fromServer = true)
      _ <- observation.observeControl(goaway(1, 1), fromServer = true)
      _ <- IO {
        assert(increased.swap.exists(_.getMessage.contains("increased")))
        assert(stillIncreased.swap.exists(_.getMessage.contains("increased")))
        assert(excluded.swap.exists(_.getMessage.contains("observed response")))
      }
    } yield ()
  }

  test("server GOAWAY cutoff counts client requests rather than server push streams") {
    for {
      observation <- Http2PositiveObservation.create[IO]
      _ <- observation.observeControl(headers(2, Vector(":status" -> "200")), fromServer = true)
      _ <- observation.observeControl(goaway(0), fromServer = true)
      _ <- observation.observeControl(data(2, ByteVector.empty), fromServer = true)
      request <- observation
        .observeControl(headers(1, Vector(":status" -> "200")), fromServer = true)
        .attempt
      _ <- IO(assert(request.swap.exists(_.getMessage.contains("observed response"))))
    } yield ()
  }

  test("closed incidental write is attempted once; skipped grants never gain credit") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      error <- Http2WriteFailureFixture.closedWrite
      attempts <- Ref.of[IO, Int](0)
      first <- positive.incidental(attempts.update(_ + 1) *> IO.raiseError[Unit](error))
      second <- positive.incidental(attempts.update(_ + 1))
      count <- attempts.get
      _ <- IO {
        assertEquals(first, Http2PositiveObservation.Write(attempted = true, writable = false))
        assertEquals(second, Http2PositiveObservation.Write(attempted = false, writable = false))
        assertEquals(count, 1)
      }
    } yield ()
  }

  test("canceling an incidental write cancels the caller without inventing a closed verdict") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      entered <- Deferred[IO, Unit]
      fiber <- positive.incidental(entered.complete(()).void *> IO.never).start
      _ <- entered.get
      _ <- fiber.cancel
      outcome <- fiber.join
      next <- positive.incidental(IO.unit)
      _ <- IO {
        assert(outcome.isCanceled)
        assertEquals(next, Http2PositiveObservation.Write(attempted = true, writable = true))
      }
    } yield ()
  }

  // Faults are injected at the actual registered cases' raw observer boundaries, not at
  // validExchange, whose target-side failure policy is intentionally different.
  private val boundaries = Vector(
    ("settingsAck", "peer-concurrency-zero-allows-responses", "setup"),
    ("responseHead", "peer-concurrency-zero-allows-responses", "head"),
    ("firstPositiveData", "negative-stream-window", "after-head"),
    ("readData", "peer-concurrency-zero-allows-responses", "after-head"),
    ("barrier", "stream-credit-boundary", "after-head"),
    ("resetCompletion", "reset-closes-blocked-stream", "after-head"),
  )

  private def rawFault(
      name: String,
      boundary: String,
      writeFault: Boolean,
      error: Throwable,
      hold: Option[(Deferred[IO, Unit], Deferred[IO, Unit])] = None,
  ): IO[Either[Throwable, Unit]] = {
    val fail: IO[Nothing] = hold.fold(IO.raiseError[Nothing](error)) { case (blocked, _) =>
      blocked.complete(()).void *> IO.never[Nothing]
    }
    val initial = encode(settings())
    val ack = encode(settings(ack = true))
    val head = encode(headers(1, Vector(":status" -> "200"), endStream = false))
    val prefix = boundary match {
      case "setup" => initial
      case "head" => initial ++ ack
      case _ => initial ++ ack ++ head
    }
    val wire = prefix ++ encode(settings())
    for {
      trace <- Transcript.create[IO]()
      unread <- Ref.of[IO, ByteVector](wire)
      consumed <- Ref.of[IO, Long](0)
      released <- Ref.of[IO, Boolean](false)
      peer <- RawPeer.create[IO](
        (n: Int) =>
          consumed.get.flatMap { count =>
            if (!writeFault && count >= prefix.size) fail
            else
              unread.modify(b => (b.drop(n.toLong), b.take(n.toLong))).flatMap { bytes =>
                consumed
                  .update(_ + bytes.size)
                  .as(
                    if (bytes.isEmpty) None else Some(Chunk.byteVector(bytes))
                  )
              }
          },
        (_: Chunk[Byte]) =>
          consumed.get.flatMap { count =>
            if (writeFault && count > prefix.size) fail else IO.unit
          },
        IO.unit,
        trace,
      )
      target = ScriptedServer[IO](
        "raw-observer-provenance",
        Resource.pure(
          ServerHandle[IO](SocketAddress(ip"127.0.0.1", port"1"), IO.never, IO.pure(None))
        ),
      )
      selected = Http2StateCases.all.find(_.id == "h2.server." + name).get
      result <- selected
        .run(
          CaseContext(
            target,
            trace,
            (_, _) =>
              Resource.make(IO.pure(peer))(_ =>
                released.set(true) *> hold.traverse_(_._2.complete(()).void)
              ),
          )
        )
        .attempt
      closed <- released.get
      _ <- IO(assert(closed))
    } yield result
  }

  for {
    (observer, name, boundary) <- boundaries
    writeFault <- Vector(false, true)
    kind <- Vector("io", "wrapper", "timeout", "adapter")
  } test(s"raw provenance $observer: write=$writeFault $kind") {
    val cause = new java.io.IOException("fixture cause")
    val error = kind match {
      case "io" => cause
      case "wrapper" => new RuntimeException("fixture wrapper", cause)
      case "timeout" => new java.util.concurrent.TimeoutException("fixture timeout")
      case _ => new AdapterFailure("fixture", cause)
    }
    rawFault(name, boundary, writeFault, error).map { result =>
      assert(result.swap.exists(_ eq error), result.toString)
      assertEquals(error.getCause, if (kind == "wrapper" || kind == "adapter") cause else null)
      assertEquals(
        WireSuite.outcomeStatus(result),
        if (kind == "timeout") "inconclusive" else "error",
      )
    }
  }
  for {
    (observer, name, boundary) <- boundaries
    writeFault <- Vector(false, true)
  }
    test(s"raw cancellation $observer: write=$writeFault") {
      for {
        blocked <- Deferred[IO, Unit]
        released <- Deferred[IO, Unit]
        fiber <- rawFault(
          name,
          boundary,
          writeFault,
          new RuntimeException("unused"),
          Some(blocked -> released),
        ).start
        _ <- blocked.get
        _ <- fiber.cancel
        outcome <- fiber.join
        _ <- released.get
        _ <- IO(assert(outcome.isCanceled))
      } yield ()
    }

  test("unprocessed request exclusion remains an observation limit") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      _ <- positive.expecting(Set(3))
      excluded <- positive.observe(goaway(1), fromServer = true).attempt
      _ <- IO(assert(excluded.swap.exists(_.isInstanceOf[WireObservationLimit])))
    } yield ()
  }

  test("positive admission is per connection and preserves malformed raw stimuli") {
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit codecSettings =>
      for {
        trace <- Transcript.create[IO]()
        writes <- Ref.of[IO, Vector[ByteVector]](Vector.empty)
        peer <- RawPeer.create[IO](
          (_: Int) => IO.pure(None),
          (bytes: Chunk[Byte]) => writes.update(_ :+ bytes.toByteVector),
          IO.unit,
          trace,
        )
        other <- RawPeer
          .create[IO]((_: Int) => IO.pure(None), (_: Chunk[Byte]) => IO.unit, IO.unit, trace)
        _ <- writeFrame(peer, headers(1, Vector(":method" -> "GET")))
        positive <- codecSettings.positive(peer)
        _ <- positive.observe(goaway(1), fromServer = true)
        malformed = Vector(0, 2, 1).map(id => headers(id, Vector(":method" -> "GET")))
        _ <- malformed.traverse_(writeFrame(peer, _))
        excluded <- writeFrame(peer, headers(3, Vector(":method" -> "GET"))).attempt
        _ <- writeFrame(other, headers(1, Vector(":method" -> "GET")))
        actual <- writes.get
        _ <- IO {
          assert(excluded.swap.exists(_.isInstanceOf[WireObservationLimit]))
          assertEquals(actual.drop(1), malformed.map(frame => encode(frame)))
        }
      } yield ()
    }
  }

  test("generic readUntil retains its original strict GOAWAY boundary") {
    for {
      trace <- Transcript.create[IO]()
      bytes <- Ref.of[IO, ByteVector](
        encode(goaway(1)) ++ encode(headers(1, Vector(":status" -> "200")))
      )
      peer <- RawPeer.create[IO](
        (n: Int) => bytes.modify(b => (b.drop(n.toLong), Some(Chunk.byteVector(b.take(n.toLong))))),
        (_: Chunk[Byte]) => IO.unit,
        IO.unit,
        trace,
      )
      result <- Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Headers).attempt
      _ <- IO(assert(result.swap.exists(_.isInstanceOf[WireDecodingFailure])))
    } yield ()
  }

  test("canceled higher stream does not create a lower response obligation") {
    for {
      positive <- Http2PositiveObservation.create[IO]
      _ <- positive.openingStream(1)
      _ <- positive.openingStream(3)
      _ <- positive.expecting(Set(1))
      _ <- positive.observe(goaway(1), fromServer = true)
      _ <- positive.observe(headers(1, Vector(":status" -> "200")), fromServer = true)
      _ <- positive.completed(Set(1))
      denied <- positive.observe(goaway(0), fromServer = true).attempt
      _ <- IO(assert(denied.swap.exists(_.isInstanceOf[ProtocolFailure])))
    } yield ()
  }

  for (
    name <- Vector(
      "stream-credit-boundary",
      "connection-credit-independent",
      "negative-stream-window",
      "maximum-stream-window-update",
    )
  )
    test(s"native target exchange rejection retains its protocol-failure boundary: $name") {
      val native = new IllegalStateException("target rejected valid upload")
      val driver = new ClientDriver[IO] {
        def exchange(request: RequestData): Resource[IO, ResponseData[IO]] =
          Resource.eval(IO.raiseError(native))
        def exchangeStream(
            request: RequestData,
            body: FixtureCleanup[IO] => Stream[IO, Byte],
        ): Resource[IO, ResponseData[IO]] = Resource.eval(IO.raiseError(native))
      }
      val target = new WireTarget[IO] {
        def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

        val name = "native-exchange-failure"
        val version = "1"
        def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
          Resource.eval(IO.raiseError(new IllegalStateException("client fixture")))
        def client(
            protocol: Protocol,
            address: SocketAddress[IpAddress],
        ): Resource[IO, ClientDriver[IO]] =
          Resource.pure(driver)
      }
      for {
        trace <- Transcript.create[IO]()
        result <- Http2StateCases.all
          .find(_.id == "h2.client." + name)
          .get
          .run(CaseContext(target, trace))
          .attempt
        _ <- IO {
          assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
          assert(result.swap.exists(_.getMessage.contains("target rejected valid upload")))
          assertEquals(WireSuite.outcomeStatus(result), "fail")
        }
      } yield ()
    }

}
