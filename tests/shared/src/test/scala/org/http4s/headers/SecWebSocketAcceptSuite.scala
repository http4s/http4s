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
