package org.http4s

import java.nio.ByteBuffer

import fs2.Stream._
import fs2._
import fs2.util.Attempt

import scala.util.control.NonFatal

package object util {
  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = { input =>
    val decoder = charset.nioCharset.newDecoder
    input.repeatPull[String] {
      _.receive {
        case (chunk, handle) =>
          val charBuffer = decoder.decode(ByteBuffer.wrap(chunk.toBytes.toArray))
          Pull.output1(charBuffer.toString) as handle
      }
    }
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  private[http4s] def tryCatchNonFatal[A](f: => A): Attempt[A] =
    try Right(f)
    catch { case NonFatal(t) => Left(t) }
}
