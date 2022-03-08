/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package blazecore
package util

import cats.effect._
import cats.effect.std.Dispatcher
import fs2._
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1
import scala.collection.mutable.Buffer
import scala.concurrent._

private[http4s] class CachingChunkWriter[F[_]](
    pipe: TailStage[ByteBuffer],
    trailer: F[Headers],
    bufferMaxSize: Int,
    omitEmptyContentLength: Boolean,
)(implicit
    protected val F: Async[F],
    protected val ec: ExecutionContext,
    protected val dispatcher: Dispatcher[F],
) extends Http1Writer[F] {
  import ChunkWriter._

  private[this] var pendingHeaders: StringWriter = _
  private[this] val bodyBuffer: Buffer[Chunk[Byte]] = Buffer()
  private[this] var size: Int = 0

  override def writeHeaders(headerWriter: StringWriter): Future[Unit] = {
    pendingHeaders = headerWriter
    FutureUnit
  }

  private def addChunk(chunk: Chunk[Byte]): Unit =
    if (chunk.nonEmpty) {
      bodyBuffer += chunk
      size += chunk.size
    }

  private def toChunkAndClear: Chunk[Byte] = {
    val chunk = if (bodyBuffer.isEmpty) {
      Chunk.empty
    } else if (bodyBuffer.size == 1) {
      bodyBuffer.head
    } else {
      Chunk.concat(bodyBuffer)
    }
    bodyBuffer.clear()
    size = 0
    chunk
  }

  override protected def exceptionFlush(): Future[Unit] =
    if (bodyBuffer.nonEmpty) {
      val c = toChunkAndClear
      pipe.channelWrite(encodeChunk(c, Nil))
    } else {
      FutureUnit
    }

  def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    addChunk(chunk)
    val c = toChunkAndClear
    doWriteEnd(c)
  }

  private def doWriteEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    val f =
      if (pendingHeaders != null) { // This is the first write, so we can add a body length instead of chunking
        val h = pendingHeaders
        pendingHeaders = null

        if (!chunk.isEmpty) {
          val body = chunk.toByteBuffer
          h << s"Content-Length: ${body.remaining()}\r\n\r\n"

          // Trailers are optional, so dropping because we have no body.
          val hbuff = ByteBuffer.wrap(h.result.getBytes(ISO_8859_1))
          pipe.channelWrite(hbuff :: body :: Nil)
        } else {
          if (!omitEmptyContentLength)
            h << s"Content-Length: 0\r\n"
          h << "\r\n"
          val hbuff = ByteBuffer.wrap(h.result.getBytes(ISO_8859_1))
          pipe.channelWrite(hbuff)
        }
      } else if (!chunk.isEmpty) {
        writeBodyChunk(chunk, true).flatMap { _ =>
          writeTrailer(pipe, trailer)
        }
      } else {
        writeTrailer(pipe, trailer)
      }

    f.map(Function.const(false))
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    addChunk(chunk)
    if (size >= bufferMaxSize || flush) { // time to flush
      val c = toChunkAndClear
      pipe.channelWrite(encodeChunk(c, Nil))
    } else FutureUnit // Pretend to be done.
  }

  private def encodeChunk(chunk: Chunk[Byte], last: List[ByteBuffer]): List[ByteBuffer] = {
    val list = ChunkWriter.encodeChunk(chunk, last)
    if (pendingHeaders != null) {
      pendingHeaders << TransferEncodingChunkedString
      val b = ByteBuffer.wrap(pendingHeaders.result.getBytes(ISO_8859_1))
      pendingHeaders = null
      b :: list
    } else list
  }
}
