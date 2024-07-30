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
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.duration._

class ReconnectSuite extends Http4sSuite {

  test("reconnects when connection closes if requested") {
    TestControl.executeEmbed {
      WSTestClient
        .fromHttpWebSocketApp[IO] { (wsb: WebSocketBuilder[IO]) =>
          HttpApp[IO] { case _ =>
            wsb.build(
              Stream(WebSocketFrame.Text("hello"), WebSocketFrame.Close(1000).toOption.get),
              _.drain,
            )
          }
        }
        .flatMap { client =>
          IO.ref(true).flatMap { shouldReconnect =>
            Reconnect(
              client.connectHighLevel(WSRequest(Uri())),
              _ => shouldReconnect.getAndSet(false),
            ).use { conn =>
              conn.receive.assertEquals(Some(WSFrame.Text("hello", last = true))) *>
                // reconnection happens
                conn.receive.assertEquals(Some(WSFrame.Text("hello", last = true))) *>
                // no more reconnections
                IO.sleep(1.second) *>
                conn.receive.intercept[IllegalStateException]
            }
          }
        }
    }
  }

}
