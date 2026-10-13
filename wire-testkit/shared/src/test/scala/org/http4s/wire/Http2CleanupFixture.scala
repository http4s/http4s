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
import fs2.Stream

private[wire] object Http2CleanupFixture extends munit.Assertions {
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  // In-memory matrices run under TestControl: this window advances only after all
  // runnable work drains. Socket regressions retain the real scheduling window.
  def assertHeldClientObservation(
      run: IO[Unit],
      consumed: Deferred[IO, Unit],
      releases: Ref[IO, Int],
  ): IO[Unit] = {
    import scala.concurrent.duration._
    run.background
      .use { joined =>
        val done = joined.flatMap {
          case cats.effect.Outcome.Succeeded(value) => value
          case cats.effect.Outcome.Errored(error) => IO.raiseError[Unit](error)
          case cats.effect.Outcome.Canceled() => IO.canceled *> IO.never[Unit]
        }
        IO.race(consumed.get, done).flatMap {
          case Right(_) =>
            IO.raiseError(new AssertionError("case completed without a wire verdict"))
          case Left(_) =>
            IO.race(
              done,
              IO.sleep(200.millis) *> releases.get.flatMap(value => IO(assertEquals(value, 0))),
            ).flatMap {
              case Left(_) =>
                IO.raiseError(new AssertionError("response cleanup supplied the wire verdict"))
              case Right(_) => IO.unit
            }
        }
      }
      .timeout(5.seconds)
  }

  def cleanupLifetimeProbe(
      id: String,
      closeOnRelease: Boolean,
      inMemory: Boolean = true,
  ): IO[Unit] = {
    import Http2Wire._
    for {
      released <- Ref.of[IO, Int](0)
      failed <- Ref.of[IO, Boolean](false)
      faultSeen <- Deferred[IO, Unit]
      driver = (peer: RawPeer[IO]) =>
        for {
          begun <- Resource.eval(Ref.of[IO, Boolean](false))
          next <- Resource.eval(Ref.of[IO, Int](1))
          dec <- Resource.eval(HpackWire.decoder[IO]())
        } yield new ClientDriver[IO] {
          def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = response(r, false)
          def exchangeStream(
              r: RequestData,
              b: FixtureCleanup[IO] => Stream[IO, Byte],
          ): Resource[IO, ResponseData[IO]] = response(r, true)
          def response(r: RequestData, upload: Boolean): Resource[IO, ResponseData[IO]] = Resource
            .eval(for {
              first <- begun.getAndSet(true)
              _ <-
                if (!first && id.endsWith("reject-preface-first-ping"))
                  peer.write(clientPreface ++ encode(Http2Wire.settings())) *> readFrame(peer).void
                else if (!first) clientHandshake(peer)
                else IO.unit
              stream <- next.getAndUpdate(_ + 2)
              _ <-
                if (id.endsWith("reject-preface-first-ping")) IO.unit
                else
                  writeFrame(
                    peer,
                    headers(
                      stream,
                      Vector(
                        ":method" -> r.method,
                        ":scheme" -> "http",
                        ":authority" -> "wire.test",
                        ":path" -> r.path,
                      ),
                      !upload,
                    ),
                  )
              result <-
                if (id.contains("hpack")) {
                  for {
                    h <- readUntil(peer, fromServer = true)(_.frameType == Headers)
                    fields <- dec.decode(h.payload).attempt
                  } yield fields
                } else if (id.endsWith("reject-preface-first-ping"))
                  IO.pure(
                    Left(new RuntimeException("native stream rejection")): Either[Throwable, Vector[
                      (String, String)
                    ]]
                  )
                else {
                  def fault: IO[Unit] = readFrame(peer).flatMap { f =>
                    val matched =
                      if (id.endsWith("settings-stream-window-overflow"))
                        f.frameType == Settings && !f.hasFlag(Ack)
                      else if (id.endsWith("idle-data")) f.frameType == Data
                      else if (id.endsWith("unpromised-server-headers")) f.frameType == Headers
                      else if (id.contains("reject-priority-length")) f.frameType == Priority
                      else f.frameType == WindowUpdate
                    if (matched && id.endsWith(".stream-window-overflow")) readFrame(peer).void
                    else if (matched) IO.unit
                    else fault
                  }
                  fault.as(
                    Left(new RuntimeException("native stream rejection")): Either[Throwable, Vector[
                      (String, String)
                    ]]
                  )
                }
              _ <- if (result.isLeft) failed.set(true) *> faultSeen.complete(()).void else IO.unit
            } yield (result, stream))
            .flatMap { case (result, _) =>
              Resource.make(
                IO.pure(
                  ResponseData[IO](
                    200,
                    result.getOrElse(Vector.empty).filterNot(_._1.startsWith(":")),
                    if (result.isLeft)
                      Stream.raiseError[IO](new RuntimeException("native stream rejection"))
                    else Stream.empty,
                    IO.pure(Vector.empty),
                  )
                )
              ) { _ =>
                if (result.isLeft)
                  released.update(_ + 1) *> (if (closeOnRelease) peer.endOfOutput else IO.unit)
                else IO.unit
              }
            }
        }
      _ <- assertHeldClientObservation(
        ScriptedClient.run(Catalog.all.find(_.id == id).get, inMemory)(driver),
        faultSeen,
        released,
      )
      _ <- failed.get.flatMap(value => IO(assertEquals(value, true)))
      _ <- released.get.flatMap(value => IO(assertEquals(value, 1)))
    } yield ()
  }

  def messageCleanupLifetimeProbe(
      id: String,
      n: Int,
      close: Boolean,
      enabled: Boolean,
      apiSuccess: Boolean,
      headerLimit: Option[Long] = None,
      inMemory: Boolean = true,
  ): IO[Unit] = {
    import Http2Wire._
    for {
      releases <- Ref.of[IO, Int](0)
      bodySeen <- Deferred[IO, Unit]
      driver = (peer: RawPeer[IO]) =>
        Resource.pure[IO, ClientDriver[IO]](new ClientDriver[IO] {
          def exchange(r: RequestData): Resource[IO, ResponseData[IO]] = for {
            _ <- Resource.eval(for {
              _ <- peer.write(
                clientPreface ++ encode(
                  Http2Wire.settings(
                    Vector(2 -> (if (enabled) 1L else 0L)) ++ headerLimit.toVector.map(6 -> _)
                  )
                )
              )
              _ <- readFrame(peer)
              ack <- readFrame(peer)
              _ <- IO(assert(ack.frameType == Settings && ack.hasFlag(Ack)))
              _ <- writeFrame(peer, Http2Wire.settings(ack = true))
              _ <- writeFrame(
                peer,
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
              frames <- Vector.fill(n)(readFrame(peer)).sequence
              _ <- IO(assert(frames.size == n))
            } yield ())
            response <- Resource.make(
              IO.pure(
                ResponseData[IO](
                  200,
                  Vector.empty,
                  Stream.eval(bodySeen.complete(()).void).drain ++ (if (apiSuccess) Stream.empty
                                                                    else
                                                                      Stream.raiseError[IO](
                                                                        new RuntimeException(
                                                                          "native body failure"
                                                                        )
                                                                      )),
                  IO.pure(Vector.empty),
                )
              )
            )(_ => releases.update(_ + 1) *> (if (close) peer.endOfOutput else IO.unit))
          } yield response
          def exchangeStream(
              r: RequestData,
              b: FixtureCleanup[IO] => Stream[IO, Byte],
          ): Resource[IO, ResponseData[IO]] = exchange(r)
        })
      _ <- assertHeldClientObservation(
        ScriptedClient.run(Http2MessageCases.all.find(_.id == id).get, inMemory)(driver),
        bodySeen,
        releases,
      )
      _ <- releases.get.flatMap(value => IO(assertEquals(value, 1)))
    } yield ()
  }

}
