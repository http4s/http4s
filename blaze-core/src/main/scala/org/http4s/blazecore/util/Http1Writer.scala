package org.http4s
package blazecore
package util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent._

import fs2._
import org.http4s.blaze.http.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.http.http20.NodeMsg._
import org.http4s.util.StringWriter
import org.http4s.util.chunk._

trait Http1Writer extends EntityBodyWriter {
  final def write(headerWriter: StringWriter, body: EntityBody): Task[Boolean] =
    Task.fromFuture(writeHeader(headerWriter)).flatMap(_ => writeEntityBody(body))

  /* Writes the header.  It is up to the writer whether to flush immediately or to
   * buffer the header with a subsequent chunk. */
  def writeHeader(headerWriter: StringWriter): Future[Unit]
}

object Http1Writer {
  def headersToByteBuffer(headers: String): ByteBuffer =
    ByteBuffer.wrap(headers.getBytes(StandardCharsets.ISO_8859_1))
}
