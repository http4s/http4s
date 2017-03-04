package org.http4s

import java.nio.ByteBuffer

import fs2._
import fs2.util.Attempt

import scala.util.control.NonFatal

package object util {
  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = {
    val decoder = charset.nioCharset.newDecoder
    _.rechunkN(Int.MaxValue - 1, allowFewer = true)
      .chunks
      .map { chunk =>
        decoder.decode(ByteBuffer.wrap(chunk.toArray)).toString
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
