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

import java.util.zip.Deflater

class PerMessageDeflateSuite extends Http4sSuite {
  import PerMessageDeflate.Params

  private def negotiate(values: String*): Option[Params] =
    PerMessageDeflate.negotiate(
      Headers(values.toList.map(Header.Raw(PerMessageDeflate.HeaderName, _)))
    )

  private val default = Params(false, false, false)

  private def withDeflate[A](params: Params = default, maxSize: Int = 1024)(
      f: PerMessageDeflate => A
  ): A = {
    val deflate = PerMessageDeflate(params, maxSize)
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
    assertEquals(params, Some(Params(true, true, false)))
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

  test("parse quoted values with escaped quotes") {
    assertEquals(
      negotiate("x-foo; a=\"b\\\"c, d\", permessage-deflate; server_max_window_bits=\"15\""),
      Some(Params(false, false, true)),
    )
  }

  test("accept the offer when another extension has unusual values") {
    assertEquals(negotiate("x-foo; a=b/c; d=@e, permessage-deflate"), Some(default))
  }

  test("decline permessage-deflate parameters with unusual values") {
    assertEquals(negotiate("permessage-deflate; server_max_window_bits=1/5"), None)
  }

  test("decline everything when the header is malformed") {
    assertEquals(negotiate("permessage-deflate; ="), None)
    assertEquals(negotiate("permessage-deflate; a=\"open"), None)
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

  test("close without ever compressing or decompressing") {
    withDeflate()(_ => ())
  }

  test("decompress independent messages without context takeover") {
    withDeflate(Params(false, true, false)) { deflate =>
      List.fill(2)(hex"f248cdc9c90700").foreach { payload =>
        val frame = deflate.decode(WebSocketFrame.Text(payload, true), rsv1 = true)
        assertEquals(frame, WebSocketFrame.Text("Hello"))
      }
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
    List(default, Params(true, true, false)).foreach { params =>
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

  test("fail when the decompressed message exceeds the limit") {
    withDeflate(maxSize = 1024) { server =>
      withDeflate(maxSize = 1024 * 1024) { client =>
        val (encoded, rsv1) = client.encode(WebSocketFrame.Binary(ByteVector.fill(100000)(0)))
        assert(encoded.length < 1024)
        intercept[TranscodeError](server.decode(encoded, rsv1))
      }
    }
  }

  test("fail when decompressed fragments together exceed the limit") {
    withDeflate(maxSize = 1000) { server =>
      withDeflate(maxSize = 1024 * 1024) { client =>
        val (first, rsv1) = client.encode(WebSocketFrame.Binary(ByteVector.fill(600)(0), false))
        val (second, _) = client.encode(WebSocketFrame.Continuation(ByteVector.fill(600)(0), true))
        server.decode(first, rsv1)
        intercept[TranscodeError](server.decode(second, rsv1 = false))
      }
    }
  }

  test("reset the decompressed limit when a new message starts mid-fragment") {
    withDeflate(maxSize = 1000) { server =>
      withDeflate(maxSize = 1024 * 1024) { client =>
        val (first, rsv1) = client.encode(WebSocketFrame.Binary(ByteVector.fill(600)(0), false))
        val (second, _) = client.encode(WebSocketFrame.Binary(ByteVector.fill(600)(0), true))
        server.decode(first, rsv1)
        assertEquals(server.decode(second, rsv1 = true).length, 600)
      }
    }
  }

  test("reset the decompressed limit for each message") {
    withDeflate(maxSize = 1000) { server =>
      withDeflate(maxSize = 1024 * 1024) { client =>
        List.fill(3)(ByteVector.fill(600)(1)).foreach { data =>
          val (encoded, rsv1) = client.encode(WebSocketFrame.Binary(data))
          assertEquals(server.decode(encoded, rsv1).data, data)
        }
      }
    }
  }

  private def rawDeflate(data: String, dictionary: String, finish: Boolean): ByteVector = {
    val deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
    try {
      if (dictionary.nonEmpty) deflater.setDictionary(dictionary.getBytes)
      deflater.setInput(data.getBytes)
      if (finish) deflater.finish()
      val buffer = new Array[Byte](1024)
      val n = deflater.deflate(
        buffer,
        0,
        buffer.length,
        if (finish) Deflater.NO_FLUSH else Deflater.SYNC_FLUSH,
      )
      ByteVector(buffer, 0, n)
    } finally deflater.end()
  }

  test("decompress a message ending with a BFINAL block (RFC 7692 7.2.3.4)") {
    withDeflate() { deflate =>
      val frame = deflate.decode(WebSocketFrame.Text(hex"f348cdc9c9070000", true), rsv1 = true)
      assertEquals(frame, WebSocketFrame.Text("Hello"))
    }
  }

  test("keep decompressing after a BFINAL block in the same message") {
    withDeflate() { deflate =>
      val payload =
        rawDeflate("He", "", finish = true) ++ rawDeflate("llo", "He", finish = false).dropRight(4)
      val frame = deflate.decode(WebSocketFrame.Text(payload, true), rsv1 = true)
      assertEquals(frame, WebSocketFrame.Text("Hello"))
    }
  }

  test("keep the context after a message ending with a BFINAL block") {
    withDeflate() { deflate =>
      val first = rawDeflate("Hello", "", finish = true) ++ hex"00"
      val second = rawDeflate("Hello", "Hello", finish = false).dropRight(4)
      assertEquals(
        deflate.decode(WebSocketFrame.Text(first, true), rsv1 = true),
        WebSocketFrame.Text("Hello"),
      )
      assertEquals(
        deflate.decode(WebSocketFrame.Text(second, true), rsv1 = true),
        WebSocketFrame.Text("Hello"),
      )
    }
  }

  test("fail on too many BFINAL blocks in one frame") {
    withDeflate(maxSize = 1024 * 1024) { server =>
      withDeflate(maxSize = 1024 * 1024) { client =>
        val (fill, rsv1) = client.encode(WebSocketFrame.Binary(ByteVector.fill(40000)(0)))
        server.decode(fill, rsv1)
        val emptyFinalBlocks = ByteVector.concat(List.fill(100)(hex"0300"))
        intercept[ProtocolException](
          server.decode(WebSocketFrame.Binary(emptyFinalBlocks), rsv1 = true)
        )
      }
    }
  }

  test("fail on invalid data after a BFINAL block") {
    withDeflate() { deflate =>
      val payload = rawDeflate("Hello", "", finish = true) ++ hex"ffffffff"
      intercept[ProtocolException](deflate.decode(WebSocketFrame.Text(payload, true), rsv1 = true))
    }
  }
}
