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
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.Resource
import com.comcast.ip4s._
import fs2.Stream
import fs2.io.net.Network
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.ember.server.EmberServerBuilder

import scala.concurrent.duration._

class EmberTargetSuite extends CatsEffectSuite {
  override val munitIOTimeout: Duration = 20.seconds

  private val target = EmberTarget[IO]
  private val emptyRequest = RequestData(headers = Vector("Content-Length" -> "0"))

  Vector(Protocol.Http1, Protocol.Http2).foreach { protocol =>
    test(s"${protocol.id}: canceling an upload finalizes its source and releases the peer socket") {
      for {
        started <- Deferred[IO, Unit]
        requestObserved <- Deferred[IO, Either[Throwable, Unit]]
        peerClosed <- Deferred[IO, Either[Throwable, Unit]]
        socketReleased <- Deferred[IO, Unit]
        sourceFinalizers <- Ref.of[IO, Int](0)
        serverReleased <- Deferred[IO, Unit]
        upload = trackedSource(started, sourceFinalizers)(Stream.never[IO])
        // The independent peer isolates client cancellation from server application-body reads.
        _ <- uploadPeer(protocol, requestObserved, peerClosed, socketReleased)
          .onFinalize(serverReleased.complete(()).void)
          .use { address =>
            val exercise = target.client(protocol, address).use { driver =>
              val exchange = driver
                .exchangeStream(RequestData(method = "POST"), upload)
                .use(_ =>
                  IO.raiseError[Unit](new AssertionError("unfinished upload received a response"))
                )
              Resource.make(exchange.start)(_.cancel).use { fiber =>
                for {
                  _ <- requestObserved.get.flatMap(IO.fromEither)
                  _ <- started.get
                  _ <- fiber.cancel
                  outcome <- fiber.join
                  _ <- assertCanceled(outcome)
                  _ <- sourceFinalizers.get.assertEquals(1)
                } yield ()
              }
            }
            val peerFailure = peerClosed.get.flatMap {
              case Left(error) => IO.raiseError[Unit](error)
              case Right(_) => IO.never[Unit]
            }
            exercise.race(peerFailure).void *> peerClosed.get.flatMap(IO.fromEither)
          }
        _ <- serverReleased.tryGet.assertEquals(Some(()))
        _ <- socketReleased.tryGet.assertEquals(Some(()))
        _ <- sourceFinalizers.get.assertEquals(1)
      } yield ()
    }

    test(s"${protocol.id}: canceling response consumption finalizes both body scopes") {
      for {
        serverStarted <- Deferred[IO, Unit]
        firstByte <- Deferred[IO, Unit]
        serverFinalizers <- Ref.of[IO, Int](0)
        consumerFinalizers <- Ref.of[IO, Int](0)
        serverReleased <- Deferred[IO, Unit]
        body = trackedSource(serverStarted, serverFinalizers)(
          Stream.emit(1.toByte) ++ Stream.never[IO]
        )
        _ <- streamingServer(protocol, body)
          .onFinalize(serverReleased.complete(()).void)
          .use { address =>
            target.client(protocol, address).use { driver =>
              val consume = driver.exchange(emptyRequest).use { response =>
                response.body
                  .evalTap(_ => firstByte.complete(()).void *> IO.never[Unit])
                  .onFinalize(consumerFinalizers.update(_ + 1))
                  .compile
                  .drain
              }
              Resource.make(consume.start)(_.cancel).use { fiber =>
                for {
                  _ <- serverStarted.get
                  _ <- firstByte.get
                  _ <- fiber.cancel
                  outcome <- fiber.join
                  _ <- assertCanceled(outcome)
                  _ <- consumerFinalizers.get.assertEquals(1)
                } yield ()
              }
            }
          }
        _ <- serverReleased.tryGet.assertEquals(Some(()))
        _ <- serverFinalizers.get.assertEquals(1)
        _ <- consumerFinalizers.get.assertEquals(1)
      } yield ()
    }

    test(
      s"${protocol.id}: releasing a partially consumed response releases upload and server sources"
    ) {
      for {
        uploadStarted <- Deferred[IO, Unit]
        serverStarted <- Deferred[IO, Unit]
        finishResponse <- Deferred[IO, Unit]
        uploadFinalizers <- Ref.of[IO, Int](0)
        serverFinalizers <- Ref.of[IO, Int](0)
        serverReleased <- Deferred[IO, Unit]
        upload = trackedSource(uploadStarted, uploadFinalizers)(Stream.emit(42.toByte))
        body = trackedSource(serverStarted, serverFinalizers)(
          Stream.emit(1.toByte) ++ Stream.exec(finishResponse.get) ++ Stream.emit(2.toByte)
        )
        _ <- streamingServer(protocol, body)
          .onFinalize(serverReleased.complete(()).void)
          .use { address =>
            target.client(protocol, address).use { driver =>
              val request = RequestData(method = "POST", headers = Vector("Content-Length" -> "1"))
              driver.exchangeStream(request, upload).use { response =>
                for {
                  prefix <- response.body.take(1).compile.toVector
                  _ <- IO(assertEquals(prefix, Vector(1.toByte)))
                  _ <- uploadStarted.get
                  _ <- serverStarted.get
                  // The remainder becomes available before normal Resource release can drain it.
                  _ <- finishResponse.complete(())
                } yield ()
              } *> uploadFinalizers.get.assertEquals(1)
            }
          }
        _ <- serverReleased.tryGet.assertEquals(Some(()))
        _ <- uploadFinalizers.get.assertEquals(1)
        _ <- serverFinalizers.get.assertEquals(1)
      } yield ()
    }
  }

  private def trackedSource(
      started: Deferred[IO, Unit],
      finalizers: Ref[IO, Int],
  )(body: Stream[IO, Byte]): Stream[IO, Byte] =
    Stream.bracket(started.complete(()).void)(_ => finalizers.update(_ + 1)).flatMap(_ => body)

  private def assertCanceled(outcome: Outcome[IO, Throwable, Unit]): IO[Unit] =
    outcome match {
      case Outcome.Canceled() => IO.unit
      case Outcome.Errored(error) => IO.raiseError(error)
      case Outcome.Succeeded(result) =>
        result *> IO(fail("operation completed before cancellation"))
    }

  private def uploadPeer(
      protocol: Protocol,
      requestObserved: Deferred[IO, Either[Throwable, Unit]],
      peerClosed: Deferred[IO, Either[Throwable, Unit]],
      socketReleased: Deferred[IO, Unit],
  ): Resource[IO, SocketAddress[IpAddress]] =
    for {
      listener <- Network[IO].bind(SocketAddress(ip"127.0.0.1", port"0"))
      transcript <- Resource.eval(Transcript.create[IO]())
      serve = listener.accept.head.compile.resource.lastOrError
        .onFinalize(socketReleased.complete(()).void)
        .use { socket =>
          RawPeer.create(socket, transcript).flatMap { peer =>
            val receiveHead = protocol match {
              case Protocol.Http1 =>
                peer.readUntil(Http1Wire.ascii("\r\n\r\n")).flatMap { bytes =>
                  Check(
                    bytes.startsWith(Http1Wire.ascii("POST / HTTP/1.1\r\n")),
                    "expected the streaming POST request head",
                  )
                }
              case Protocol.Http2 =>
                for {
                  _ <- Http2Wire.serverHandshake(peer)
                  first <- Http2Wire.readUntil(peer)(_.frameType == Http2Wire.Headers)
                  decoder <- HpackWire.decoder[IO]()
                  headers <- Http2Wire.readHeaders(peer, first, decoder)
                  _ <- Check.equal(
                    headers.collect { case (":method", value) => value },
                    Vector("POST"),
                  )
                } yield ()
            }
            receiveHead *> requestObserved.complete(Right(())).void *>
              peer.readToEnd(65536).void.handleErrorWith { error =>
                if (RuntimeInfo.isConnectionReset(error)) IO.unit else IO.raiseError(error)
              }
          }
        }
        .attempt
        .flatMap { result =>
          requestObserved.complete(result).void *> peerClosed.complete(result).void
        }
      _ <- Resource.make(serve.start) { fiber =>
        fiber.cancel *> fiber.join.flatMap {
          case Outcome.Errored(error) => IO.raiseError(error)
          case _ => IO.unit
        }
      }
    } yield listener.address.asIpUnsafe

  private def streamingServer(
      protocol: Protocol,
      body: Stream[IO, Byte],
  ): Resource[IO, SocketAddress[IpAddress]] = {
    val builder = EmberServerBuilder
      .default[IO]
      .withHost(ip"127.0.0.1")
      .withPort(port"0")
      .withShutdownTimeout(Duration.Zero)
      .withIdleTimeout(20.seconds)
      .withRequestHeaderReceiveTimeout(15.seconds)
      .withHttpApp(
        HttpApp[IO](request => request.body.compile.drain.as(Response[IO]().withBodyStream(body)))
      )
    (protocol match {
      case Protocol.Http1 => builder.withoutHttp2
      case Protocol.Http2 => builder.withHttp2
    }).build.map(_.addressIp4s)
  }
}
