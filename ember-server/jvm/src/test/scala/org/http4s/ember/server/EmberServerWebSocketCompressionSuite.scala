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

import cats.effect.IO
import cats.effect.Resource
import com.comcast.ip4s._
import org.http4s.HttpRoutes
import org.http4s.Http4sSuite
import org.http4s.server.Server
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension
import org.java_websocket.handshake.ServerHandshake

import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class EmberServerWebSocketCompressionSuite extends Http4sSuite {

  private def echoServer(compression: Boolean): Resource[IO, Server] = {
    val builder = EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpWebSocketApp(ws => HttpRoutes.of[IO] { case _ => ws.build(identity) }.orNotFound)
    (if (compression) builder.withWebSocketCompression else builder).build
  }

  private def echo(server: Server, messages: List[String]): IO[(String, List[String])] =
    IO.blocking {
      val extensions = new AtomicReference[String]
      val received = new LinkedBlockingQueue[String]
      val client = new WebSocketClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}"),
        new Draft_6455(new PerMessageDeflateExtension),
      ) {
        def onOpen(handshake: ServerHandshake): Unit =
          extensions.set(handshake.getFieldValue("Sec-WebSocket-Extensions"))
        def onMessage(message: String): Unit = received.put(message)
        def onClose(code: Int, reason: String, remote: Boolean): Unit = ()
        def onError(e: Exception): Unit = ()
      }
      try {
        assert(client.connectBlocking(5, TimeUnit.SECONDS))
        messages.foreach(client.send)
        (extensions.get, messages.map(_ => received.poll(5, TimeUnit.SECONDS)))
      } finally client.closeBlocking()
    }

  private val messages = List("foo", "bar" * 2000, "")

  test("compress messages when the client offers permessage-deflate") {
    echoServer(compression = true).use(echo(_, messages)).map { case (extensions, replies) =>
      assertEquals(
        extensions,
        "permessage-deflate; server_no_context_takeover; client_no_context_takeover",
      )
      assertEquals(replies, messages)
    }
  }

  test("leave messages uncompressed unless compression is enabled") {
    echoServer(compression = false).use(echo(_, messages)).map { case (extensions, replies) =>
      assertEquals(extensions, "")
      assertEquals(replies, messages)
    }
  }
}
