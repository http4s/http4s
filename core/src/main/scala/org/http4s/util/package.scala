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
    val avgBytesPerChar = math.ceil(1.0 / decoder.averageCharsPerByte().toDouble).toInt
    val charBufferSize = 128

    _.repeatPull[String] {
      _.awaitN(charBufferSize * avgBytesPerChar, allowFewer = true).optional.flatMap {
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
          val charBuffer = CharBuffer.allocate(byteVector.size.toInt * maxCharsPerByte)
          decoder.decode(byteBuffer, charBuffer, false)
          val nextByteVector = ByteVector.view(byteBuffer.slice)
          val nextHandle = handle.push(Chunk.bytes(nextByteVector.toArray))
          Pull.output1(charBuffer.flip().toString) as nextHandle
      }
    }
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  private[http4s] def tryCatchNonFatal[A](f: => A): Attempt[A] =
    try Right(f)
    catch { case NonFatal(t) => Left(t) }

  @deprecated("Moved to org.http4s.syntax.StringOps", "0.16")
  type CaseInsensitiveStringOps = org.http4s.syntax.StringOps

  @deprecated("Moved to org.http4s.syntax.StringSyntax", "0.16")
  type CaseInsensitiveStringSyntax = org.http4s.syntax.StringSyntax
}
