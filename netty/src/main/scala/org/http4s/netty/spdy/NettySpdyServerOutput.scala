package org.http4s
package netty
package spdy

import org.http4s.{TrailerChunk, BodyChunk}
import scala.concurrent.Future
import io.netty.channel.{ChannelHandlerContext, Channel}
import io.netty.handler.codec.spdy.{DefaultSpdyHeadersFrame, DefaultSpdyDataFrame}
import io.netty.buffer.Unpooled

/**
 * @author Bryce Anderson
 *         Created on 12/10/13
 */
trait NettySpdyServerOutput extends StreamOutput {

  type A = Channel

  protected def ctx: ChannelHandlerContext

  /** Write the end of a stream
    *
    * @param streamid ID of the stream to write to
    * @param chunk last body buffer
    * @param t optional Trailer
    * @return a future which will resolve once the data has made ot past the window
    */
  def writeStreamEnd(streamid: Int, chunk: BodyChunk, t: Option[TrailerChunk]): Future[A] = {
    if (t.isDefined) {
      if (chunk.length > 0) {
        val buff = Unpooled.wrappedBuffer(chunk.toArray)
        val msg = new DefaultSpdyDataFrame(streamid, buff)
        ctx.write(msg)
      }
      val msg = new DefaultSpdyHeadersFrame(streamid)
      t.get.headers.foreach(h => msg.headers().add(h.name.toString, h.value))
      msg.setLast(true)
      ctx.writeAndFlush(msg)
    }
    else {
      val buff = Unpooled.wrappedBuffer(chunk.toArray)
      val msg = new DefaultSpdyDataFrame(streamid, buff)
      msg.setLast(true)
      ctx.writeAndFlush(msg)
    }
  }

  /** Write data to the stream
    *
    * @param streamid ID of the stream to write to
    * @param chunk buffer of data to write
    * @return a future which will resolve once the data has made ot past the window
    */
  def writeStreamChunk(streamid: Int, chunk: BodyChunk, flush: Boolean): Future[A] = {
    val buff = Unpooled.wrappedBuffer(chunk.toArray)
    val msg = new DefaultSpdyDataFrame(streamid, buff)
    if (flush) ctx.writeAndFlush(msg)
    else ctx.write(msg)
  }
}
