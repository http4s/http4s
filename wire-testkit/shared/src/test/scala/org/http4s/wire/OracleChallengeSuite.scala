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

import cats.effect.Async
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.std.Queue
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.io.net.Network
import munit.CatsEffectSuite
import scodec.bits.ByteVector

import scala.concurrent.duration._

class OracleChallengeSuite extends CatsEffectSuite {
  override val munitIOTimeout: Duration = 15.seconds

  private val accepted = Http1Wire.ascii("accepted")

  test("the GET smoke oracle accepts the correct scripted response and releases its peer") {
    runHttp1Challenge(accepted, 8, None)
  }

  test("the GET smoke oracle rejects incorrect content at the expected body assertion") {
    val wrong = Http1Wire.ascii("rejected")
    runHttp1Challenge(wrong, 8, Some(wrong))
  }

  test("the GET smoke oracle detects a body delimited one octet too early") {
    runHttp1Challenge(accepted, 7, Some(accepted.take(7)))
  }

  test("the PING smoke oracle accepts an identical payload and releases its peer") {
    runPingChallenge(identity, shouldFail = false)
  }

  test("the PING smoke oracle rejects a corrupted echo at the expected payload assertion") {
    runPingChallenge(
      payload => ByteVector((payload(0) ^ 1).toByte) ++ payload.drop(1),
      shouldFail = true,
    )
  }

  test("canceling a waiting smoke case releases the scripted socket, listener, and fiber") {
    for {
      waiting <- Deferred[IO, Unit]
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        readAndPublish(peer, publish) *> waiting.complete(()).void *> IO.never[Unit]
      }
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      run = smoke("h1.server.get").run(CaseContext(target, transcript))
      _ <- Resource.make(run.start)(_.cancel).use { fiber =>
        for {
          _ <- waiting.get
          _ <- fiber.cancel
          outcome <- fiber.join
          _ <- outcome match {
            case Outcome.Canceled() => IO.unit
            case Outcome.Errored(error) => IO.raiseError(error)
            case Outcome.Succeeded(result) => result *> IO(fail("waiting smoke case completed"))
          }
          _ <- assertReleased(probe)
        } yield ()
      }
    } yield ()
  }

  private def runHttp1Challenge(
      body: ByteVector,
      declaredLength: Int,
      expectedFailureBody: Option[ByteVector],
  ): IO[Unit] =
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        readAndPublish(peer, publish) *> peer.write(
          Http1Wire.message(
            "HTTP/1.1 200 OK",
            Vector("Content-Length" -> declaredLength.toString),
            body,
          )
        )
      }
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      run = smoke("h1.server.get").run(CaseContext(target, transcript))
      _ <- expectedFailureBody match {
        case None => run
        case Some(observed) =>
          run.intercept[ProtocolFailure].flatMap { error =>
            IO(assertEquals(error.getMessage, s"expected: $accepted\nobserved: $observed"))
          }
      }
      _ <- assertReleased(probe)
    } yield ()

  private def runPingChallenge(
      transform: ByteVector => ByteVector,
      shouldFail: Boolean,
  ): IO[Unit] =
    for {
      payloads <- Deferred[IO, (ByteVector, ByteVector)]
      fixture <- ScriptedTarget.create[IO] { (peer, _) =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          ping <- Http2Wire.readUntil(peer)(frame =>
            frame.frameType == Http2Wire.Ping && !frame.hasFlag(Http2Wire.Ack)
          )
          echoed = transform(ping.payload)
          _ <- payloads.complete(ping.payload -> echoed)
          _ <- Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack, payload = echoed))
        } yield ()
      }
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      run = smoke("h2.server.ping").run(CaseContext(target, transcript))
      _ <-
        if (shouldFail)
          for {
            error <- run.intercept[ProtocolFailure]
            values <- payloads.get
            _ <- IO(
              assertEquals(error.getMessage, s"expected: ${values._1}\nobserved: ${values._2}")
            )
          } yield ()
        else run
      _ <- assertReleased(probe)
    } yield ()

  private def readAndPublish(
      peer: RawPeer[IO],
      publish: RequestData => IO[Unit],
  ): IO[Unit] =
    Http1Wire.readRequest(peer).flatMap { request =>
      publish(RequestData(request.method, request.target, request.headers, request.body))
    }

  private def smoke(id: String): WireCase =
    SmokeCases.all
      .find(_.id == id)
      .getOrElse(throw new IllegalArgumentException(s"missing case: $id"))

  private def assertReleased(probe: ScriptedTarget.Probe[IO]): IO[Unit] =
    for {
      _ <- probe.accepted.tryGet.assertEquals(Some(()))
      _ <- probe.listenerFinalizers.get.assertEquals(1)
      _ <- probe.socketFinalizers.get.assertEquals(1)
      _ <- probe.scriptFinalizers.get.assertEquals(1)
    } yield ()
}

/** A test-only target for challenging case oracles with deliberately controlled wire output. */
private[wire] final class ScriptedTarget[F[_]: Async: Network] private (
    script: ScriptedTarget.Script[F],
    probe: ScriptedTarget.Probe[F],
) extends WireTarget[F] {
  val name: String = "scripted"
  val version: String = "oracle-challenge"

  def server(_protocol: Protocol, _scenario: ServerScenario): Resource[F, ServerHandle[F]] =
    for {
      received <- Resource.eval(Queue.unbounded[F, RequestData])
      transcript <- Resource.eval(Transcript.create[F]())
      listener <- Network[F]
        .bind(SocketAddress(ip"127.0.0.1", port"0"))
        .onFinalize(probe.listenerFinalizers.update(_ + 1))
      serve = listener.accept.head.compile.resource.lastOrError
        .onFinalize(probe.socketFinalizers.update(_ + 1))
        .use { socket =>
          RawPeer.create(socket, transcript).flatMap { peer =>
            (probe.accepted.complete(()).void *> script(peer, received.offer) *>
              Async[F].never[Unit]).guarantee(probe.scriptFinalizers.update(_ + 1))
          }
        }
      _ <- Resource.make(serve.start) { fiber =>
        fiber.cancel *> fiber.join.flatMap {
          case Outcome.Errored(error) => Async[F].raiseError[Unit](error)
          case _ => Async[F].unit
        }
      }
    } yield ServerHandle(listener.address.asIpUnsafe, received.take, received.tryTake)

  def client(
      _protocol: Protocol,
      _address: SocketAddress[IpAddress],
  ): Resource[F, ClientDriver[F]] =
    Resource.eval(
      Async[F].raiseError[ClientDriver[F]](
        new UnsupportedOperationException("server-only scripted target")
      )
    )
}

private[wire] object ScriptedTarget {
  type Script[F[_]] = (RawPeer[F], RequestData => F[Unit]) => F[Unit]

  final case class Probe[F[_]](
      accepted: Deferred[F, Unit],
      listenerFinalizers: Ref[F, Int],
      socketFinalizers: Ref[F, Int],
      scriptFinalizers: Ref[F, Int],
  )

  def create[F[_]: Async: Network](script: Script[F]): F[(ScriptedTarget[F], Probe[F])] =
    for {
      accepted <- Deferred[F, Unit]
      listenerFinalizers <- Ref.of[F, Int](0)
      socketFinalizers <- Ref.of[F, Int](0)
      scriptFinalizers <- Ref.of[F, Int](0)
      probe = Probe(accepted, listenerFinalizers, socketFinalizers, scriptFinalizers)
    } yield (new ScriptedTarget[F](script, probe), probe)
}
