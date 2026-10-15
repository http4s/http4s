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
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.ember.server.EmberServerBuilder

import scala.concurrent.duration._

class EmberTargetSuite extends RuntimeDiagnostics.Suite {
  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  override val munitIOTimeout: Duration = 20.seconds

  Vector("onCancel", "guaranteeCase", "resource").foreach { boundary =>
    Vector(false, true).foreach { recovered =>
      test(s"HTTP/2 monitors effect cleanup: $boundary, recovered: $recovered") {
        val cleanupError = new java.io.IOException("effect-owned canceled cleanup")
        (for {
          started <- Deferred[IO, Unit]
          cancel <- Deferred[IO, Unit]
          finalized <- Deferred[IO, Resource.ExitCase]
          observed <- Ref.of[IO, Vector[AdapterFailure]](Vector.empty)
          transcript <- Transcript.create[IO]()
          monitored = target.withFailureObserver(error => observed.update(_ :+ error))
          effect = started.complete(()).void *> cancel.get *> IO.canceled
          finish = (exit: Resource.ExitCase) => {
            val failed = finalized.complete(exit).void *> IO.raiseError[Unit](cleanupError)
            if (recovered) failed.handleErrorWith(_ => IO.unit) else failed
          }
          source = (cleanup: FixtureCleanup[IO]) =>
            boundary match {
              case "onCancel" =>
                Stream.exec(effect.onCancel(cleanup(finish(Resource.ExitCase.Canceled))))
              case "guaranteeCase" =>
                Stream.exec(
                  effect.guaranteeCase(exit => cleanup(finish(Resource.ExitCase.fromOutcome(exit))))
                )
              case _ =>
                Stream.exec(
                  Resource.makeCase(IO.unit)((_, exit) => cleanup(finish(exit))).use(_ => effect)
                )
            }
          result <- CaseContext(monitored, transcript)
            .client(Protocol.Http2) { client =>
              client.exchangeStream(RequestData(method = "POST"), source).use { response =>
                IO(assertEquals(response.status, 200)) *> response.body.compile.drain *>
                  cancel.complete(()).void *> finalized.get.void
              }
            } { peer =>
              Http2Wire.serverHandshake(peer) *>
                Http2Wire
                  .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
                  .flatMap { head =>
                    started.get *> Http2Wire.writeFrame(
                      peer,
                      Http2Wire.headers(head.streamId, Vector(":status" -> "200"), endStream = true),
                    )
                  }
            }
            .attempt
          _ <- finalized.get.assertEquals(Resource.ExitCase.Canceled)
          failures <- observed.get
          _ <- IO {
            if (recovered) {
              assertEquals(result, Right(()))
              assertEquals(failures, Vector.empty)
            } else {
              assert(result.left.toOption.flatMap(AdapterFailure.find).isDefined)
              assert(failures.exists(_.getCause eq cleanupError))
            }
          }
        } yield ()).timeout(5.seconds)
      }
    }
  }

  Vector(false, true).foreach { nested =>
    test(s"HTTP/2 retains a self-canceled upload's failing finalizer, nested: $nested") {
      val cleanup = new java.io.IOException("self-canceled source cleanup")
      (for {
        started <- Deferred[IO, Unit]
        cancel <- Deferred[IO, Unit]
        finalized <- Deferred[IO, Resource.ExitCase]
        observed <- Deferred[IO, AdapterFailure]
        transcript <- Transcript.create[IO]()
        monitored = target.withFailureObserver(failure => observed.complete(failure).void)
        run = Stream.exec(started.complete(()).void *> cancel.get *> IO.canceled)
        finish = (exit: Resource.ExitCase) =>
          finalized.complete(exit).void *> IO.raiseError[Unit](cleanup)
        source =
          if (nested)
            Stream
              .resource(Resource.makeCase(IO.unit)((_, exit) => finish(exit)))
              .flatMap(_ => run)
              .scope
          else run.onFinalizeCase(finish)
        result <- CaseContext(monitored, transcript)
          .client(Protocol.Http2) { client =>
            client.exchangeStream(RequestData(method = "POST"), _ => source).use { response =>
              IO(assertEquals(response.status, 200)) *> response.body.compile.drain *>
                cancel.complete(()).void *> observed.get.void
            }
          } { peer =>
            Http2Wire.serverHandshake(peer) *>
              Http2Wire
                .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
                .flatMap { head =>
                  started.get *> Http2Wire.writeFrame(
                    peer,
                    Http2Wire.headers(head.streamId, Vector(":status" -> "200"), endStream = true),
                  )
                }
          }
          .attempt
        _ <- finalized.get.assertEquals(Resource.ExitCase.Canceled)
        failure <- observed.get
        _ <- IO(assertEquals(failure.getCause, cleanup))
        _ <- IO(assert(result.left.toOption.flatMap(AdapterFailure.find).isDefined))
      } yield ()).timeout(5.seconds)
    }
  }

  Vector(false, true).foreach { deadline =>
    test(s"HTTP/2 retains canceled upload finalizer failure, deadline: $deadline") {
      val sourceError = new java.io.IOException("canceled fixture finalizer")
      (for {
        sourceStarted <- Deferred[IO, Unit]
        finalized <- Ref.of[IO, Int](0)
        responseCompleted <- Ref.of[IO, Boolean](false)
        transcript <- Transcript.create[IO]()
        source = (Stream.exec(sourceStarted.complete(()).void) ++ Stream.never[IO])
          .onFinalize(finalized.update(_ + 1) *> IO.raiseError[Unit](sourceError))
        error <- AdapterFailure
          .observe(target) { monitored =>
            val run = CaseContext(monitored, transcript).client(Protocol.Http2) { client =>
              client.exchangeStream(RequestData(method = "POST"), _ => source).use { response =>
                IO(assertEquals(response.status, 200)) *> response.body.compile.drain *>
                  responseCompleted.set(true)
              }
            } { peer =>
              Http2Wire.serverHandshake(peer) *>
                Http2Wire
                  .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
                  .flatMap { head =>
                    sourceStarted.get *> (if (deadline) IO.never
                                          else
                                            Http2Wire.writeFrame(
                                              peer,
                                              Http2Wire.headers(
                                                head.streamId,
                                                Vector(":status" -> "200"),
                                                endStream = true,
                                              ),
                                            ))
                  }
            }
            if (deadline)
              IO.race(sourceStarted.get *> IO.never[Unit].timeout(50.millis), run).void
            else run
          }
          .intercept[AdapterFailure]
        _ <- IO(
          assert(
            Iterator
              .iterate[Throwable](error)(_.getCause)
              .takeWhile(_ != null)
              .contains(sourceError)
          )
        )
        _ <- finalized.get.assertEquals(1)
        _ <- responseCompleted.get.assertEquals(!deadline)
      } yield ()).timeout(8.seconds)
    }
  }

  Vector(false, true).foreach { deadline =>
    test(s"HTTP/2 retains canceled effect-level upload finalizer failure, deadline: $deadline") {
      val sourceError = new java.io.IOException("canceled fixture finalizer")
      (for {
        sourceStarted <- Deferred[IO, Unit]
        finalized <- Ref.of[IO, Int](0)
        responseCompleted <- Ref.of[IO, Boolean](false)
        transcript <- Transcript.create[IO]()
        source = (cleanup: FixtureCleanup[IO]) =>
          Stream.exec(
            (sourceStarted.complete(()).void *> IO.never[Unit]).onCancel(
              cleanup(finalized.update(_ + 1) *> IO.raiseError[Unit](sourceError))
            )
          )
        error <- AdapterFailure
          .observe(target) { monitored =>
            val run = CaseContext(monitored, transcript).client(Protocol.Http2) { client =>
              client.exchangeStream(RequestData(method = "POST"), source).use { response =>
                IO(assertEquals(response.status, 200)) *> response.body.compile.drain *>
                  responseCompleted.set(true)
              }
            } { peer =>
              Http2Wire.serverHandshake(peer) *>
                Http2Wire
                  .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
                  .flatMap { head =>
                    sourceStarted.get *> (if (deadline) IO.never
                                          else
                                            Http2Wire.writeFrame(
                                              peer,
                                              Http2Wire.headers(
                                                head.streamId,
                                                Vector(":status" -> "200"),
                                                endStream = true,
                                              ),
                                            ))
                  }
            }
            if (deadline)
              IO.race(sourceStarted.get *> IO.never[Unit].timeout(50.millis), run).void
            else run
          }
          .intercept[AdapterFailure]
        _ <- IO(
          assert(
            Iterator
              .iterate[Throwable](error)(_.getCause)
              .takeWhile(_ != null)
              .contains(sourceError)
          )
        )
        _ <- finalized.get.assertEquals(1)
        _ <- responseCompleted.get.assertEquals(!deadline)
      } yield ()).timeout(8.seconds)
    }
  }

  Vector(false, true).foreach { late =>
    test(s"HTTP/2 retains upload source failure after response complete: $late") {
      val sourceError = new java.io.IOException("fixture upload source")
      (for {
        sourceStarted <- Deferred[IO, Unit]
        failSource <- Deferred[IO, Unit]
        resetObserved <- Deferred[IO, Unit]
        transcript <- Transcript.create[IO]()
        source = Stream.exec(sourceStarted.complete(()).void *> failSource.get) ++
          Stream.raiseError[IO](sourceError)
        result <- CaseContext(target, transcript)
          .client(Protocol.Http2) { client =>
            client.exchangeStream(RequestData(method = "POST"), _ => source).use { response =>
              response.body.compile.drain *> failSource.complete(()).void *> resetObserved.get
            }
          } { peer =>
            for {
              _ <- Http2Wire.serverHandshake(peer)
              request <- Http2Wire.readUntil(peer, fromServer = false)(
                _.frameType == Http2Wire.Headers
              )
              _ <- sourceStarted.get
              _ <-
                if (late)
                  Http2Wire.writeFrame(
                    peer,
                    Http2Wire
                      .headers(request.streamId, Vector(":status" -> "200"), endStream = true),
                  )
                else failSource.complete(()).void
              // Native reset proves the uploader processed the source failure, rather
              // than merely starting an effect which release might cancel unfinished.
              _ <- Http2Wire.readUntil(peer, fromServer = false)(frame =>
                frame.frameType == Http2Wire.RstStream && frame.streamId == request.streamId
              )
              _ <- resetObserved.complete(())
            } yield ()
          }
          .attempt
        _ <- result match {
          case Left(error) =>
            AdapterFailure
              .raiseIfPresent[IO](error)
              .intercept[AdapterFailure]
              .map(failure => assertEquals(failure.getCause, sourceError))
          case Right(_) => IO(fail("source failure disappeared after response completion"))
        }
      } yield ()).timeout(8.seconds)
    }
  }

  Vector("head", "body", "trailers").foreach { waitingOn =>
    test(s"HTTP/2 cancellation remains bounded while waiting for $waitingOn") {
      (for {
        waiting <- Deferred[IO, Unit]
        headReceived <- Deferred[IO, Unit]
        canceled <- Deferred[IO, Unit]
        transcript <- Transcript.create[IO]()
        _ <- CaseContext(target, transcript).client(Protocol.Http2) { client =>
          val run = client.exchange(RequestData()).use { response =>
            waiting.complete(()).void *> (if (waitingOn == "trailers") response.trailers.void
                                          else response.body.compile.drain)
          }
          Resource.make(run.start)(_.cancel).use { fiber =>
            (if (waitingOn == "head") headReceived.get else waiting.get) *>
              fiber.cancel.timeout(2.seconds) *> fiber.join.flatMap {
                case cats.effect.Outcome.Canceled() => canceled.complete(()).void
                case other => IO(fail(s"expected cancellation, got $other"))
              }
          }
        } { peer =>
          Http2Wire.serverHandshake(peer) *>
            Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers).flatMap {
              head =>
                headReceived.complete(()).void *>
                  (if (waitingOn == "head") IO.unit
                   else
                     Http2Wire.writeFrame(
                       peer,
                       Http2Wire
                         .headers(head.streamId, Vector(":status" -> "200"), endStream = false),
                     )) *>
                  canceled.get
            }
        }
      } yield ()).timeout(5.seconds)
    }
  }

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
                .exchangeStream(RequestData(method = "POST"), _ => upload)
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
              driver.exchangeStream(request, _ => upload).use { response =>
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

  test("HTTP/2 server observation preserves :path and its empty query boundary") {
    val path = "/a%2Fb?"
    for {
      transcript <- Transcript.create[IO]()
      _ <- CaseContext(target, transcript).server(Protocol.Http2, ServerScenario.Reply()).use {
        case (peer, server) =>
          for {
            _ <- Http2Wire.clientHandshake(peer)
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.headers(
                1,
                Vector(
                  ":method" -> "GET",
                  ":scheme" -> "http",
                  ":authority" -> "wire.test",
                  ":path" -> path,
                ),
              ),
            )
            observed <- server.received
            _ <- IO(assertEquals(observed.path, path))
          } yield ()
      }
    } yield ()
  }

  test(
    "resource release errors retain adapter provenance without tagging native operation errors"
  ) {
    val native = new java.io.IOException("native transport")
    for {
      released <- EmberTarget
        .guardRelease(Resource.make(IO.unit)(_ => IO.raiseError[Unit](native)))
        .use(_ => IO.unit)
        .attempt
      _ <- IO(assert(released.left.exists(_.isInstanceOf[AdapterFailure])))
      allocated <- EmberTarget
        .guardRelease(Resource.eval(IO.raiseError[Unit](native)))
        .use(_ => IO.unit)
        .attempt
      _ <- IO(assertEquals(allocated, Left(native)))
      consumed <- EmberTarget
        .guardRelease(Resource.pure[IO, Unit](()))
        .use(_ => IO.raiseError[Unit](native))
        .attempt
      _ <- IO(assertEquals(consumed, Left(native)))
    } yield ()
  }

  test("primary native error retains simultaneous adapter cleanup failure") {
    val native = new java.io.IOException("native transport")
    val cleanup = new java.io.IOException("cleanup")
    EmberTarget
      .guardRelease(Resource.make(IO.unit)(_ => IO.raiseError[Unit](cleanup)))
      .use(_ => IO.raiseError[Unit](native))
      .attempt
      .flatMap { result =>
        IO(assertEquals(result, Left(native))) *>
          AdapterFailure.raiseIfPresent[IO](native).intercept[AdapterFailure].map { failure =>
            assertEquals(failure.getCause, cleanup)
          }
      }
  }

  test("case observation preserves fixture and unsuppressible primary errors independently") {
    val primary = new RuntimeException("primary", null, false, false) {}
    val challenge = Catalog.all
      .find(_.id == "h1.server.get")
      .get
      .copy(run =
        context =>
          context.server(Protocol.Http1, ServerScenario.Reply(status = -1)).use { case (peer, _) =>
            peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n") *>
              Http1Wire.readResponse(peer) *> IO.raiseError[Unit](primary)
          }
      )
    for {
      transcript <- Transcript.create[IO]()
      result <- WireSuite.runCase(target, challenge, transcript, 5.seconds).attempt
      _ <- IO(assertEquals(WireSuite.outcomeStatus(result), "error"))
      _ <- IO(assert(result.left.exists(_.isInstanceOf[AdapterFailure])))
      _ <- IO(assert(result.left.exists(_.getSuppressed.contains(primary))))
      _ <- IO(assertEquals(primary.getSuppressed.toList, Nil))
      _ <- AdapterFailure.observe(target)(monitored =>
        monitored.server(Protocol.Http1, ServerScenario.Reply()).use(_ => IO.unit)
      )
    } yield ()
  }

  test("invalid fixture status survives an ordinary Ember 500 response") {
    fixtureFailure(ServerScenario.Reply(status = -1), "GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n")
  }

  test("fixture body limit survives an ordinary Ember 500 response") {
    val body = "x" * (1024 * 1024 + 1)
    fixtureFailure(
      ServerScenario.EchoBody,
      s"POST / HTTP/1.1\r\nHost: wire.test\r\nContent-Length: ${body.length}\r\n\r\n$body",
    )
  }

  test("native request body decoding failure is not a fixture failure") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        AdapterFailure.observe(target)(monitored =>
          CaseContext(monitored, transcript).server(Protocol.Http1, ServerScenario.EchoBody).use {
            case (peer, _) =>
              peer.writeAscii(
                "POST / HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\nZ\r\n"
              ) *>
                Http1Wire
                  .readResponse(peer)
                  .flatMap(response => IO(assertEquals(response.status, 500)))
          }
        )
      }
      .timeout(5.seconds)
  }

  private def fixtureFailure(scenario: ServerScenario, request: String): IO[Unit] =
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).server(Protocol.Http1, scenario).use { case (peer, _) =>
          peer.writeAscii(request) *> Http1Wire.readResponse(peer).void
        }
      }
      .timeout(5.seconds)
      .intercept[AdapterFailure]
      .void

  test("release provenance wrapper preserves canceled acquisition and partial cleanup") {
    for {
      started <- Deferred[IO, Unit]
      released <- Ref.of[IO, Int](0)
      partial = Resource.make(IO.unit)(_ => released.update(_ + 1)).flatMap { _ =>
        Resource.eval(started.complete(()) *> IO.never[Unit])
      }
      _ <- Resource
        .make(EmberTarget.guardRelease(partial).use(_ => IO.unit).start)(_.cancel)
        .use { fiber =>
          started.get *> fiber.cancel.timeout(2.seconds) *> fiber.join.flatMap(assertCanceled)
        }
      _ <- released.get.assertEquals(1)
    } yield ()
  }

  test("metadata records effective client and server timeouts") {
    val server = target.settings(Protocol.Http2, Role.Server).toMap
    val client = target.settings(Protocol.Http1, Role.Client).toMap
    assertEquals(server("request-header-receive-timeout-ms"), "15000")
    assertEquals(server("idle-timeout-ms"), "20000")
    assertEquals(server("shutdown-timeout-ms"), "0")
    assertEquals(client("request-timeout-ms"), "15000")
    assertEquals(client("idle-connection-time-ms"), "20000")
    assertEquals(client("http2-prior-knowledge"), "false")
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
                  first <- Http2Wire.readUntil(peer, fromServer = false)(
                    _.frameType == Http2Wire.Headers
                  )
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
