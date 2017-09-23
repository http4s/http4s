package org.http4s

import fs2._
import fs2.interop.scodec.ByteVectorChunk
import java.nio.{ByteBuffer, CharBuffer}
import scala.concurrent.ExecutionContextExecutor
import scala.util.control.NonFatal
import scodec.bits.ByteVector

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
          val byteVector = ByteVector(segment.toVector)
          val byteBuffer = byteVector.toByteBuffer
          val charBuffer = CharBuffer.allocate(byteVector.size.toInt * maxCharsPerByte)
          decoder.decode(byteBuffer, charBuffer, false)
          val nextByteVector = ByteVector.view(byteBuffer.slice)
          val nextStream = stream.consChunk(ByteVectorChunk(nextByteVector))
          Pull.output1(charBuffer.flip().toString).as(Some(nextStream))
      }
    }
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(
      s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  private[http4s] def tryCatchNonFatal[A](f: => A): Either[Throwable, A] =
    try Right(f)
    catch { case NonFatal(t) => Left(t) }

  @deprecated("Moved to org.http4s.syntax.StringOps", "0.16")
  type CaseInsensitiveStringOps = org.http4s.syntax.StringOps

  @deprecated("Moved to org.http4s.syntax.StringSyntax", "0.16")
  type CaseInsensitiveStringSyntax = org.http4s.syntax.StringSyntax

  @deprecated(
    "Moved to org.http4s.execution.trampoline, is now merely a ExecutionContextExecutor.",
    "0.18.0-M2")
  val TrampolineExecutionContext: ExecutionContextExecutor = execution.trampoline
}
