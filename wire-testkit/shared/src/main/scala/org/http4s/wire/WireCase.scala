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

import cats.effect.IO
import cats.effect.Deferred
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.io.net.Network
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

final case class CaseContext(target: WireTarget[IO], transcript: Transcript[IO]) {
  def server(
      protocol: Protocol,
      scenario: ServerScenario = ServerScenario.Reply(),
  ): Resource[IO, (RawPeer[IO], ServerHandle[IO])] =
    for {
      server <- target.server(protocol, scenario)
      socket <- Network[IO].connect(server.address)
      peer <- Resource.eval(RawPeer.create(socket, transcript))
    } yield (peer, server)

  /** Run a target client concurrently with an independent, scripted server. */
  def client[A](protocol: Protocol)(
      exercise: ClientDriver[IO] => IO[A]
  )(serve: RawPeer[IO] => IO[Unit]): IO[A] =
    Network[IO].bind(SocketAddress(ip"127.0.0.1", port"0")).use { listener =>
      Deferred[IO, Unit].flatMap { completed =>
        val server = listener.accept.head.compile.resource.lastOrError.use { socket =>
          RawPeer.create(socket, transcript).flatMap(serve) *> completed.get
        }
        val client = target
          .client(protocol, listener.address.asIpUnsafe)
          .use(exercise)
          .guarantee(completed.complete(()).void)
        (server, client).parTupled.map(_._2)
      }
    }
}
