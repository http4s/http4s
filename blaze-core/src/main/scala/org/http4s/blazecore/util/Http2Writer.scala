package org.http4s
package blazecore
package util

import org.http4s.blaze.http.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.http.http20.NodeMsg._

import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}


class Http2Writer(tail: TailStage[Http2Msg],
                  private var headers: Headers,
                  protected val ec: ExecutionContext) extends ProcessWriter {

  override protected def writeEnd(chunk: ByteVector): Future[Boolean] = {
    val f = if (headers == null) tail.channelWrite(DataFrame(true, chunk.toByteBuffer))
    else {
      val hs = headers
      headers = null
      if (chunk.isEmpty) tail.channelWrite(HeadersFrame(None, true, hs))
      else tail.channelWrite(HeadersFrame(None, false, hs)::DataFrame(true, chunk.toByteBuffer)::Nil)
    }

    f.map(Function.const(false))(ec)
  }

  override protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = {
    if (chunk.isEmpty) Future.successful(())
    else {
      if (headers == null) tail.channelWrite(DataFrame(false, chunk.toByteBuffer))
      else {
        val hs = headers
        headers = null
        tail.channelWrite(HeadersFrame(None, false, hs)::DataFrame(false, chunk.toByteBuffer)::Nil)
      }
    }
  }
}
