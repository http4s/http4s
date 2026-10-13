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

import cats.ApplicativeThrow
import cats.effect.IO
import cats.effect.Deferred
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import munit.Location

final case class WireCase(
    id: String,
    protocol: Protocol,
    role: Role,
    references: Vector[Rfc],
    run: CaseContext => IO[Unit],
    location: Location,
)

object WireCase {
  def apply(id: String, protocol: Protocol, role: Role, rfc: Rfc)(
      run: CaseContext => IO[Unit]
  )(implicit location: Location): WireCase =
    WireCase(id, protocol, role, Vector(rfc), run, location)
}

final class ProtocolFailure(message: String) extends AssertionError(message)

object Check {
  def apply(condition: Boolean, detail: => String): IO[Unit] =
    if (condition) IO.unit else IO.raiseError(new ProtocolFailure(detail))

  def equal[A](actual: A, expected: A): IO[Unit] =
    apply(actual == expected, s"expected: $expected\nobserved: $actual")
}

final case class CaseContext(
    target: WireTarget[IO],
    transcript: Transcript[IO],
    serverConnection: (SocketAddress[IpAddress], Transcript[IO]) => Resource[IO, RawPeer[IO]] =
      (address: SocketAddress[IpAddress], transcript: Transcript[IO]) =>
        WirePeerTransport.connect[IO](address, transcript),
    clientListener: Transcript[IO] => Resource[IO, WirePeerTransport.Listener[IO]] = transcript =>
      WirePeerTransport.bind[IO](transcript),
) {
  def server(
      protocol: Protocol,
      scenario: ServerScenario = ServerScenario.Reply(),
  ): Resource[IO, (RawPeer[IO], ServerHandle[IO])] =
    for {
      server <- target.server(protocol, scenario)
      peer <- serverConnection(server.address, transcript)
    } yield (peer, server)

  /** Keep the target allocated until the script has observed the required wire behavior.
    * An exercise failure still cancels the script; a script failure cancels the held client.
    */
  def clientUntilObserved[A](protocol: Protocol)(
      exercise: ClientDriver[IO] => IO[A]
  )(serve: RawPeer[IO] => IO[Unit]): IO[A] =
    Deferred[IO, Unit].flatMap { observed =>
      client(protocol)(driver => exercise(driver).flatTap(_ => observed.get))(peer =>
        serve(peer) *> observed.complete(()).void
      )
    }

  /** Run a target client concurrently with an independent, scripted server. */
  def client[A](protocol: Protocol)(
      exercise: ClientDriver[IO] => IO[A]
  )(serve: RawPeer[IO] => IO[Unit]): IO[A] =
    clientListener(transcript).use { listener =>
      CaseContext.withClientAndPeer(
        target.client(protocol, listener.address),
        listener.accept,
      )(exercise)(serve)
    }
}

object CaseContext {
  private final case class Held[A](get: IO[A], finish: IO[Unit])

  /** The owner, rather than the parallel operation using its value, releases the
    * resource. Neither side's teardown can manufacture the other side's outcome.
    */
  private def hold[A](
      resource: Resource[IO, A],
      operation: String,
      selected: Deferred[IO, Either[Throwable, Unit]],
      cleanup: Ref[IO, Vector[AdapterFailure]],
  ): Resource[IO, Held[A]] =
    Resource.eval(Deferred[IO, Outcome[IO, Throwable, A]]).flatMap { acquired =>
      val owner = AdapterFailure
        .guardRelease(
          resource,
          operation,
          Some((failure: AdapterFailure) => cleanup.update(_ :+ failure)),
        )
        .use(value => acquired.complete(Outcome.succeeded(IO.pure(value))) *> selected.get.rethrow)
        .guaranteeCase {
          case Outcome.Errored(error) => acquired.complete(Outcome.errored(error)).void
          case Outcome.Canceled() => acquired.complete(Outcome.canceled).void
          case Outcome.Succeeded(_) => IO.unit
        }
        .attempt
      owner.background.map { finished =>
        Held(
          acquired.get.flatMap {
            case Outcome.Succeeded(value) => value
            case Outcome.Errored(error) => IO.raiseError[A](error)
            case Outcome.Canceled() => IO.canceled *> IO.never[A]
          },
          acquired.tryGet.flatMap {
            // Unpublished acquisitions are canceled by the background Resource.
            // That scope also owns a successful acquisition racing this check.
            case None => IO.unit
            case Some(_) => finished.void
          },
        )
      }
    }

  private[wire] def withClientAndPeer[A, B, C](
      client: Resource[IO, A],
      peer: Resource[IO, B],
  )(exercise: A => IO[C])(serve: B => IO[Unit]): IO[C] =
    for {
      completed <- Deferred[IO, Unit]
      selected <- Deferred[IO, Either[Throwable, Unit]]
      cleanup <- Ref.of[IO, Vector[AdapterFailure]](Vector.empty)
      result <- (for {
        clientOwner <- hold(client, "releasing case client", selected, cleanup)
        peerOwner <- hold(peer, "releasing scripted peer", selected, cleanup)
      } yield (clientOwner, peerOwner)).use { case (clientOwner, peerOwner) =>
        val run = clientOwner.get.flatMap(exercise).guarantee(completed.complete(()).void)
        val server = peerOwner.get.flatMap(serve) *> completed.get
        (server, run).parTupled.map(_._2).attempt.flatMap { result =>
          selected.complete(result.void) *>
            (clientOwner.finish, peerOwner.finish).parTupled.as(result)
        }
      }
      failures <- cleanup.get
      value <- result.swap.toOption.orElse(failures.headOption) match {
        case None => result.liftTo[IO]
        case Some(primary) =>
          IO.delay {
            failures.foreach { failure =>
              if ((primary ne failure) && !primary.getSuppressed.exists(_ eq failure))
                primary.addSuppressed(failure)
            }
          } *> IO.raiseError[C](primary)
      }
    } yield value

}

/** A harness retention budget, independent of the peer's advertised maximum frame size.
  * A single frame may be read up to the wire decoder's 24-bit limit, but is not retained
  * in the observation collection until this cumulative reservation succeeds.
  */
private[wire] object Http2ObservationBudget {
  val MaxBytes: Long = 1024L * 1024L

  def reserve[F[_]: ApplicativeThrow](retained: Long, additional: Long): F[Long] =
    if (retained < 0L || additional < 0L || retained > MaxBytes || additional > MaxBytes - retained)
      ApplicativeThrow[F].raiseError(
        new WireObservationLimit(
          s"HTTP/2 observation byte budget exceeded ($MaxBytes bytes)"
        )
      )
    else ApplicativeThrow[F].pure(retained + additional)
}
