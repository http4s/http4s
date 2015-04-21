package org.http4s.blaze.util


import org.http4s.blaze.http.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.http.http20.NodeMsg._

import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}


class Http2Writer(tail: TailStage[Http2Msg],
                  headers:Headers,
                  protected val ec: ExecutionContext) extends ProcessWriter {

  private var sentHeaders = false

  override protected def writeEnd(chunk: ByteVector): Future[Unit] = {
    if (sentHeaders) tail.channelWrite(DataFrame(isLast = true, data = chunk.toByteBuffer))
    else {
      sentHeaders = true
      if (chunk.isEmpty) tail.channelWrite(HeadersFrame(None, true, headers))
        else tail.channelWrite(HeadersFrame(None, false, headers)::DataFrame(true, chunk.toByteBuffer)::Nil)
      }
    }

  override protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = {
    if (chunk.isEmpty) Future.successful(())
    else {
      if (sentHeaders) tail.channelWrite(DataFrame(isLast = false, data = chunk.toByteBuffer))
      else {
        sentHeaders = true
        tail.channelWrite(HeadersFrame(None, false, headers)::DataFrame(isLast = false, data = chunk.toByteBuffer)::Nil)
      }
    }
  }
}
