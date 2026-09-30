/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.io.net.Network
import fs2.io.net.tls.TLSParameters
import fs2.io.net.tls.TLSSocket
import org.http4s.Http4sSuite
import org.http4s.ember.core.h2.H2TLS

class H2TLSSuite extends Http4sSuite {

  test("protocol is h2 when both sides support it") {
    tlsClientSocket(
      serverParams = H2TLS.transform(TLSParameters.Default),
      clientParams = H2TLS.transform(TLSParameters.Default),
    )
      .use(H2TLS.protocol[IO])
      .assertEquals(Some("h2"))
  }

  test("protocol is http/1.1 when the client only offers it") {
    tlsClientSocket(
      serverParams = H2TLS.transform(TLSParameters.Default),
      clientParams = TLSParameters(applicationProtocols = List("http/1.1").some),
    )
      .use(H2TLS.protocol[IO])
      .assertEquals(Some("http/1.1"))
  }

  test("protocol is None when ALPN was not negotiated") {
    tlsClientSocket(serverParams = TLSParameters.Default, clientParams = TLSParameters.Default)
      .use { socket =>
        // Neither side offers ALPN, so the JVM reports the negotiated protocol as "" rather than failing
        socket.applicationProtocol.assertEquals("") >> H2TLS.protocol(socket)
      }
      .assertEquals(None)
  }

  /** Connects a TLS client to a TLS server over loopback, completing the handshake
    * the same way Ember does, and returns the client side socket.
    */
  private def tlsClientSocket(
      serverParams: TLSParameters,
      clientParams: TLSParameters,
  ): Resource[IO, TLSSocket[IO]] =
    for {
      tls <- Resource.eval(
        Network[IO].tlsContext
          .fromKeyStoreResource("keystore.jks", "password".toCharArray, "password".toCharArray)
      )
      serverSocket <- Network[IO].bind(SocketAddress(ip"127.0.0.1", port"0"))
      server = serverSocket.accept.head.compile.resource.lastOrError
        .flatMap(tls.serverBuilder(_).withParameters(serverParams).build)
        .evalTap(_.write(Chunk.empty))
      client = Network[IO]
        .connect(serverSocket.address)
        .flatMap(tls.clientBuilder(_).withParameters(clientParams).build)
        .evalTap(_.write(Chunk.empty))
      sockets <- (server, client).parTupled
    } yield sockets._2

}
