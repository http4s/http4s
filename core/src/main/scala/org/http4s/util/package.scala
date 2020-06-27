/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.ApplicativeError
import fs2._
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.StandardCharsets
import org.http4s.internal.skipUtf8ByteOrderMark
import org.typelevel.ci.CIString
import scala.concurrent.ExecutionContextExecutor

package object util {
  @deprecated(
    "Can fall into an infinite loop, which can't be fixed without a RaiseThrowable constraint. A corrected version will be contributed to fs2.",
    "0.21.5")
  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = {
    val decoder = charset.nioCharset.newDecoder
    val maxCharsPerByte = math.ceil(decoder.maxCharsPerByte().toDouble).toInt
    val avgBytesPerChar = math.ceil(1.0 / decoder.averageCharsPerByte().toDouble).toInt
    val charBufferSize = 128

    _.repeatPull[String] {
      _.unconsN(charBufferSize * avgBytesPerChar, allowFewer = true).flatMap {
        case None =>
          val charBuffer = CharBuffer.allocate(1)
          decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
          decoder.flush(charBuffer)
          val outputString = charBuffer.flip().toString
          if (outputString.isEmpty) Pull.done.as(None)
          else Pull.output1(outputString).as(None)
        case Some((chunk, stream)) =>
          if (chunk.nonEmpty) {
            val chunkWithoutBom = skipUtf8ByteOrderMark(chunk)
            val bytes = chunkWithoutBom.toArray
            val byteBuffer = ByteBuffer.wrap(bytes)
            val charBuffer = CharBuffer.allocate(bytes.length * maxCharsPerByte)
            decoder.decode(byteBuffer, charBuffer, false)
            val nextStream = stream.consChunk(Chunk.byteBuffer(byteBuffer.slice()))
            Pull.output1(charBuffer.flip().toString).as(Some(nextStream))
          } else
            Pull.output(Chunk.empty[String]).as(Some(stream))
      }
    }
  }

  /** Converts ASCII encoded byte stream to a stream of `String`. */
  private[http4s] def asciiDecode[F[_]](implicit
      F: ApplicativeError[F, Throwable]): Pipe[F, Byte, String] =
    _.chunks.through(asciiDecodeC)

  private def asciiCheck(b: Byte) = 0x80 & b

  /** Converts ASCII encoded `Chunk[Byte]` inputs to `String`. */
  private[http4s] def asciiDecodeC[F[_]](implicit
      F: ApplicativeError[F, Throwable]): Pipe[F, Chunk[Byte], String] = { in =>
    def tailRecAsciiCheck(i: Int, bytes: Array[Byte]): Stream[F, String] =
      if (i == bytes.length)
        Stream.emit(new String(bytes, StandardCharsets.US_ASCII))
      else if (asciiCheck(bytes(i)) == 0x80)
        Stream.raiseError[F](
          new IllegalArgumentException("byte stream is not encodable as ascii bytes"))
      else
        tailRecAsciiCheck(i + 1, bytes)

    in.flatMap(c => tailRecAsciiCheck(0, c.toArray))
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  @deprecated("For internal use only. Will be removed from public API.", "0.20.22")
  def bug(message: String): AssertionError = org.http4s.internal.bug(message)

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

  /* This is nearly identical to the hashCode of java.lang.String, but converting
   * to lower case on the fly to avoid copying `value`'s character storage.
   */
  @deprecated("Not a good hash. Prefer org.typelevel.ci.CIString.", "0.21.5")
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

  @deprecated("Replaced by org.typelevel.ci.CIString", "1.0.0-M1")
  type CaseInsensitiveString = CIString
  @deprecated("Replaced by org.typelevel.ci.CIString", "1.0.0-M1")
  val CaseInsensitiveString = CIString
}
