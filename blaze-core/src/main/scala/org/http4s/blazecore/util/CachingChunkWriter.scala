package org.http4s
package blazecore
package util

import java.nio.ByteBuffer

import cats.effect._
import fs2._
import org.http4s.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

import scala.concurrent._

class CachingChunkWriter[F[_]](headers: StringWriter,
                               pipe: TailStage[ByteBuffer],
                               trailer: F[Headers],
                               bufferMaxSize: Int = 10*1024)
                              (implicit F: Effect[F], ec: ExecutionContext)
  extends ChunkEntityBodyWriter(headers, pipe, trailer) {

  private var bodyBuffer: Chunk[Byte] = _

  private def addChunk(b: Chunk[Byte]): Chunk[Byte] = {
    if (bodyBuffer == null) bodyBuffer = b
    else bodyBuffer = (bodyBuffer ++ b).toChunk
    bodyBuffer
  }

  override protected def exceptionFlush(): Future[Unit] = {
    val c = bodyBuffer
    bodyBuffer = null
    if (c != null && !c.isEmpty) super.writeBodyChunk(c, flush = true)  // TODO: would we want to writeEnd?
    else Future.successful(())
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
      super.writeBodyChunk(c, flush = true)
    }
    else Future.successful(())    // Pretend to be done.
  }
}
