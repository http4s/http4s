package org.http4s
package blazecore
package util

import cats.implicits._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent._

import fs2._
import org.http4s.blaze.http.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.http.http20.NodeMsg._
import org.http4s.syntax.async._
import org.http4s.util.StringWriter
import org.http4s.util.chunk._

private[http4s] trait Http1Writer[F[_]] extends EntityBodyWriter[F] {
  final def write(headerWriter: StringWriter, body: EntityBody[F]): F[Boolean] =
    F.fromFuture(writeHeaders(headerWriter)).attempt.flatMap {
      case Left(t) => body.drain.run.map(_ => true)
      case Right(_) => writeEntityBody(body)
    }

  /* Writes the header.  It is up to the writer whether to flush immediately or to
   * buffer the header with a subsequent chunk. */
  def writeHeaders(headerWriter: StringWriter): Future[Unit]
}

private[util] object Http1Writer {
  def headersToByteBuffer(headers: String): ByteBuffer =
    ByteBuffer.wrap(headers.getBytes(StandardCharsets.ISO_8859_1))
}
