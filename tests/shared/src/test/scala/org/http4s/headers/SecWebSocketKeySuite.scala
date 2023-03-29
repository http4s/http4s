package org.http4s
package headers

import org.http4s.laws.discipline.arbitrary._

class SecWebSocketKeySuite extends HeaderLaws {
  checkAll("Sec-WebSocket-Key", headerLaws[`Sec-WebSocket-Key`])

  // https://datatracker.ietf.org/doc/html/rfc6455#page-7
  val rfc6455ExampleSecWebSocketKey = "dGhlIHNhbXBsZSBub25jZQ=="

  test("parser accepts RFC 6455 example Sec-WebSocket-Key") {
    assert(`Sec-WebSocket-Key`.parse(rfc6455ExampleSecWebSocketKey).isRight)
  }
}
