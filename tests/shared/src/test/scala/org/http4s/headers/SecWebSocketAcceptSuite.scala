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

import org.http4s.laws.discipline.arbitrary._

class SecWebSocketAcceptSuite extends HeaderLaws {
  checkAll("Sec-WebSocket-Accept", headerLaws[`Sec-WebSocket-Accept`])

  // https://datatracker.ietf.org/doc/html/rfc6455#page-8
  val rfc6455ExampleSecWebSocketAccept = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="

  test("parser accepts RFC 6455 example Sec-WebSocket-Accept") {
    assert(`Sec-WebSocket-Accept`.parse(rfc6455ExampleSecWebSocketAccept).isRight)
  }
}
