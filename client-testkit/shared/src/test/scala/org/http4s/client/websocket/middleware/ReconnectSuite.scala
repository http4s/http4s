/*
 * Copyright 2014 http4s.org
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

package org.http4s
package client
package websocket
package middleware

import cats.effect.IO
import cats.effect.testkit.TestControl
import fs2.Stream
import org.http4s.client.testkit.WSTestClient
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

class ReconnectSuite extends Http4sSuite {

  test("reconnects if connection closes") {
    TestControl.executeEmbed {
      WSTestClient
        .fromHttpWebSocketApp[IO] { (wsb: WebSocketBuilder2[IO]) =>
          HttpApp[IO] { case _ =>
            wsb.build(
              Stream(WebSocketFrame.Text("hello"), WebSocketFrame.Close(1000).toOption.get),
              _.drain,
            )
          }
        }
        .flatMap { client =>
          Reconnect(client.connectHighLevel(WSRequest(Uri())), _ => IO.pure(true)).use { conn =>
            conn.receive.assertEquals(Some(WSFrame.Text("hello", true))) *>
              // reconnection happens
              conn.receive.assertEquals(Some(WSFrame.Text("hello", true)))
          }
        }
    }
  }

}
