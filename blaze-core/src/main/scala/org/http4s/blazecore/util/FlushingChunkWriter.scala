package org.http4s
package blazecore
package util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import scala.concurrent._

import fs2._
import org.http4s.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

private[http4s] class FlushingChunkWriter(pipe: TailStage[ByteBuffer],
                         trailer: Task[Headers])(implicit ec: ExecutionContext)
              extends ChunkEntityBodyWriter(pipe, trailer) {
  override def writeHeaders(headerWriter: StringWriter): Future[Unit] = {
    // It may be a while before we get another chunk, so we flush now
    pipe.channelWrite(Http1Writer.headersToByteBuffer(headerWriter.result)).map { _ =>
      // Set to non-null to indicate that the headers aren't closed yet.  We still
      // need to write a Content-Length or Transfer-Encoding
      pendingHeaders = new StringWriter
    }
  }
}
