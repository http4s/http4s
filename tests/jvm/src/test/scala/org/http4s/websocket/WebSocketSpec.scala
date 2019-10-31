package org.http4s.websocket

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import org.http4s.websocket.WebSocketFrame._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import scodec.bits.ByteVector

class WebSocketSpec extends Specification with ScalaCheck {

  def helloTxtMasked =
    Array(0x81, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58).map(_.toByte)

  def helloTxt = Array(0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f).map(_.toByte)

  def decode(msg: Array[Byte], isClient: Boolean): WebSocketFrame =
    new FrameTranscoder(isClient).bufferToFrame(ByteBuffer.wrap(msg))

  def encode(msg: WebSocketFrame, isClient: Boolean): Array[Byte] = {
    val msgs = new FrameTranscoder(isClient).frameToBuffer(msg)
    val sz = msgs.foldLeft(0)((c, i) => c + i.remaining())
    val b = ByteBuffer.allocate(sz)
    msgs.foreach(b.put)
    b.array()
  }

  "WebSocket decoder" should {

    "equate frames correctly" in {
      val f1 = Binary(ByteVector(0x2.toByte, 0x3.toByte), true)
      val f11 = Binary(ByteVector(0x2.toByte, 0x3.toByte), true)
      val f2 = Binary(ByteVector(0x2.toByte, 0x3.toByte), false)
      val f3 = Text(ByteVector(0x2.toByte, 0x3.toByte), true)
      val f4 = Binary(ByteVector(0x2.toByte, 0x4.toByte), true)

      f1 should_== f1
      f1 should_== f11
      f1 should_!= f2
      f1 should_!= f3
      f1 should_!= f4
    }

    "decode a hello world message" in {

      val result = decode(helloTxtMasked, false)
      result.last should_== true
      new String(result.data.toArray, UTF_8) should_== "Hello"

      val result2 = decode(helloTxt, true)
      result2.last should_== true
      new String(result2.data.toArray, UTF_8) should_== "Hello"
    }

    "encode a hello world message" in {
      val frame = Text(ByteVector.view("Hello".getBytes(UTF_8)), false)
      val msg = decode(encode(frame, true), false)
      msg should_== frame
      msg.last should_== false
      new String(msg.data.toArray, UTF_8) should_== "Hello"
    }

    "encode a continuation message" in {
      val frame = Continuation(ByteVector.view("Hello".getBytes(UTF_8)), true)
      val msg = decode(encode(frame, true), false)
      msg should_== frame
      msg.last should_== true
      new String(msg.data.toArray, UTF_8) should_== "Hello"
    }

    "encode a close message" in {

      val reasonGen = for {
        length <- choose(0, 30) //UTF-8 chars are at most 4 bytes, byte limit is 123
        chars <- listOfN(length, arbitrary[Char])
      } yield chars.mkString

      forAll(choose(1000, 4999), reasonGen) { (validCloseCode: Int, validReason: String) =>
        val frame = Close(validCloseCode, validReason).right.get
        val msg = decode(encode(frame, true), false)
        msg should_== frame
        msg.last should_== true
        val closeCode = msg.data.slice(0, 2)
        (closeCode(0) << 8 & 0xff00) | (closeCode(1) & 0xff) should_== validCloseCode
        val reason = msg.data.slice(2, msg.data.length)
        new String(reason.toArray, UTF_8) should_== validReason
      }
    }

    "refuse to encode a close message with an invalid close code" in {
      forAll { closeCode: Int =>
        (closeCode < 1000 || closeCode > 4999) ==> Close(closeCode).isLeft
      }
    }

    "refuse to encode a close message with a reason that is too large" in {
      val validCloseCode = 1000

      forAll { reason: String =>
        (reason.getBytes(UTF_8).length > 123) ==> Close(validCloseCode, reason).isLeft
      }
    }

    "encode and decode a message with 125 < len <= 0xffff" in {
      val bytes = ByteVector((0 until 0xfff).map(_.toByte))
      val frame = Binary(bytes, false)

      val msg = decode(encode(frame, true), false)
      val msg2 = decode(encode(frame, false), true)

      msg should_== frame
      msg should_== msg2
    }

    "encode and decode a message len > 0xffff" in {
      val bytes = ByteVector((0 until (0xffff + 1)).map(_.toByte))
      val frame = Binary(bytes, false)

      val msg = decode(encode(frame, true), false)
      val msg2 = decode(encode(frame, false), true)

      msg should_== frame
      msg should_== msg2
    }
  }

}
