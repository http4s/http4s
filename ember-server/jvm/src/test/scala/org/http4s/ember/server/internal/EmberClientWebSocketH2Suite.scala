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

package org.http4s.ember.server.internal

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Pipe
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import org.http4s._
import org.http4s.client.websocket._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.websocket._
import org.http4s.websocket._

/** The WebSocket upgrade is only specified for HTTP/1, so the client must
  * negotiate it over HTTP/1 even when it would otherwise prefer HTTP/2.
  */
class EmberClientWebSocketH2Suite extends Http4sSuite {

  def service[F[_]](wsBuilder: WebSocketBuilder2[F])(implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello")
        case GET -> Root / "ws-echo" =>
          val sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame] = _.collect {
            case WebSocketFrame.Text(text, _) => WebSocketFrame.Text(text)
          }
          wsBuilder.build(sendReceive)
      }
      .orNotFound
  }

  val tlsContext: Resource[IO, TLSContext[IO]] =
    Resource.eval(
      Network[IO].tlsContext
        .fromKeyStoreResource("keystore.jks", "password".toCharArray, "password".toCharArray)
    )

  val serverResource: Resource[IO, Server] =
    tlsContext.flatMap { tls =>
      EmberServerBuilder
        .default[IO]
        .withPort(port"0")
        .withTLS(tls)
        .withHttp2
        .withHttpWebSocketApp(service[IO])
        .build
    }

  val clientResource =
    tlsContext.flatMap { tls =>
      EmberClientBuilder
        .default[IO]
        .withTLSContext(tls)
        .withoutCheckEndpointAuthentication
        .withHttp2
        .buildWebSocket
    }

  private def fixture =
    (ResourceFunFixture(serverResource), ResourceFunFixture(clientResource)).mapN(
      FunFixture.map2(_, _)
    )

  fixture.test("upgrade over HTTP/1 even after HTTP/2 was negotiated for the host") {
    case (server, (client, wsClient)) =>
      val address = server.addressIp4s
      val httpsUrl = Uri.unsafeFromString(s"https://127.0.0.1:${address.port.value}/")
      val wsUrl = Uri.unsafeFromString(s"wss://127.0.0.1:${address.port.value}/ws-echo")

      for {
        // Negotiates HTTP/2 via ALPN, so the client records the host as an HTTP/2 host.
        status <- client.get(httpsUrl)(resp => IO.pure(resp.status))
        _ = assertEquals(status, Status.Ok)
        received <- wsClient
          .connect(WSRequest(wsUrl))
          .use(conn => conn.send(WSFrame.Text("hello")) *> conn.receive)
      } yield assertEquals(received, Some(WSFrame.Text("hello"): WSFrame))
  }
}
