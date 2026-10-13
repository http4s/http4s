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
import cats.effect.IO
import cats.effect.Resource
import com.comcast.ip4s._
import fs2.Stream
import fs2.io.net.Network

/** Connection ownership is independent of the driver's exchange-resource ownership. */
private[wire] object ScriptedClient {
  def network[F[_]: Async: Network](
      driver: (SocketAddress[IpAddress], RawPeer[F]) => Resource[F, ClientDriver[F]]
  ): WireTarget[F] = {
    // JVM and Native use this instance; JS implements its own socket transport.
    val _ = Network[F]
    apply((address: SocketAddress[IpAddress], trace: Transcript[F]) =>
      WirePeerTransport.connect[F](address, trace)
    )(driver)
  }

  def apply[F[_]: Async](
      connect: (SocketAddress[IpAddress], Transcript[F]) => Resource[F, RawPeer[F]]
  )(driver: (SocketAddress[IpAddress], RawPeer[F]) => Resource[F, ClientDriver[F]]): WireTarget[F] =
    new WireTarget[F] {
      val name = "scripted-client"
      val version = "oracle-challenge"
      def withFailureObserver(observe: AdapterFailure => F[Unit]): WireTarget[F] = this
      def server(protocol: Protocol, scenario: ServerScenario): Resource[F, ServerHandle[F]] =
        Resource.eval(Async[F].raiseError(new UnsupportedOperationException("client only")))
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[F, ClientDriver[F]] =
        for {
          trace <- Resource.eval(Transcript.create[F]())
          peer <- connect(address, trace)
          result <- driver(address, peer)
        } yield result
    }

  def run(
      wireCase: WireCase,
      inMemory: Boolean,
  )(driver: RawPeer[IO] => Resource[IO, ClientDriver[IO]]): IO[Unit] =
    Transcript.create[IO]().flatMap { trace =>
      if (!inMemory) wireCase.run(CaseContext(network[IO]((_, peer) => driver(peer)), trace))
      else
        Transcript.create[IO]().flatMap { scripted =>
          MemoryPeer.pair(trace, scripted).use { case (server, client) =>
            val address = SocketAddress(ip"127.0.0.1", port"1")
            val target = apply[IO]((_, _) => Resource.pure(client))((_, peer) => driver(peer))
            wireCase.run(
              CaseContext(
                target,
                trace,
                clientListener = _ =>
                  Resource.pure(
                    WirePeerTransport.Listener(address, Resource.pure(server))
                  ),
              )
            )
          }
        }
    }

  /** Use only for fixtures which deliberately ignore the upload; streaming probes supply their own driver. */
  def ignoringUpload[F[_]](
      run: RequestData => Resource[F, ResponseData[F]]
  ): ClientDriver[F] = new ClientDriver[F] {
    def exchange(request: RequestData): Resource[F, ResponseData[F]] = run(request)
    def exchangeStream(
        request: RequestData,
        body: FixtureCleanup[F] => Stream[F, Byte],
    ): Resource[F, ResponseData[F]] = run(request)
  }
}
