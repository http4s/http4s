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
import fs2.Chunk
import fs2.Stream
import fs2.io.net.Network
import fs2.io.net.Socket

import scala.concurrent.duration._

class CaseContextSuite extends RuntimeDiagnostics.Suite {
  private val driver = new ClientDriver[IO] {
    def exchange(request: RequestData): Resource[IO, ResponseData[IO]] =
      Resource.eval(IO.raiseError(new UnsupportedOperationException))
    def exchangeStream(
        request: RequestData,
        body: FixtureCleanup[IO] => Stream[IO, Byte],
    ): Resource[IO, ResponseData[IO]] = exchange(request)
  }

  private def run[A](clientResource: Socket[IO] => Resource[IO, ClientDriver[IO]])(
      exercise: ClientDriver[IO] => IO[A]
  )(serve: RawPeer[IO] => IO[Unit]): IO[A] = {
    val target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "coordination-fixture"
      val version = "local"
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException))
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] = Network[IO].connect(address).flatMap(clientResource)
    }
    Transcript
      .create[IO]()
      .flatMap(t => CaseContext(target, t).client(Protocol.Http1)(exercise)(serve))
      .timeout(5.seconds)
  }

  test("exercise failure is selected before resource release can cause peer EOF") {
    val primary = new WireObservationLimit("exercise")
    val secondary = new IllegalStateException("peer EOF")
    for {
      serving <- Deferred[IO, Unit]
      peerDone <- Deferred[IO, Unit]
      canceled <- Ref.of[IO, Boolean](false)
      releasedAfterCancel <- Ref.of[IO, Boolean](false)
      result <- run(socket =>
        Resource.make(IO.pure(driver)) { _ =>
          canceled.get.flatMap(releasedAfterCancel.set) *> socket.endOfOutput *> peerDone.get
        }
      )(_ => serving.get *> IO.raiseError[Unit](primary)) { peer =>
        (serving.complete(()) *> peer.awaitInput *> peerDone.complete(()) *>
          IO.raiseError[Unit](secondary))
          .onCancel(canceled.set(true) *> peerDone.complete(()).void)
      }.attempt
      _ = assert(result.swap.exists(_ eq primary))
      _ <- releasedAfterCancel.get.assertEquals(true)
    } yield ()
  }

  test("acquisition and scripted handshake run concurrently and release once") {
    for {
      released <- Ref.of[IO, Int](0)
      result <- run(socket =>
        Resource.make(socket.read(1).as(driver))(_ => released.update(_ + 1))
      )(_ => IO.pure(42))(_.writeAscii("x"))
      _ = assertEquals(result, 42)
      _ <- released.get.assertEquals(1)
    } yield ()
  }

  test("peer failure cancels blocked acquisition and releases partial resources") {
    val primary = new IllegalStateException("script")
    for {
      acquiring <- Deferred[IO, Unit]
      canceled <- Ref.of[IO, Boolean](false)
      released <- Ref.of[IO, Int](0)
      result <- run(_ =>
        Resource
          .make(IO.unit)(_ => released.update(_ + 1))
          .evalMap(_ =>
            acquiring.complete(()) *> IO.never[ClientDriver[IO]].onCancel(canceled.set(true))
          )
      )(_ => IO.unit)(_ => acquiring.get *> IO.raiseError(primary)).attempt
      _ = assert(result.swap.exists(_ eq primary))
      _ <- canceled.get.assertEquals(true)
      _ <- released.get.assertEquals(1)
    } yield ()
  }

  test("acquisition failure cancels the scripted peer") {
    val primary = new IllegalStateException("acquire")
    for {
      serving <- Deferred[IO, Unit]
      canceled <- Ref.of[IO, Boolean](false)
      result <- run(_ => Resource.eval(serving.get *> IO.raiseError[ClientDriver[IO]](primary)))(
        _ => IO.unit
      )(_ => (serving.complete(()) *> IO.never[Unit]).onCancel(canceled.set(true))).attempt
      _ = assert(result.swap.exists(_ eq primary))
      _ <- canceled.get.assertEquals(true)
    } yield ()
  }

  for (primaryFromPeer <- Vector(false, true))
    test(s"primary error retains independent target release failure (peer=$primaryFromPeer)") {
      val primary = new IllegalStateException("primary")
      val cleanup = new IllegalStateException("cleanup")
      for {
        exercising <- Deferred[IO, Unit]
        result <- run(_ => Resource.make(IO.pure(driver))(_ => IO.raiseError(cleanup)))(_ =>
          exercising.complete(()) *> (if (primaryFromPeer) IO.never[Unit]
                                      else IO.raiseError(primary))
        )(_ => if (primaryFromPeer) exercising.get *> IO.raiseError(primary) else IO.never).attempt
        _ = assert(result.swap.exists(_ eq primary))
        _ = assert(primary.getSuppressed.exists(error => error.getCause eq cleanup))
      } yield ()
    }

  test("successful exercise still reports target release failure") {
    val cleanup = new IllegalStateException("cleanup")
    run(_ => Resource.make(IO.pure(driver))(_ => IO.raiseError(cleanup)))(_ => IO.unit)(_ =>
      IO.unit
    )
      .intercept[AdapterFailure]
      .map(error => assert(error.getCause eq cleanup))
  }

  for (duringAcquisition <- Vector(false, true))
    test(s"external cancellation releases target and cancels peer (acquiring=$duringAcquisition)") {
      for {
        started <- Deferred[IO, Unit]
        serving <- Deferred[IO, Unit]
        released <- Ref.of[IO, Int](0)
        peerCanceled <- Ref.of[IO, Boolean](false)
        operation = run(_ =>
          Resource.make(IO.pure(driver))(_ => released.update(_ + 1)).evalMap { driver =>
            if (duringAcquisition) started.complete(()) *> IO.never[ClientDriver[IO]]
            else IO.pure(driver)
          }
        )(_ => started.complete(()) *> IO.never[Unit])(_ =>
          (serving.complete(()) *> IO.never[Unit]).onCancel(peerCanceled.set(true))
        )
        _ <- operation.background.use { _ =>
          started.get *> serving.get *> IO.unit
        }
        _ <- released.get.assertEquals(1)
        _ <- peerCanceled.get.assertEquals(true)
      } yield ()
    }

  test("script finalizer failure cancels exercise and remains the primary error") {
    val primary = new IllegalStateException("script finalizer")
    for {
      exercising <- Deferred[IO, Unit]
      canceled <- Ref.of[IO, Boolean](false)
      result <- run(_ => Resource.pure(driver))(_ =>
        (exercising.complete(()) *> IO.never[Unit]).onCancel(canceled.set(true))
      )(_ => exercising.get.guarantee(IO.raiseError(primary))).attempt
      _ = assert(result.swap.exists(_ eq primary))
      _ <- canceled.get.assertEquals(true)
    } yield ()
  }

  test("external cancellation retains monitored fixture release failure") {
    val cleanup = new IllegalStateException("canceled fixture release")
    for {
      exercising <- Deferred[IO, Unit]
      observed <- Deferred[IO, AdapterFailure]
      operation = run(_ =>
        AdapterFailure.make(
          IO.pure(driver),
          Some((error: AdapterFailure) => observed.complete(error).void),
        )(_ => IO.raiseError(cleanup))
      )(_ => exercising.complete(()) *> IO.never[Unit])(_ => IO.never)
      _ <- Resource.make(operation.start)(_.cancel).use { fiber =>
        exercising.get *> fiber.cancel *> fiber.join.flatMap {
          case Outcome.Canceled() => IO.unit
          case other => IO(fail(s"expected cancellation, got $other"))
        }
      }
      error <- observed.get
      _ = assert(error.getCause eq cleanup)
    } yield ()
  }

  test("peer failure racing completed acquisition never loses resource ownership") {
    IO.defer {
      val primary = new IllegalStateException("script")
      for {
        acquired <- Deferred[IO, Unit]
        released <- Ref.of[IO, Int](0)
        result <- run(_ =>
          Resource.make(acquired.complete(()).as(driver))(_ => released.update(_ + 1))
        )(_ => IO.never[Unit])(_ => acquired.get *> IO.raiseError(primary)).attempt
        _ = assert(result.swap.exists(_ eq primary))
        _ <- released.get.assertEquals(1)
      } yield ()
    }.replicateA_(100)
  }

  test("script failure cancels a client awaiting socket input before peer teardown") {
    IO.defer {
      val primary = new WireObservationLimit("script")
      val eof = new IllegalStateException("client EOF")
      for {
        reading <- Deferred[IO, Unit]
        canceled <- Ref.of[IO, Boolean](false)
        result <- run(socket =>
          Resource.pure(new ClientDriver[IO] {
            def exchange(request: RequestData): Resource[IO, ResponseData[IO]] =
              Resource.eval(
                (reading.complete(()) *> socket.read(1) *> IO.raiseError[ResponseData[IO]](eof))
                  .onCancel(canceled.set(true))
              )
            def exchangeStream(
                request: RequestData,
                body: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] = exchange(request)
          })
        )(_.exchange(RequestData()).use(_ => IO.unit))(_ =>
          reading.get *> IO.raiseError(primary)
        ).attempt
        _ = assert(result.swap.exists(_ eq primary))
        _ <- canceled.get.assertEquals(true)
      } yield ()
    }.replicateA_(100)
  }

  test("scripted socket release cannot replace its failure with client EOF") {
    val primary = new WireObservationLimit("script")
    val eof = new IllegalStateException("client EOF")
    Network[IO]
      .bind(SocketAddress(ip"127.0.0.1", port"0"))
      .use { listener =>
        for {
          reading <- Deferred[IO, Unit]
          exerciseDone <- Deferred[IO, Unit]
          canceled <- Ref.of[IO, Boolean](false)
          canceledBeforeRelease <- Ref.of[IO, Boolean](false)
          result <- CaseContext
            .withClientAndPeer(
              Network[IO].connect(listener.address.asIpUnsafe),
              listener.accept.head.compile.resource.lastOrError.flatMap { socket =>
                Resource.make(IO.pure(socket)) { _ =>
                  canceled.get.flatMap(canceledBeforeRelease.set) *>
                    socket.endOfOutput *> exerciseDone.get
                }
              },
            )(socket =>
              (reading.complete(()) *> socket.read(1) *> exerciseDone.complete(()) *>
                IO.raiseError[Unit](eof))
                .onCancel(canceled.set(true) *> exerciseDone.complete(()).void)
            )(_ => reading.get *> IO.raiseError(primary))
            .attempt
          _ = assert(result.swap.exists(_ eq primary))
          _ <- canceledBeforeRelease.get.assertEquals(true)
        } yield ()
      }
      .timeout(5.seconds)
  }

  for (primaryFromPeer <- Vector(false, true))
    test(s"primary retains both independent resource release failures (peer=$primaryFromPeer)") {
      val primary = new WireObservationLimit("primary")
      val clientCleanup = new IllegalStateException("client release")
      val peerCleanup = new IllegalStateException("peer release")
      for {
        exercising <- Deferred[IO, Unit]
        serving <- Deferred[IO, Unit]
        result <- CaseContext
          .withClientAndPeer(
            Resource.make(IO.unit)(_ => IO.raiseError(clientCleanup)),
            Resource.make(IO.unit)(_ => IO.raiseError(peerCleanup)),
          )(_ =>
            exercising.complete(()) *> serving.get *>
              (if (primaryFromPeer) IO.never[Unit] else IO.raiseError(primary))
          )(_ =>
            serving.complete(()) *> exercising.get *>
              (if (primaryFromPeer) IO.raiseError[Unit](primary) else IO.never[Unit])
          )
          .attempt
        _ = assert(result.swap.exists(_ eq primary))
        _ = assert(primary.getSuppressed.exists(_.getCause eq clientCleanup))
        _ = assert(primary.getSuppressed.exists(_.getCause eq peerCleanup))
      } yield ()
    }

  test("successful operations retain both independent resource release failures") {
    val clientCleanup = new IllegalStateException("client release")
    val peerCleanup = new IllegalStateException("peer release")
    CaseContext
      .withClientAndPeer(
        Resource.make(IO.unit)(_ => IO.raiseError(clientCleanup)),
        Resource.make(IO.unit)(_ => IO.raiseError(peerCleanup)),
      )(_ => IO.unit)(_ => IO.unit)
      .intercept[AdapterFailure]
      .map { error =>
        val causes = (error +: error.getSuppressed.toVector).map(_.getCause)
        assert(causes.exists(_ eq clientCleanup))
        assert(causes.exists(_ eq peerCleanup))
      }
  }

  test("exercise failure cancels peer acquisition and releases its partial resource") {
    val primary = new IllegalStateException("exercise")
    for {
      acquiring <- Deferred[IO, Unit]
      canceled <- Ref.of[IO, Boolean](false)
      released <- Ref.of[IO, Int](0)
      result <- CaseContext
        .withClientAndPeer(
          Resource.pure[IO, Unit](()),
          Resource
            .make(IO.unit)(_ => released.update(_ + 1))
            .evalMap(_ => acquiring.complete(()) *> IO.never[Unit].onCancel(canceled.set(true))),
        )(_ => acquiring.get *> IO.raiseError[Unit](primary))(_ => IO.unit)
        .attempt
      _ = assert(result.swap.exists(_ eq primary))
      _ <- canceled.get.assertEquals(true)
      _ <- released.get.assertEquals(1)
    } yield ()
  }

  test("exercise failure racing completed peer acquisition never loses ownership") {
    IO.defer {
      val primary = new IllegalStateException("exercise")
      for {
        acquired <- Deferred[IO, Unit]
        released <- Ref.of[IO, Int](0)
        result <- CaseContext
          .withClientAndPeer(
            Resource.pure[IO, Unit](()),
            Resource.make(acquired.complete(()))(_ => released.update(_ + 1)),
          )(_ => acquired.get *> IO.raiseError[Unit](primary))(_ => IO.never)
          .attempt
        _ = assert(result.swap.exists(_ eq primary))
        _ <- released.get.assertEquals(1)
      } yield ()
    }.replicateA_(100)
  }

  test("simultaneous exercise and script failures preserve either original race winner") {
    IO.defer {
      val exerciseError = new IllegalStateException("exercise")
      val scriptError = new WireObservationLimit("script")
      for {
        exercising <- Deferred[IO, Unit]
        serving <- Deferred[IO, Unit]
        result <- CaseContext
          .withClientAndPeer(
            Resource.pure[IO, Unit](()),
            Resource.pure[IO, Unit](()),
          )(_ => exercising.complete(()) *> serving.get *> IO.raiseError[Unit](exerciseError))(_ =>
            serving.complete(()) *> exercising.get *> IO.raiseError[Unit](scriptError)
          )
          .attempt
        _ = assert(result.swap.exists(error => (error eq exerciseError) || (error eq scriptError)))
      } yield ()
    }.replicateA_(100)
  }

  test("eager-server-settings preserves the lawful zero-window observation limit") {
    val selected =
      InteractionCases.all.find(_.id == "h2.client.interaction-eager-server-settings").get
    val target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "zero-window-client"
      val version = "local"
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException))
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] = Network[IO].connect(address).map { socket =>
        new ClientDriver[IO] {
          def exchange(request: RequestData): Resource[IO, ResponseData[IO]] = Resource.eval {
            def read: IO[ResponseData[IO]] = socket.read(8192).flatMap {
              case None => IO.raiseError(new IllegalStateException("client EOF"))
              case Some(_) => IO.defer(read)
            }
            socket.write(
              Chunk.byteVector(
                Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings(Vector(4 -> 0L)))
              )
            ) *> read
          }
          def exchangeStream(
              request: RequestData,
              body: FixtureCleanup[IO] => Stream[IO, Byte],
          ): Resource[IO, ResponseData[IO]] = exchange(request)
        }
      }
    }
    IO.defer {
      Transcript
        .create[IO]()
        .flatMap(t => selected.run(CaseContext(target, t)))
        .timeout(5.seconds)
        .intercept[WireObservationLimit]
        .void
    }.replicateA_(100)
  }
}
