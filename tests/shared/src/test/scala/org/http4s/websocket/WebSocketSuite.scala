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

package org.http4s.websocket

import cats.syntax.all._
import org.http4s.Http4sSuite
import org.http4s.websocket.WebSocketFrame._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import scodec.bits.ByteVector

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8

class WebSocketSuite extends Http4sSuite {
  private def helloTxtMasked =
    intArrayOps(Array(0x81, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58)).map(
      _.toByte
    )

  private def helloTxt = intArrayOps(Array(0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f)).map(_.toByte)

  def decode(msg: Array[Byte], isClient: Boolean): WebSocketFrame =
    new FrameTranscoder(isClient).bufferToFrame(ByteBuffer.wrap(msg))

  def encode(msg: WebSocketFrame, isClient: Boolean): Array[Byte] = {
    val msgs = new FrameTranscoder(isClient).frameToBuffer(msg)
    val sz = msgs.foldLeft(0)((c, i) => c + i.remaining())
    val b = ByteBuffer.allocate(sz)
    msgs.foreach(b.put)
    b.array()
  }

  test("WebSocket decoder should equate frames correctly") {
    val f1 = Binary(ByteVector(0x2.toByte, 0x3.toByte), true)
    val f11 = Binary(ByteVector(0x2.toByte, 0x3.toByte), true)
    val f2 = Binary(ByteVector(0x2.toByte, 0x3.toByte), false)
    val f3 = Text(ByteVector(0x2.toByte, 0x3.toByte), true)
    val f4 = Binary(ByteVector(0x2.toByte, 0x4.toByte), true)

    assertEquals(f1, f1)
    assertEquals(f1, f11)
    assertNotEquals(f1, f2)
    assertNotEquals[Any, Any](f1, f3)
    assertNotEquals(f1, f4)
  }

  test("WebSocket decoder should decode a hello world message") {
    val result = decode(helloTxtMasked, false)
    assert(result.last)
    assertEquals(new String(result.data.toArray, UTF_8), "Hello")

    val result2 = decode(helloTxt, true)
    assert(result2.last)
    assertEquals(new String(result2.data.toArray, UTF_8), "Hello")
  }

  test("WebSocket decoder should encode a hello world message") {
    val frame = Text(ByteVector.view("Hello".getBytes(UTF_8)), false)
    val msg = decode(encode(frame, true), false)
    assertEquals(msg, frame)
    assert(!msg.last)
    assertEquals(new String(msg.data.toArray, UTF_8), "Hello")
  }

  test("WebSocket decoder should encode a continuation message") {
    val frame = Continuation(ByteVector.view("Hello".getBytes(UTF_8)), true)
    val msg = decode(encode(frame, true), false)
    assertEquals(msg, frame)
    assert(msg.last)
    assertEquals(new String(msg.data.toArray, UTF_8), "Hello")
  }

  test("WebSocket decoder should encode a close message") {
    val reasonGen = for {
      length <- choose(0, 30) // UTF-8 chars are at most 4 bytes, byte limit is 123
      chars <- listOfN(length, arbitrary[Char])
    } yield chars.mkString

    forAll(choose(1000, 4999), reasonGen) { (validCloseCode: Int, validReason: String) =>
      val frame = Close(validCloseCode, validReason).valueOr(throw _)
      val msg = decode(encode(frame, true), false)
      assertEquals(msg, frame)
      assert(msg.last)
      val closeCode = msg.data.slice(0, 2)
      assertEquals((closeCode(0) << 8 & 0xff00) | (closeCode(1) & 0xff), validCloseCode)
      val reason = msg.data.slice(2, msg.data.length)
      assertEquals(new String(reason.toArray, UTF_8), validReason)
    }
  }

  test("WebSocket decoder should refuse to encode a close message with an invalid close code") {
    forAll { (closeCode: Int) =>
      (closeCode < 1000 || closeCode > 4999) ==> Close(closeCode).isLeft
    }
  }

  test(
    "WebSocket decoder should refuse to encode a close message with a reason that is too large"
  ) {
    val validCloseCode = 1000

    forAll { (reason: String) =>
      (reason.getBytes(UTF_8).length > 123) ==> Close(validCloseCode, reason).isLeft
    }
  }

  test("WebSocket decoder should encode and decode a message with 125 < len <= 0xffff") {
    val bytes = ByteVector((0 until 0xfff).map(_.toByte))
    val frame = Binary(bytes, false)

    val msg = decode(encode(frame, true), false)
    val msg2 = decode(encode(frame, false), true)

    assertEquals(msg, frame)
    assertEquals(msg, msg2)
  }

  test("WebSocket decoder should encode and decode a message len > 0xffff") {
    val bytes = ByteVector((0 until (0xffff + 1)).map(_.toByte))
    val frame = Binary(bytes, false)

    val msg = decode(encode(frame, true), false)
    val msg2 = decode(encode(frame, false), true)

    assertEquals(msg, frame)
    assertEquals(msg, msg2)
  }

}
