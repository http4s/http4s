package org.http4s

import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder

import scodec.bits.ByteVector

import scalaz.State
import scalaz.concurrent.Task
import scalaz.stream.{process1, Channel, Process, Process1}
import scalaz.stream.Process._
import scalaz.stream.io.bufferedChannel
import scalaz.std.option.none

package object util {
  /** Temporary.  Contribute back to scalaz-stream. */
  def decode(charset: Charset): Process1[ByteVector, String] = suspend {
    val decoder = charset.nioCharset.newDecoder
    var carryOver = ByteVector.empty

    def push(chunk: ByteVector, eof: Boolean) = {
      val in = carryOver ++ chunk
      val byteBuffer = in.toByteBuffer
      val charBuffer = CharBuffer.allocate(in.size.toInt + 1)
      decoder.decode(byteBuffer, charBuffer, eof)
      if (eof) decoder.flush(charBuffer)
      else carryOver = ByteVector.view(byteBuffer.slice)
      charBuffer.flip().toString
    }

    // A ByteVector can now be longer than Int.MaxValue, but the CharBuffer
    // above cannot.  We need to split enormous chunks just in case.
    def breakBigChunks(): Process1[ByteVector, ByteVector] =
      receive1[ByteVector, ByteVector] { chunk =>
        def loop(chunk: ByteVector): Process1[ByteVector, ByteVector] =
          chunk.splitAt(Long.MaxValue - 1L) match {
            case (bv, ByteVector.empty) =>
              emit(bv) ++ breakBigChunks()
            case (bv, tail) =>
              emit(bv) ++ loop(tail)
          }
        loop(chunk)
      }

    def go(): Process1[ByteVector, String] = receive1[ByteVector, String] { chunk =>
      val s = push(chunk, false)
      val sChunk = if (s.nonEmpty) emit(s) else halt
      sChunk ++ go()
    }

    def flush() = {
      val s = push(ByteVector.empty, true)
      if (s.nonEmpty) emit(s) else halt
    }

    breakBigChunks() pipe go() onComplete flush()
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
    new AssertionError(s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  @deprecated("Moved to org.http4s.syntax.StringOps", "0.16")
  type CaseInsensitiveStringOps = org.http4s.syntax.StringOps

  @deprecated("Moved to org.http4s.syntax.StringSyntax", "0.16")
  type CaseInsensitiveStringSyntax = org.http4s.syntax.StringSyntax
}
