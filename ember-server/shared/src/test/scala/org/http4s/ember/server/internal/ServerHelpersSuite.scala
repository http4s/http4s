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

import munit._
import org.http4s._
import org.typelevel.ci._

class ServerHelpersSuite extends CatsEffectSuite {

  val close = Header.Raw(ci"connection", "close")
  val keepAlive = Header.Raw(ci"connection", "keep-alive")

  test("isKeepAlive - http/1.0, connection=close") {
    assertEquals(ServerHelpers.isKeepAlive(HttpVersion.`HTTP/1.0`, Headers(close)), false)
  }

  test("isKeepAlive - http/1.0, connection=keep-alive") {
    assertEquals(ServerHelpers.isKeepAlive(HttpVersion.`HTTP/1.0`, Headers(keepAlive)), true)
  }

  test("isKeepAlive - http/1.0, default") {
    assertEquals(ServerHelpers.isKeepAlive(HttpVersion.`HTTP/1.0`, Headers.empty), false)
  }

  test("isKeepAlive - http/1.1, connection=close") {
    assertEquals(ServerHelpers.isKeepAlive(HttpVersion.`HTTP/1.1`, Headers(close)), false)
  }

  test("isKeepAlive - http/1.1, connection=keep-alive") {
    assertEquals(ServerHelpers.isKeepAlive(HttpVersion.`HTTP/1.1`, Headers(keepAlive)), true)
  }

  test("isKeepAlive - http/1.1, default") {
    assertEquals(ServerHelpers.isKeepAlive(HttpVersion.`HTTP/1.1`, Headers.empty), true)
  }
}
