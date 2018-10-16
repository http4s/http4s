package org.http4s
package blazecore
package util

import cats.implicits._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.internal.fromFuture
import org.http4s.util.StringWriter
import org.log4s.getLogger
import scala.concurrent._

private[http4s] trait Http1Writer[F[_]] extends EntityBodyWriter[F] {
  final def write(headerWriter: StringWriter, body: EntityBody[F]): F[Boolean] =
    fromFuture(F.delay(writeHeaders(headerWriter))).attempt.flatMap {
      case Right(()) =>
        writeEntityBody(body)
      case Left(t) =>
        body.drain.compile.drain.handleError { t2 =>
          // Don't lose this error when sending the other
          // TODO implement with cats.effect.Bracket when we have it
          Http1Writer.logger.error(t2)("Error draining body")
        } *> F.raiseError(t)
    }

  /* Writes the header.  It is up to the writer whether to flush immediately or to
   * buffer the header with a subsequent chunk. */
  def writeHeaders(headerWriter: StringWriter): Future[Unit]
}

private[util] object Http1Writer {
  private val logger = getLogger

  def headersToByteBuffer(headers: String): ByteBuffer =
    ByteBuffer.wrap(headers.getBytes(StandardCharsets.ISO_8859_1))
}
