/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package blazecore
package util

import cats.effect.Async
import cats.implicits._
import fs2._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1

import cats.effect.std.Dispatcher
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

import scala.concurrent._

private[util] object ChunkWriter {
  val CRLFBytes = "\r\n".getBytes(ISO_8859_1)
  private[this] val CRLFBuffer = ByteBuffer.wrap(CRLFBytes).asReadOnlyBuffer()
  def CRLF = CRLFBuffer.duplicate()

  private[this] val chunkEndBuffer =
    ByteBuffer.wrap("0\r\n\r\n".getBytes(ISO_8859_1)).asReadOnlyBuffer()
  def ChunkEndBuffer = chunkEndBuffer.duplicate()

  val TransferEncodingChunkedString = "Transfer-Encoding: chunked\r\n\r\n"
  private[this] val TransferEncodingChunkedBytes =
    "Transfer-Encoding: chunked\r\n\r\n".getBytes(ISO_8859_1)
  private[this] val transferEncodingChunkedBuffer =
    ByteBuffer.wrap(TransferEncodingChunkedBytes).asReadOnlyBuffer
  def TransferEncodingChunked = transferEncodingChunkedBuffer.duplicate()

  def writeTrailer[F[_]](pipe: TailStage[ByteBuffer], trailer: F[Headers])(implicit
      F: Async[F],
      ec: ExecutionContext,
      D: Dispatcher[F]): Future[Boolean] = {
    val f = trailer.map { trailerHeaders =>
      if (trailerHeaders.nonEmpty) {
        val rr = new StringWriter(256)
        rr << "0\r\n" // Last chunk
        trailerHeaders.foreach { h =>
          h.render(rr) << "\r\n"; ()
        } // trailers
        rr << "\r\n" // end of chunks
        ByteBuffer.wrap(rr.result.getBytes(ISO_8859_1))
      } else ChunkEndBuffer
    }
    for {
      buffer <- D.unsafeToFuture(f)
      _ <- pipe.channelWrite(buffer)
    } yield false
  }

  def writeLength(length: Long): ByteBuffer = {
    val bytes = length.toHexString.getBytes(ISO_8859_1)
    val b = ByteBuffer.allocate(bytes.length + 2)
    b.put(bytes).put(CRLFBytes).flip()
    b
  }

  def encodeChunk(chunk: Chunk[Byte], last: List[ByteBuffer]): List[ByteBuffer] =
    writeLength(chunk.size.toLong) :: chunk.toByteBuffer :: CRLF :: last
}
