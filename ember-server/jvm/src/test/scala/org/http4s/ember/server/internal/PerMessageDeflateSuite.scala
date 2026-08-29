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

import fs2.io.net.ProtocolException
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Http4sSuite
import org.http4s.websocket.FrameTranscoder.TranscodeError
import org.http4s.websocket.WebSocketFrame
import scodec.bits._

class PerMessageDeflateSuite extends Http4sSuite {
  import PerMessageDeflate.Params

  private def negotiate(values: String*): Option[Params] =
    PerMessageDeflate.negotiate(
      Headers(values.toList.map(Header.Raw(PerMessageDeflate.HeaderName, _)))
    )

  private val default = Params(false, false, None)

  private def withDeflate[A](params: Params = default, maxFrameSize: Int = 1024)(
      f: PerMessageDeflate => A
  ): A = {
    val deflate = PerMessageDeflate(params, maxFrameSize)
    try f(deflate)
    finally deflate.close()
  }

  test("accept a plain offer") {
    assertEquals(negotiate("permessage-deflate"), Some(default))
    assertEquals(default.toHeader.value, "permessage-deflate")
  }

  test("accept the offer browsers send") {
    assertEquals(negotiate("permessage-deflate; client_max_window_bits"), Some(default))
  }

  test("echo context takeover parameters") {
    val params =
      negotiate("permessage-deflate; server_no_context_takeover; client_no_context_takeover")
    assertEquals(params, Some(Params(true, true, None)))
    assertEquals(
      params.map(_.toHeader.value),
      Some("permessage-deflate; server_no_context_takeover; client_no_context_takeover"),
    )
  }

  test("accept server_max_window_bits=15") {
    val params = negotiate("permessage-deflate; server_max_window_bits=\"15\"")
    assertEquals(
      params.map(_.toHeader.value),
      Some("permessage-deflate; server_max_window_bits=15"),
    )
  }

  test("fall back to the next offer when server_max_window_bits is below 15") {
    assertEquals(
      negotiate(
        "permessage-deflate; client_max_window_bits; server_max_window_bits=10, permessage-deflate; client_max_window_bits"
      ),
      Some(default),
    )
  }

  test("skip unknown extensions and read multiple headers") {
    assertEquals(negotiate("x-webkit-deflate-frame", "permessage-deflate"), Some(default))
  }

  test("decline invalid offers") {
    List(
      "permessage-deflate; foo",
      "permessage-deflate; server_no_context_takeover; server_no_context_takeover",
      "permessage-deflate; server_no_context_takeover=1",
      "permessage-deflate; server_max_window_bits",
      "permessage-deflate; client_max_window_bits=7",
      "permessage-deflate; client_max_window_bits=016",
      "x-webkit-deflate-frame",
    ).foreach(offer => assertEquals(negotiate(offer), None, offer))
  }

  test("decompress a message (RFC 7692 7.2.3.1)") {
    withDeflate() { deflate =>
      val frame = deflate.decode(WebSocketFrame.Text(hex"f248cdc9c90700", true), rsv1 = true)
      assertEquals(frame, WebSocketFrame.Text("Hello"))
    }
  }

  test("decompress with context takeover (RFC 7692 7.2.3.2)") {
    withDeflate() { deflate =>
      val first = deflate.decode(WebSocketFrame.Text(hex"f248cdc9c90700", true), rsv1 = true)
      val second = deflate.decode(WebSocketFrame.Text(hex"f200110000", true), rsv1 = true)
      assertEquals(
        List(first, second),
        List(WebSocketFrame.Text("Hello"), WebSocketFrame.Text("Hello")),
      )
    }
  }

  test("decompress a fragmented message (RFC 7692 7.2.3.3)") {
    withDeflate() { deflate =>
      val first = deflate.decode(WebSocketFrame.Text(hex"f248cd", false), rsv1 = true)
      val second = deflate.decode(WebSocketFrame.Continuation(hex"c9c90700", true), rsv1 = false)
      assertEquals(first.data ++ second.data, ByteVector("Hello".getBytes))
      assertEquals(second.last, true)
    }
  }

  test("decompress an empty message (RFC 7692 7.2.3.6)") {
    withDeflate() { deflate =>
      val frame = deflate.decode(WebSocketFrame.Binary(hex"00"), rsv1 = true)
      assertEquals(frame, WebSocketFrame.Binary(ByteVector.empty))
    }
  }

  test("pass uncompressed frames through") {
    withDeflate() { deflate =>
      val frame = WebSocketFrame.Text("plain")
      assertEquals(deflate.decode(frame, rsv1 = false), frame)
    }
  }

  test("compress a message as in RFC 7692 7.2.3.1") {
    withDeflate() { deflate =>
      assertEquals(
        deflate.encode(WebSocketFrame.Text("Hello")),
        (WebSocketFrame.Text(hex"f248cdc9c90700", true), true),
      )
    }
  }

  test("round trip messages with and without context takeover") {
    List(default, Params(true, true, None)).foreach { params =>
      withDeflate(params) { server =>
        withDeflate(params) { client =>
          List("Hello", "Hello", "a" * 500, "").foreach { msg =>
            val (encoded, rsv1) = server.encode(WebSocketFrame.Text(msg))
            assertEquals(client.decode(encoded, rsv1), WebSocketFrame.Text(msg))
          }
        }
      }
    }
  }

  test("round trip fragmented messages") {
    val ones = ByteVector.fill(300)(1)
    val twos = ByteVector.fill(300)(2)
    val messages = List(
      List(
        WebSocketFrame.Binary(ones, last = false),
        WebSocketFrame.Continuation(ByteVector.empty, last = false),
        WebSocketFrame.Continuation(twos, last = true),
      ),
      List(
        WebSocketFrame.Binary(ones, last = false),
        WebSocketFrame.Continuation(ByteVector.empty, last = true),
      ),
    )
    withDeflate() { server =>
      withDeflate() { client =>
        messages.foreach { frames =>
          val encoded = frames.map(server.encode)
          assertEquals(encoded.map(_._2), true :: List.fill(frames.size - 1)(false))
          val decoded = encoded.map { case (f, rsv1) => client.decode(f, rsv1) }
          assertEquals(decoded.map(_.data).reduce(_ ++ _), frames.map(_.data).reduce(_ ++ _))
          assertEquals(decoded.map(_.last), frames.map(_.last))
        }
      }
    }
  }

  test("fail on RSV1 set on control and continuation frames") {
    withDeflate() { deflate =>
      intercept[ProtocolException](deflate.decode(WebSocketFrame.Ping(), rsv1 = true))
      intercept[ProtocolException](
        deflate.decode(WebSocketFrame.Continuation(hex"00", true), rsv1 = true)
      )
    }
  }

  test("fail on invalid compressed data") {
    withDeflate() { deflate =>
      intercept[ProtocolException](
        deflate.decode(WebSocketFrame.Binary(hex"ffffffff"), rsv1 = true)
      )
    }
  }

  test("fail when the decompressed frame exceeds the max frame size") {
    withDeflate(maxFrameSize = 1024) { server =>
      withDeflate(maxFrameSize = 1024 * 1024) { client =>
        val (encoded, rsv1) = client.encode(WebSocketFrame.Binary(ByteVector.fill(100000)(0)))
        assert(encoded.length < 1024)
        intercept[TranscodeError](server.decode(encoded, rsv1))
      }
    }
  }
}
