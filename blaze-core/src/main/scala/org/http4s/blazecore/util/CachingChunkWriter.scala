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

class CachingChunkWriter(pipe: TailStage[ByteBuffer],
                         trailer: Task[Headers],
                         bufferMaxSize: Int = 10*1024)(implicit ec: ExecutionContext)
              extends ChunkEntityBodyWriter(pipe, trailer) {

  private var bodyBuffer: Chunk[Byte] = null

  override def writeHeader(headerWriter: StringWriter): Future[Unit] = {
    pendingHeaders = headerWriter
    FutureUnit
  }

  private def addChunk(b: Chunk[Byte]): Chunk[Byte] = {
    if (bodyBuffer == null) bodyBuffer = b
    else bodyBuffer = Chunk.concatBytes(Seq(bodyBuffer, b))

    bodyBuffer
  }

  override protected def exceptionFlush(): Future[Unit] = {
    val c = bodyBuffer
    bodyBuffer = null
    if (c != null && !c.isEmpty) super.writeBodyChunk(c, true)  // TODO: would we want to writeEnd?
    else FutureUnit
  }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    val b = addChunk(chunk)
    bodyBuffer = null
    super.writeEnd(b)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    val c = addChunk(chunk)
    if (c.size >= bufferMaxSize || flush) { // time to flush
      bodyBuffer = null
      super.writeBodyChunk(c, true)
    }
    else FutureUnit    // Pretend to be done.
  }
}
