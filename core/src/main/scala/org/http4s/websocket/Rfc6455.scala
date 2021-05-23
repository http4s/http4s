package org.http4s.websocket

import java.nio.charset.StandardCharsets

// https://datatracker.ietf.org/doc/html/rfc6455
private[http4s] object Rfc6455 {
  val handshakeMagic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
  val handshakeMagicBytes = handshakeMagic.getBytes(StandardCharsets.US_ASCII)
}
