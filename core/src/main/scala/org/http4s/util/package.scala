package org.http4s

import fs2._
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.StandardCharsets

import scala.concurrent.ExecutionContextExecutor

package object util {
  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = {
    val decoder = charset.nioCharset.newDecoder
    val maxCharsPerByte = math.ceil(decoder.maxCharsPerByte().toDouble).toInt
    val avgBytesPerChar = math.ceil(1.0 / decoder.averageCharsPerByte().toDouble).toLong
    val charBufferSize = 128L

    _.repeatPull[String] {
      _.unconsN(charBufferSize * avgBytesPerChar, allowFewer = true).flatMap {
        case None =>
          val charBuffer = CharBuffer.allocate(1)
          decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
          decoder.flush(charBuffer)
          val outputString = charBuffer.flip().toString
          if (outputString.isEmpty) Pull.done.as(None)
          else Pull.output1(outputString).as(None)
        case Some((segment, stream)) =>
          val bytes = segment.force.toArray
          val byteBuffer = ByteBuffer.wrap(bytes)
          val charBuffer = CharBuffer.allocate(bytes.length * maxCharsPerByte)
          decoder.decode(byteBuffer, charBuffer, false)
          val nextStream = stream.consChunk(Chunk.byteBuffer(byteBuffer.slice()))
          Pull.output1(charBuffer.flip().toString).as(Some(nextStream))
      }
    }
  }

  /** Converts ASCII encoded byte stream to a stream of `String`. */
  private[http4s] def asciiDecode[F[_]]: Pipe[F, Byte, String] =
    _.chunks.through(asciiDecodeC)

  private def asciiCheck(b: Byte) = 0x80 & b

  /** Converts ASCII encoded `Chunk[Byte]` inputs to `String`. */
  private[http4s] def asciiDecodeC[F[_]]: Pipe[F, Chunk[Byte], String] = { in =>
    def tailRecAsciiCheck(i: Int, bytes: Array[Byte]): Stream[F, String] =
      if (i == bytes.length)
        Stream.emit(new String(bytes, StandardCharsets.US_ASCII))
      else {
        if (asciiCheck(bytes(i)) == 0x80) {
          Stream.raiseError(
            new IllegalArgumentException("byte stream is not encodable as ascii bytes"))
        } else {
          tailRecAsciiCheck(i + 1, bytes)
        }
      }

    in.flatMap(c => tailRecAsciiCheck(0, c.toArray))
  }

  /** Hex encoding digits. Adapted from apache commons Hex.encodeHex **/
  private val Digits: Array[Char] =
    Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

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

  @deprecated("Moved to org.http4s.syntax.StringOps", "0.16")
  type CaseInsensitiveStringOps = org.http4s.syntax.StringOps

  @deprecated("Moved to org.http4s.syntax.StringSyntax", "0.16")
  type CaseInsensitiveStringSyntax = org.http4s.syntax.StringSyntax

  @deprecated("Moved to org.http4s.syntax.NonEmptyListSyntax", "0.18.0-M5")
  type NonEmptyListSyntax = org.http4s.syntax.NonEmptyListSyntax

  @deprecated(
    "Moved to org.http4s.execution.trampoline, is now merely a ExecutionContextExecutor.",
    "0.18.0-M2")
  val TrampolineExecutionContext: ExecutionContextExecutor = execution.trampoline

  @deprecated("Use fs2.StreamApp instead", "0.18.0-M7")
  type StreamApp[F[_]] = fs2.StreamApp[F]

  @deprecated("Use fs2.StreamApp.ExitCode instead", "0.18.0-M7")
  type ExitCode = fs2.StreamApp.ExitCode
  @deprecated("Use fs2.StreamApp.ExitCode instead", "0.18.0-M7")
  val ExitCode = fs2.StreamApp.ExitCode

  /* This is nearly identical to the hashCode of java.lang.String, but converting
   * to lower case on the fly to avoid copying `value`'s character storage.
   */
  def hashLower(s: String): Int = {
    var h = 0
    var i = 0
    val len = s.length
    while (i < len) {
      // Strings are equal igoring case if either their uppercase or lowercase
      // forms are equal. Equality of one does not imply the other, so we need
      // to go in both directions. A character is not guaranteed to make this
      // round trip, but it doesn't matter as long as all equal characters
      // hash the same.
      h = h * 31 + Character.toLowerCase(Character.toUpperCase(s.charAt(i)))
      i += 1
    }
    h
  }
}
