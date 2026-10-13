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
import fs2.Chunk
import fs2.Stream
import munit.CatsEffectSuite
import scodec.bits.ByteVector

import scala.concurrent.duration._

class NettyTargetSuite extends CatsEffectSuite {
  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  test("native request write failure preserves direct source lifecycle and provenance") {
    def run(wrapped: Boolean): IO[Unit] =
      Resource
        .make(
          IO(
            new io.netty.channel.embedded.EmbeddedChannel(
              new io.netty.handler.codec.http.HttpClientCodec()
            )
          )
        )(channel => IO(channel.finishAndReleaseAll()).void)
        .use { channel =>
          for {
            exit <- Deferred[IO, Resource.ExitCase]
            observed <- Ref.of[IO, Option[AdapterFailure]](None)
            body = (Stream.exec(IO(channel.close()).void) ++ Stream.emit(42.toByte))
              .onFinalizeCase(value => exit.complete(value).void)
            error <- AdapterFailure
              .recorder[IO](Some(failure => observed.set(Some(failure))))
              .use { failures =>
                val source = if (wrapped) failures.source("source")(body) else body
                IO {
                  val send =
                    target.getClass.getDeclaredMethods.find(_.getName.endsWith("sendRequest")).get
                  send.setAccessible(true)
                  send
                    .invoke(
                      target,
                      channel,
                      Protocol.Http1,
                      com.comcast.ip4s.SocketAddress(
                        com.comcast.ip4s.Ipv4Address.fromString("127.0.0.1").get,
                        com.comcast.ip4s.Port.fromInt(80).get,
                      ),
                      RequestData(method = "POST"),
                      source,
                      None,
                      channel.newPromise(),
                    )
                    .asInstanceOf[IO[Unit]]
                }.flatten
              }
              .attempt
            _ <- error match {
              case Left(native: java.nio.channels.ClosedChannelException) =>
                exit.get.assertEquals(Resource.ExitCase.Errored(native)) *>
                  IO(assert(AdapterFailure.find(native).isEmpty))
              case other => IO(fail(s"expected native closed-channel failure, got $other"))
            }
            _ <- observed.get.assertEquals(None)
          } yield ()
        }
    (run(false) *> run(true)).timeout(2.seconds)
  }

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

  private val target = NettyTarget[IO]()
  private val pieces = Vector.tabulate(12)(i => ByteVector.fill(16384)(i.toByte))
  private val body = pieces.foldLeft(ByteVector.empty)(_ ++ _)
  private val duplicateHeaders = Vector("x-repeat" -> "first", "x-repeat" -> "second")

  private def upload: Stream[IO, Byte] =
    Stream.emits(pieces).flatMap(value => Stream.chunk(Chunk.byteVector(value)))

  Vector(Protocol.Http1, Protocol.Http2).foreach { protocol =>
    test(
      s"${protocol.id} streams an upload beyond the HTTP/2 initial window and reuses the driver"
    ) {
      target
        .server(protocol, ServerScenario.EchoBody)
        .use { server =>
          target.client(protocol, server.address).use { client =>
            def exchange = client
              .exchangeStream(RequestData("POST", "/echo", duplicateHeaders), _ => upload)
              .use { response =>
                for {
                  received <- response.body.compile.to(Chunk)
                  trailers <- response.trailers
                } yield {
                  assertEquals(response.status, 200)
                  assertEquals(received.toByteVector, body)
                  assertEquals(trailers, Vector.empty[(String, String)])
                }
              }
            for {
              _ <- exchange
              _ <- exchange
              first <- server.received
              second <- server.received
            } yield Vector(first, second).foreach { request =>
              assertEquals(request.method, "POST")
              assertEquals(request.path, "/echo")
              assertEquals(
                request.headers.filter(_._1.equalsIgnoreCase("x-repeat")),
                duplicateHeaders,
              )
              assertEquals(request.body, body)
            }
          }
        }
        .timeout(10.seconds)
    }

    test(s"${protocol.id} streams response chunks beyond the HTTP/2 initial window") {
      target
        .server(protocol, ServerScenario.ReplyChunks(pieces))
        .use { server =>
          target.client(protocol, server.address).use { client =>
            client.exchange(RequestData()).use { response =>
              response.body.compile.to(Chunk).map(result => assertEquals(result.toByteVector, body))
            }
          }
        }
        .timeout(10.seconds)
    }
  }

  test("canceling the native future bridge does not await native completion") {
    val pending = new io.netty.util.concurrent.DefaultPromise[Unit](
      io.netty.util.concurrent.ImmediateEventExecutor.INSTANCE
    )
    val registered = new java.util.concurrent.atomic.AtomicBoolean(false)
    def awaitRegistration: IO[Unit] = IO(registered.get()).flatMap {
      case true => IO.unit
      case false => IO.cede *> awaitRegistration
    }
    Resource.make(target.awaitFuture { registered.set(true); pending }.start)(_.cancel).use { fiber =>
      for {
        _ <- awaitRegistration
        canceled <- Deferred[IO, Unit]
        _ <- Resource.make((fiber.cancel *> canceled.complete(()).void).start)(_.cancel).use { _ =>
          // Unblock the native promise even on failure so the regression itself has bounded cleanup.
          canceled.get.timeout(1.second).guarantee(IO { pending.trySuccess(()); () })
        }
        _ <- IO(assert(pending.isCancelled))
      } yield ()
    }
  }

  test("HTTP/2 cancels a DATA write at zero stream credit without peer credit") {
    for {
      ready <- Deferred[IO, Unit]
      started <- Deferred[IO, Unit]
      finalized <- Deferred[IO, Unit]
      canceled <- Deferred[IO, Unit]
      transcript <- Transcript.create[IO]()
      _ <- CaseContext(target, transcript).client(Protocol.Http2) { client =>
        val upload = (Stream.exec(ready.get *> started.complete(()).void) ++
          Stream.chunk(Chunk.array(Array.fill[Byte](1024)(42))))
          .onFinalize(finalized.complete(()).void)
        Resource
          .make(
            client
              .exchangeStream(RequestData(method = "POST"), _ => upload)
              .use(_ => IO.never)
              .start
          )(_.cancel)
          .use { fiber =>
            started.get *> IO.sleep(100.millis) *> finalized.tryGet.assertEquals(None) *>
              fiber.cancel.timeout(2.seconds) *> finalized.get *> canceled.complete(()).void
          }
      } { peer =>
        for {
          preface <- peer.readExact(Http2Wire.clientPreface.size.toInt)
          _ <- IO(assertEquals(preface, Http2Wire.clientPreface))
          _ <- Http2Wire.readFrame(peer)
          _ <- peer.write(
            Http2Wire.encode(Http2Wire.settings(Vector(4 -> 0L))) ++
              Http2Wire.encode(Http2Wire.settings(ack = true))
          )
          _ <- {
            def awaitAck: IO[Unit] = Http2Wire.readFrame(peer).flatMap { frame =>
              if (frame.frameType == Http2Wire.Settings && frame.hasFlag(Http2Wire.Ack)) IO.unit
              else if (
                frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.WindowUpdate
              ) awaitAck
              else
                IO.raiseError(new AssertionError(s"unexpected frame before SETTINGS ACK: $frame"))
            }
            awaitAck
          }
          _ <- ready.complete(())
          // No WINDOW_UPDATE is sent. Successful cleanup must not need remote credit.
          _ <- canceled.get.timeout(3.seconds)
        } yield ()
      }
    } yield ()
  }

  test("server observations are visible synchronously and canceled waits do not consume them") {
    val observations = new NettyTarget.Observations[IO]
    val request = RequestData(path = "/observed")
    for {
      waiting <- observations.take.start
      _ <- IO.cede
      _ <- waiting.cancel
      _ <- IO(observations.offer(request))
      _ <- observations.poll.assertEquals(Some(request))
      _ <- observations.poll.assertEquals(None)
      _ <- IO(observations.offer(request))
      _ <- observations.take.assertEquals(request)
    } yield ()
  }

  test("cause-wrapped adapter failures remain infrastructure errors") {
    val failure = new AdapterFailure("fixture conversion", new NumberFormatException("bad status"))
    val combined = new java.io.EOFException("native transport close")
    val cyclic = new RuntimeException("cyclic wrapper")
    combined.addSuppressed(cyclic)
    cyclic.initCause(combined)
    cyclic.addSuppressed(failure)
    for {
      result <- AdapterFailure.raiseIfPresent[IO](new RuntimeException(failure)).attempt
      _ <- IO(assertEquals(result, Left(failure)))
      suppressed <- AdapterFailure.raiseIfPresent[IO](combined).attempt
      _ <- IO(assertEquals(suppressed, Left(failure)))
      _ <- AdapterFailure.raiseIfPresent[IO](new java.io.EOFException("native transport close"))
    } yield ()
  }

  test("server fixture conversion failure is raised on resource release") {
    Transcript.create[IO]().flatMap { transcript =>
      CaseContext(target, transcript)
        .server(Protocol.Http1, ServerScenario.Reply(status = -1))
        .use { case (peer, _) =>
          peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n") *> peer.readToEnd(65536).void
        }
        .attempt
        .flatMap {
          case Left(error) =>
            AdapterFailure
              .raiseIfPresent[IO](error)
              .attempt
              .map(result => assert(result.left.exists(_.isInstanceOf[AdapterFailure])))
          case Right(_) => IO(fail("fixture failure was hidden as a connection close"))
        }
    }
  }

  test("cleanup provenance survives a primary with suppression disabled") {
    // Netty's static Http2Exception constructor uses the same disabled suppression
    // and initialized null cause. Resource.use must not silently lose cleanup here.
    val primary = new RuntimeException("native static failure", null, false, false) {}
    val challenge = Catalog.all
      .find(_.id == "h1.server.get")
      .get
      .copy(run =
        context =>
          context.server(Protocol.Http1, ServerScenario.Reply(status = -1)).use { case (peer, _) =>
            peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n") *>
              peer.readToEnd(65536) *> IO.raiseError[Unit](primary)
          }
      )
    for {
      transcript <- Transcript.create[IO]()
      result <- WireSuite.runCase(target, challenge, transcript, 5.seconds).attempt
      _ <- IO(assertEquals(WireSuite.outcomeStatus(result), "error"))
      _ <- IO(assert(result.left.exists(_.isInstanceOf[AdapterFailure])))
      _ <- IO(assertEquals(primary.getSuppressed.toList, Nil))
      _ <- IO(assert(result.left.exists(_.getSuppressed.contains(primary))))
      _ <- AdapterFailure.observe(target)(monitored =>
        monitored.server(Protocol.Http1, ServerScenario.Reply()).use(_ => IO.unit)
      )
    } yield ()
  }

  test("server fixture failure remains visible alongside a primary native failure") {
    val primary = new java.io.IOException("native failure")
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript)
          .server(Protocol.Http1, ServerScenario.Reply(status = -1))
          .use { case (peer, _) =>
            peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n") *>
              peer.readToEnd(65536) *> IO.raiseError[Unit](primary)
          }
          .attempt
          .flatMap { result =>
            IO(assertEquals(result, Left(primary))) *>
              AdapterFailure.raiseIfPresent[IO](primary).intercept[AdapterFailure].void
          }
      }
      .timeout(5.seconds)
  }

  test("metadata records the loaded native codec and effective role settings") {
    assertEquals(
      target.version,
      io.netty.util.Version.identify().get("netty-codec-http2").artifactVersion(),
    )
    assertEquals(target.settings(Protocol.Http1, Role.Client).toMap.apply("auto-read"), "false")
    assertEquals(
      target.settings(Protocol.Http2, Role.Client).toMap.apply("stream-auto-read"),
      "false",
    )
    assertEquals(
      target.settings(Protocol.Http2, Role.Server).toMap.apply("graceful-shutdown-timeout-ms"),
      "0",
    )
  }

  Vector("h2.client.request-lowercase-field-names", "h2.server.response-lowercase-field-names")
    .foreach { id =>
      test(s"native conversion executes shared case $id") {
        val selected = Catalog.all.find(_.id == id).getOrElse(fail(s"missing shared case $id"))
        Transcript.create[IO]().flatMap(transcript => selected.run(CaseContext(target, transcript)))
      }
    }

  test("HTTP/2 native outgoing conversion lowercases ordered duplicate application headers") {
    val mixed = Vector("X-Repeat" -> "first", "X-Repeat" -> "second")
    target.server(Protocol.Http2, ServerScenario.Reply(headers = mixed)).use { server =>
      target.client(Protocol.Http2, server.address).use { client =>
        client.exchange(RequestData(headers = mixed)).use { response =>
          for {
            _ <- response.body.compile.drain
            observed <- server.pollReceived
          } yield {
            assertEquals(response.headers.filter(_._1 == "x-repeat"), duplicateHeaders)
            assertEquals(observed.map(_.headers.filter(_._1 == "x-repeat")), Some(duplicateHeaders))
          }
        }
      }
    }
  }

  test("HTTP/1 completed native writes survive response release before uploader completion") {
    import io.netty.bootstrap.Bootstrap
    import io.netty.channel._
    import io.netty.channel.nio.NioIoHandler
    import io.netty.channel.socket.nio.NioSocketChannel
    import io.netty.handler.codec.http._

    target
      .server(Protocol.Http1, ServerScenario.Reply())
      .use { server =>
        Resource
          .make(IO(new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())))(group =>
            IO.defer {
              val shutdown =
                group.shutdownGracefully(0L, 0L, java.util.concurrent.TimeUnit.MILLISECONDS)
              target.awaitFuture(shutdown).void
            }
          )
          .use { group =>
            val responses = Vector.fill(2)(scala.concurrent.Promise[Unit]())
            val connect = IO {
              new Bootstrap()
                .group(group)
                .channel(classOf[NioSocketChannel])
                .handler(new ChannelInitializer[NioSocketChannel] {
                  def initChannel(channel: NioSocketChannel): Unit = {
                    channel
                      .pipeline()
                      .addLast(
                        new HttpClientCodec(),
                        new SimpleChannelInboundHandler[HttpObject] {
                          private var count = 0
                          def channelRead0(context: ChannelHandlerContext, message: HttpObject)
                              : Unit =
                            if (message.isInstanceOf[LastHttpContent]) {
                              responses(count).success(())
                              count += 1
                            }
                        },
                      )
                    ()
                  }
                })
                .connect(server.address.host.toString, server.address.port.value)
            }.flatMap(future => target.awaitFuture(future).as(future.channel()))
            Resource.make(connect)(channel => target.awaitFuture(channel.close()).void).use {
              channel =>
                Vector(0, 1).traverse_ { index =>
                  for {
                    written <- Deferred[IO, Unit]
                    canceled <- Deferred[IO, Unit]
                    _ <- target
                      .requestUploader(channel, Protocol.Http1) { completion =>
                        val message =
                          new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
                        message.headers().set(HttpHeaderNames.HOST, "wire.test")
                        HttpUtil.setContentLength(message, 0L)
                        target.awaitFuture(channel.writeAndFlush(message, completion)).void *>
                          written.complete(()).void *> IO
                            .never[Unit]
                            .onCancel(canceled.complete(()).void)
                      }
                      .use { _ =>
                        // The peer's complete response precedes uploader completion by construction.
                        written.get *> IO.fromFuture(IO.pure(responses(index).future))
                      }
                    _ <- canceled.get
                    _ <- IO(assert(channel.isActive))
                  } yield ()
                }
            }
          }
      }
      .timeout(5.seconds)
  }

  private def framingFields(head: ByteVector): Vector[(String, String)] =
    head.decodeAscii.fold(throw _, identity).split("\r\n").toVector.drop(1).flatMap { line =>
      val colon = line.indexOf(':')
      val name = line.take(colon).toLowerCase(java.util.Locale.ROOT)
      if (name == "content-length" || name == "transfer-encoding")
        Vector(name -> line.drop(colon + 1).trim)
      else Vector.empty
    }

  Vector(
    "empty transfer coding" -> Vector("tRaNsFeR-EnCoDiNg" -> ""),
    "opaque transfer coding" -> Vector("Transfer-Encoding" -> "gzip"),
    "chunked transfer coding" -> Vector("Transfer-Encoding" -> "chunked"),
    "content length" -> Vector("Content-Length" -> "03"),
    "absent framing" -> Vector.empty[(String, String)],
  ).foreach { case (label, headers) =>
    Vector(false, true).foreach { streamed =>
      test(
        s"HTTP/1 client framing defaults preserve supplied fields: $label, streamed: $streamed"
      ) {
        val body = Http1Wire.ascii("abc")
        val chunked = headers.exists(_._2 == "chunked") || (headers.isEmpty && streamed)
        val expectedHeaders =
          if (headers.nonEmpty)
            headers.map { case (name, value) => name.toLowerCase(java.util.Locale.ROOT) -> value }
          else if (streamed) Vector("transfer-encoding" -> "chunked")
          else Vector("content-length" -> "3")
        val expectedBody = if (chunked) Http1Wire.ascii("3\r\nabc\r\n0\r\n\r\n") else body
        // Nonfinal request TE is deliberately invalid protocol input. This scripted peer
        // observes adapter fidelity only; no recipient acceptance or coding support is claimed.
        Transcript
          .create[IO]()
          .flatMap { transcript =>
            CaseContext(target, transcript).client(Protocol.Http1) { client =>
              val request = RequestData(method = "POST", headers = headers, body = body)
              val exchange =
                if (streamed)
                  client.exchangeStream(request, _ => Stream.chunk(Chunk.byteVector(body)))
                else client.exchange(request)
              exchange.use(response =>
                IO(assertEquals(response.status, 200)) *> response.body.compile.drain
              )
            } { peer =>
              for {
                head <- peer.readUntil(Http1Wire.ascii("\r\n\r\n"))
                _ <- IO(assertEquals(framingFields(head), expectedHeaders))
                bytes <- peer.readExact(expectedBody.size.toInt)
                _ <- IO(assertEquals(bytes, expectedBody))
                _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
              } yield ()
            }
          }
          .timeout(5.seconds)
      }
    }

    test(s"HTTP/1 server framing defaults preserve supplied fields: $label") {
      val body = Http1Wire.ascii("abc")
      val expectedHeaders =
        if (headers.nonEmpty)
          headers.map { case (name, value) => name.toLowerCase(java.util.Locale.ROOT) -> value }
        else Vector("content-length" -> "3")
      val expectedBody =
        if (headers.exists(_._2 == "chunked")) Http1Wire.ascii("3\r\nabc\r\n0\r\n\r\n")
        else body
      // Observe raw bytes, including empty TE and opaque coding, without decoding them.
      Transcript
        .create[IO]()
        .flatMap { transcript =>
          CaseContext(target, transcript)
            .server(
              Protocol.Http1,
              ServerScenario.Reply(headers = headers :+ ("Connection" -> "close"), body = body),
            )
            .use { case (peer, _) =>
              for {
                _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n")
                head <- peer.readUntil(Http1Wire.ascii("\r\n\r\n"))
                _ <- IO(assertEquals(framingFields(head), expectedHeaders))
                bytes <- peer.readToEnd(65536)
                _ <- IO(assertEquals(bytes, expectedBody))
              } yield ()
            }
        }
        .timeout(5.seconds)
    }
  }

  test("HTTP/1 prompt native replies permit repeated ordinary GET exchanges") {
    target
      .server(Protocol.Http1, ServerScenario.Reply())
      .use { server =>
        target.client(Protocol.Http1, server.address).use { client =>
          client
            .exchange(RequestData())
            .use { response =>
              IO(assertEquals(response.status, 200)) *> response.body.compile.drain
            }
            .replicateA_(100)
        }
      }
      .timeout(10.seconds)
  }

  test("HTTP/1 uses one TCP connection for sequential exchanges and exposes trailers") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).client(Protocol.Http1) { client =>
          def exchange = client.exchange(RequestData()).use { response =>
            for {
              result <- response.body.compile.to(Chunk)
              trailers <- response.trailers
            } yield {
              assertEquals(result.toByteVector, Http1Wire.ascii("a"))
              assertEquals(response.headers.filter(_._1 == "x-repeat"), duplicateHeaders)
              assertEquals(trailers, Vector("x-trailer" -> "yes"))
            }
          }
          exchange *> exchange
        } { peer =>
          val response =
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n" +
              "x-repeat: first\r\nx-repeat: second\r\n\r\n" +
              "1\r\na\r\n0\r\nx-trailer: yes\r\n\r\n"
          (Http1Wire.readRequest(peer) *> peer.writeAscii(response)).replicateA_(2)
        }
      }
      .timeout(10.seconds)
  }

  test("HTTP/1 premature close fails the body instead of completing it") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).client(Protocol.Http1) { client =>
          client.exchange(RequestData()).use { response =>
            response.body.compile.drain.attempt.map(result => assert(result.isLeft))
          }
        } { peer =>
          Http1Wire.readRequest(peer) *>
            peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nshort") *>
            peer.endOfOutput
        }
      }
      .timeout(10.seconds)
  }

  test("HTTP/1 reports a native request decoder failure as 400") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).server(Protocol.Http1).use { case (peer, _) =>
          peer.writeAscii("GET / HTTP/1.1\r\nbad header: value\r\n\r\n") *>
            Http1Wire.readResponse(peer).map(response => assertEquals(response.status, 400))
        }
      }
      .timeout(10.seconds)
  }

  Vector("1.0", "1.1").foreach { version =>
    test(s"HTTP/$version streamed replies use the request version's native framing") {
      Transcript
        .create[IO]()
        .flatMap { transcript =>
          CaseContext(target, transcript)
            .server(
              Protocol.Http1,
              ServerScenario.ReplyChunks(Vector(Http1Wire.ascii("a"), Http1Wire.ascii("bc"))),
            )
            .use { case (peer, _) =>
              peer.writeAscii(
                s"GET / HTTP/$version\r\nHost: wire.test\r\nConnection: close\r\n\r\n"
              ) *>
                peer.readToEnd(65536).map { bytes =>
                  val wire = bytes.decodeAscii.fold(throw _, identity)
                  assert(wire.startsWith(s"HTTP/$version 200"), wire)
                  if (version == "1.0") {
                    assert(
                      !wire.toLowerCase(java.util.Locale.ROOT).contains("transfer-encoding:"),
                      wire,
                    )
                    assert(wire.endsWith("\r\n\r\nabc"), wire)
                  } else {
                    assert(
                      wire
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("transfer-encoding: chunked"),
                      wire,
                    )
                    assert(wire.endsWith("1\r\na\r\n2\r\nbc\r\n0\r\n\r\n"), wire)
                  }
                }
            }
        }
        .timeout(5.seconds)
    }
  }

  test("HTTP/1 pipelined replies retain each request version") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript)
          .server(Protocol.Http1, ServerScenario.ReplyChunks(Vector(Http1Wire.ascii("abc"))))
          .use { case (peer, _) =>
            peer.writeAscii(
              "GET /one HTTP/1.1\r\nHost: wire.test\r\n\r\n" +
                "GET /two HTTP/1.0\r\n\r\n"
            ) *> peer.readToEnd(65536).map { bytes =>
              val wire = bytes.decodeAscii.fold(throw _, identity)
              assert(wire.startsWith("HTTP/1.1 200"), wire)
              assert(wire.contains("3\r\nabc\r\n0\r\n\r\nHTTP/1.0 200"), wire)
              assert(wire.endsWith("\r\n\r\nabc"), wire)
            }
          }
      }
      .timeout(5.seconds)
  }

  test("HTTP/1.0 EchoBody retains the request version through request completion") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).server(Protocol.Http1, ServerScenario.EchoBody).use {
          case (peer, _) =>
            peer.writeAscii("POST / HTTP/1.0\r\nContent-Length: 3\r\n\r\nabc") *>
              peer.readToEnd(65536).map { bytes =>
                val wire = bytes.decodeAscii.fold(throw _, identity)
                assert(wire.startsWith("HTTP/1.0 200"), wire)
                assert(wire.toLowerCase(java.util.Locale.ROOT).contains("content-length: 3"), wire)
                assert(wire.endsWith("\r\n\r\nabc"), wire)
              }
        }
      }
      .timeout(5.seconds)
  }

  Vector(
    ("complete DATA", true, false, 0L),
    ("complete HEADERS", true, true, 0L),
    ("incomplete DATA", false, false, 0L),
    ("complete DATA with CANCEL", true, false, 8L),
    ("incomplete DATA with CANCEL", false, false, 8L),
  ).foreach { case (label, complete, headersOnly, errorCode) =>
    test(s"HTTP/2 native buffered response before reset: $label") {
      import io.netty.buffer.Unpooled
      import io.netty.channel._
      import io.netty.channel.embedded.EmbeddedChannel
      import io.netty.handler.codec.http2._

      IO {
        val receiver = new NettyTarget.ResponseReceiver(Protocol.Http2)
        val parent = new EmbeddedChannel(
          Http2FrameCodecBuilder.forClient().build(),
          new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()),
        )
        try {
          parent.writeInbound(
            Unpooled.wrappedBuffer(Http2Wire.encode(Http2Wire.settings()).toArray)
          )
          val opened = new Http2StreamChannelBootstrap(parent)
            .option[java.lang.Boolean](ChannelOption.AUTO_READ, false)
            .handler(receiver)
            .open()
          parent.runPendingTasks()
          val child = opened.getNow
          child.writeAndFlush(
            new DefaultHttp2HeadersFrame(
              new DefaultHttp2Headers()
                .method("POST")
                .scheme("http")
                .authority("wire.test")
                .path("/"),
              false,
            )
          )
          val streamId = child.stream().id()
          // No child read is requested: native HEADERS and DATA must queue, while
          // MultiplexHandler delivers the RST_STREAM user event immediately.
          val response = Http2Wire.encode(
            Http2Wire.headers(
              streamId,
              Vector(":status" -> "200"),
              endStream = headersOnly,
            )
          ) ++ (if (headersOnly) ByteVector.empty
                else
                  Http2Wire.encode(
                    Http2Wire.data(streamId, Http1Wire.ascii("abc"), endStream = complete)
                  )) ++
            Http2Wire.encode(
              Http2Wire.Frame(Http2Wire.RstStream, 0, streamId, Http2Wire.uint32(errorCode))
            )
          parent.writeInbound(Unpooled.wrappedBuffer(response.toArray))
          child.read()
          parent.runPendingTasks()
          val results =
            scala.collection.mutable.ArrayBuffer.empty[Either[Throwable, Option[Chunk[Byte]]]]
          receiver.demand(new Object, results += _)
          if (results.lastOption.exists(_.exists(_.isDefined)))
            receiver.demand(new Object, results += _)
          assert(results.nonEmpty, "reset left response body waiting")
          if (complete && errorCode == 0L) {
            assertEquals(receiver.head.future.value.map(_.map(_._1)), Some(scala.util.Success(200)))
            assertEquals(receiver.trailers.future.value, Some(scala.util.Success(Vector.empty)))
            val body = results
              .collect { case Right(Some(chunk)) => chunk.toByteVector }
              .foldLeft(ByteVector.empty)(_ ++ _)
            assertEquals(body, if (headersOnly) ByteVector.empty else Http1Wire.ascii("abc"))
            assertEquals(results.last, Right(None))
          } else {
            assert(
              results.last.left.exists(error =>
                error.isInstanceOf[
                  java.io.IOException
                ] && error.getMessage == s"HTTP/2 stream reset: $errorCode"
              ),
              s"expected native reset failure, got $results",
            )
          }
        } finally {
          parent.finishAndReleaseAll()
          ()
        }
      }
    }
  }

  Vector("final HEADERS", "EOF", "END_STREAM DATA and EOF").foreach { completion =>
    test(s"HTTP/2 response acquisition completes after 103 followed by $completion") {
      Transcript
        .create[IO]()
        .flatMap { transcript =>
          CaseContext(target, transcript).client(Protocol.Http2) { client =>
            client
              .exchange(RequestData())
              .use { response =>
                (response.body.compile.to(Chunk), response.trailers).mapN { (body, trailers) =>
                  (response.status, body.toByteVector, trailers)
                }
              }
              .attempt
              .flatMap { result =>
                IO {
                  if (completion == "final HEADERS")
                    assertEquals(result, Right((200, ByteVector.empty, Vector.empty)))
                  else
                    assert(
                      result.left.exists(_.isInstanceOf[java.io.EOFException]),
                      s"expected native EOF before a final response head, got $result",
                    )
                }
              }
          } { peer =>
            for {
              _ <- Http2Wire.serverHandshake(peer)
              request <- Http2Wire.readUntil(peer, fromServer = false)(
                _.frameType == Http2Wire.Headers
              )
              // Complete the upload before responding so cancellation cannot mask the head failure.
              _ <-
                if (request.hasFlag(Http2Wire.EndStream)) IO.unit
                else
                  Http2Wire
                    .readUntil(peer, fromServer = false)(frame =>
                      frame.streamId == request.streamId && frame.hasFlag(Http2Wire.EndStream)
                    )
                    .void
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "103"), endStream = false),
              )
              _ <- completion match {
                case "final HEADERS" =>
                  Http2Wire.writeFrame(
                    peer,
                    Http2Wire.headers(
                      request.streamId,
                      Vector(":status" -> "200"),
                      endStream = true,
                    ),
                  )
                case "END_STREAM DATA and EOF" =>
                  Http2Wire.writeFrame(
                    peer,
                    Http2Wire.data(request.streamId, ByteVector.empty, endStream = true),
                  )
                case _ => IO.unit
              }
              _ <- peer.endOfOutput
            } yield ()
          }
        }
        // A timeout fails this liveness regression; it is never evidence of protocol rejection.
        .timeout(5.seconds)
    }
  }

  test("HTTP/2 supports concurrent streams on one driver") {
    target
      .server(Protocol.Http2, ServerScenario.EchoBody)
      .use { server =>
        target.client(Protocol.Http2, server.address).use { client =>
          Vector("/first", "/second").parTraverse_ { path =>
            client.exchangeStream(RequestData("POST", path), _ => upload).use { response =>
              response.body.compile.to(Chunk).map(result => assertEquals(result.toByteVector, body))
            }
          }
        }
      }
      .timeout(10.seconds)
  }
}
