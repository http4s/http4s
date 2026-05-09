/*
 * Copyright 2013 http4s.org
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
package headers

import cats.data.NonEmptyList
import org.http4s.laws.discipline.arbitrary._

class SecWebSocketProtocolSuite extends HeaderLaws {
  checkAll("Sec-WebSocket-Protocol", headerLaws[`Sec-WebSocket-Protocol`])

  test("parser accepts a single token") {
    assertEquals(
      `Sec-WebSocket-Protocol`.parse("soap"),
      Right(`Sec-WebSocket-Protocol`("soap")),
    )
  }

  test("parser accepts a comma-separated list of tokens") {
    assertEquals(
      `Sec-WebSocket-Protocol`.parse("soap, wamp"),
      Right(`Sec-WebSocket-Protocol`("soap", "wamp")),
    )
  }

  test("parser rejects an empty value") {
    assert(`Sec-WebSocket-Protocol`.parse("").isLeft)
  }

  test("renders comma-separated values") {
    assertEquals(
      `Sec-WebSocket-Protocol`(NonEmptyList.of("soap", "wamp")).values.toList,
      List("soap", "wamp"),
    )
  }
}
