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
package server
package websocket

import cats.effect._
import fs2.Stream
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.websocket.WebSocketFrame

class WebSocketBuilderSuite extends Http4sSuite {

  // this could be your HTTP application taking a WebSocketBuilder2 as an argument
  private def simpleWsApp(wsb: WebSocketBuilder2[IO]) =
    HttpRoutes
      .of[IO] { case GET -> Root / "ws" =>
        wsb.build(Stream.emit(WebSocketFrame.Text("hello world")), _.as(()))
      }
      .orNotFound

  test("return web socket route default message") {
    WebSocketBuilder2[IO].flatMap { wsb =>
      simpleWsApp(wsb)
        .run(Request[IO](GET, uri"/ws"))
        .flatMap(_.as[String])
        .assertEquals("This is a WebSocket route.")
    }
  }

}
