package org.http4s

import java.nio.{ByteBuffer, CharBuffer}

import fs2._
import fs2.util.Attempt
import scodec.bits.ByteVector

import scala.util.control.NonFatal

package object util {
  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = {
    val decoder = charset.nioCharset.newDecoder
    val maxCharsPerByte = math.ceil(decoder.maxCharsPerByte().toDouble).toInt
    val avgBytesPerChar =
      math.ceil(1.0 / decoder.averageCharsPerByte().toDouble).toInt
    val charBufferSize = 128

    _.repeatPull[String] {
      _.awaitN(charBufferSize * avgBytesPerChar, allowFewer = true).optional
        .flatMap {
          case None =>
            val charBuffer = CharBuffer.allocate(1)
            decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
            decoder.flush(charBuffer)
            val outputString = charBuffer.flip().toString
            if (outputString.isEmpty) Pull.done
            else Pull.output1(outputString) as Handle.empty
          case Some((chunks, handle)) =>
            val chunk = chunks.flatMap(_.toList)
            val byteVector = ByteVector(chunk.toArray)
            val byteBuffer = byteVector.toByteBuffer
            val charBuffer =
              CharBuffer.allocate(byteVector.size.toInt * maxCharsPerByte)
            decoder.decode(byteBuffer, charBuffer, false)
            val nextByteVector = ByteVector.view(byteBuffer.slice)
            val nextHandle = handle.push(Chunk.bytes(nextByteVector.toArray))
            Pull.output1(charBuffer.flip().toString) as nextHandle
        }
    }
  }

  /** Hex encoding digits. Adapted from apache commons Hex.encodeHex **/
  private val Digits: Array[Char] = Array('0', '1', '2', '3', '4', '5', '6',
    '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  /** Encode a string to a Hexadecimal string representation
    * Adapted from apache commons Hex.encodeHex
    */
  def encodeHex(data: Array[Byte]): Array[Char] = {
    val l = data.length
    val out = new Array[Char](l << 1)
    // two characters form the hex value.
    var i = 0
    var j = 0
    while (i < l) {
      out(j) = Digits((0xF0 & data(i)) >>> 4)
      j += 1
      out(j) = Digits(0x0F & data(i))
      j += 1
      i += 1
    }
    out
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(
      s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  private[http4s] def tryCatchNonFatal[A](f: => A): Attempt[A] =
    try Right(f)
    catch { case NonFatal(t) => Left(t) }

  @deprecated("Moved to org.http4s.syntax.StringOps", "0.16")
  type CaseInsensitiveStringOps = org.http4s.syntax.StringOps

  @deprecated("Moved to org.http4s.syntax.StringSyntax", "0.16")
  type CaseInsensitiveStringSyntax = org.http4s.syntax.StringSyntax
}
