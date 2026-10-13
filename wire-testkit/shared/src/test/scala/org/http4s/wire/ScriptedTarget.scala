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

private[wire] final class ScriptedTarget[F[_]: Async: Network] private (
    script: ScriptedTarget.Script[F],
    probe: ScriptedTarget.Probe[F],
) extends WireTarget[F] {
  def withFailureObserver(observe: AdapterFailure => F[Unit]): WireTarget[F] = this

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

  def run(id: String)(script: Script[IO]): IO[Unit] = for {
    fixture <- create[IO](script)
    trace <- Transcript.create[IO]()
    _ <- Catalog.all.find(_.id == id).get.run(CaseContext(fixture._1, trace))
  } yield ()

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
