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
import org.http4s.websocket.FrameTranscoder
import org.http4s.websocket.FrameTranscoder.TranscodeError
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import scodec.bits._

class PerMessageDeflateSuite extends Http4sSuite {
  import PerMessageDeflate.Params

  private val default = Params(false, false, false)
  private val noContextTakeover = Params(true, true, false)

  private def negotiate(offers: String*): Option[String] =
    PerMessageDeflate
      .negotiate(Headers(offers.toList.map(Header.Raw(PerMessageDeflate.HeaderName, _))))
      .map(_.toHeader.value)

  private def withDeflate[A](params: Params = default, maxSize: Int = 1 << 20)(
      f: PerMessageDeflate => A
  ): A = {
    val deflate = PerMessageDeflate(params, maxSize).get
    try f(deflate)
    finally deflate.close()
  }

  private val writer = new FrameTranscoder(isClient = false, maxFrameSize = Int.MaxValue)
  private val reader = new FrameTranscoder(isClient = true, maxFrameSize = Int.MaxValue)

  private def encode(
      deflate: PerMessageDeflate,
      frame: WebSocketFrame,
  ): (WebSocketFrame, Boolean) = {
    val bytes = deflate.encode(frame, writer).foldLeft(ByteVector.empty)(_ ++ ByteVector(_))
    val buffer = bytes.toByteBuffer
    (reader.bufferToFrame(buffer), PerMessageDeflate.rsv1(buffer))
  }

  private def decode(frames: (WebSocketFrame, Boolean)*): ByteVector =
    withDeflate() { deflate =>
      frames.map { case (frame, rsv1) => deflate.decode(frame, rsv1).data }.reduce(_ ++ _)
    }

  test("accept the offer browsers send") {
    assertEquals(
      negotiate("permessage-deflate; client_max_window_bits"),
      Some("permessage-deflate"),
    )
  }

  test("echo the parameters it agrees to") {
    assertEquals(
      negotiate(
        "permessage-deflate; server_no_context_takeover; client_no_context_takeover; server_max_window_bits=\"15\""
      ),
      Some(
        "permessage-deflate; server_no_context_takeover; client_no_context_takeover; server_max_window_bits=15"
      ),
    )
  }

  test("fall back to a later offer when an earlier one is unsupported") {
    assertEquals(
      negotiate("permessage-deflate; server_max_window_bits=10", "permessage-deflate"),
      Some("permessage-deflate"),
    )
  }

  test("decline invalid offers") {
    List(
      "permessage-deflate; foo",
      "permessage-deflate; client_no_context_takeover; client_no_context_takeover",
      "permessage-deflate; server_no_context_takeover=1",
      "permessage-deflate; server_max_window_bits",
      "permessage-deflate; client_max_window_bits=7",
      "permessage-deflate; a=\"unterminated",
    ).foreach(offer => assertEquals(negotiate(offer), None, offer))
  }

  test("ignore unusual syntax in other extensions") {
    assertEquals(negotiate("x-foo; a=b/c; ; d=, permessage-deflate;"), Some("permessage-deflate"))
  }

  test("compress a message as in RFC 7692 7.2.3.1") {
    withDeflate() { deflate =>
      val (frame, rsv1) = encode(deflate, Text("Hello"))
      assertEquals(frame.data, hex"f248cdc9c90700")
      assert(rsv1)
    }
  }

  test("decompress the examples of RFC 7692 7.2.3") {
    val hello = ByteVector("Hello".getBytes)
    assertEquals(decode(Text(hex"f248cdc9c90700", true) -> true), hello)
    withDeflate() { deflate =>
      deflate.decode(Text(hex"f248cdc9c90700", true), rsv1 = true)
      assertEquals(deflate.decode(Text(hex"f200110000", true), rsv1 = true).data, hello)
    }
    assertEquals(
      decode(Text(hex"f248cd", false) -> true, Continuation(hex"c9c90700", true) -> false),
      hello,
    )
    assertEquals(decode(Text(hex"f348cdc9c9070000", true) -> true), hello)
    assertEquals(decode(Binary(hex"00") -> true), ByteVector.empty)
  }

  test("round trip messages with and without context takeover") {
    val messages = List(
      List(Text("Hello")),
      List(Text("Hello")),
      List(Binary(ByteVector.fill(500)(1))),
      List(Text("")),
      List(
        Binary(ByteVector.fill(300)(2), false),
        Continuation(ByteVector.empty, false),
        Continuation(ByteVector.empty, true),
      ),
    )
    List(default, noContextTakeover).foreach { params =>
      withDeflate(params) { server =>
        withDeflate(params) { client =>
          messages.foreach { frames =>
            val received =
              frames.map(encode(server, _)).map { case (f, rsv1) => client.decode(f, rsv1) }
            assertEquals(received.map(_.data).reduce(_ ++ _), frames.map(_.data).reduce(_ ++ _))
          }
        }
      }
    }
  }

  test("reject RSV1 on control and continuation frames, and interleaved messages") {
    withDeflate() { deflate =>
      intercept[ProtocolException](deflate.decode(Ping(), rsv1 = true))
      intercept[ProtocolException](deflate.decode(Continuation(hex"00", true), rsv1 = true))
      deflate.decode(Text(hex"f248cd", false), rsv1 = true)
      intercept[ProtocolException](deflate.decode(Text("next"), rsv1 = false))
    }
  }

  test("keep decoding after a message that ends with a final block and no padding") {
    withDeflate() { deflate =>
      val hello = Text("Hello")
      assertEquals(deflate.decode(Text(hex"f348cdc9c90700", true), rsv1 = true), hello)
      assertEquals(deflate.decode(Text(hex"f248cdc9c90700", true), rsv1 = true), hello)
    }
  }

  test("reject frames with too many final blocks") {
    withDeflate() { deflate =>
      val finalBlocks = ByteVector.concat(List.fill(100)(hex"0300"))
      intercept[ProtocolException](deflate.decode(Binary(finalBlocks), rsv1 = true))
    }
  }

  test("reject invalid compressed data") {
    withDeflate() { deflate =>
      intercept[ProtocolException](deflate.decode(Binary(hex"ffffffff"), rsv1 = true))
    }
  }

  test("limit the decompressed size of a message across its fragments") {
    withDeflate(maxSize = 1000) { server =>
      withDeflate() { client =>
        val first = encode(client, Binary(ByteVector.fill(600)(0), false))
        val last = encode(client, Continuation(ByteVector.fill(600)(0), true))
        server.decode(first._1, first._2)
        intercept[TranscodeError](server.decode(last._1, last._2))
      }
    }
  }
}
