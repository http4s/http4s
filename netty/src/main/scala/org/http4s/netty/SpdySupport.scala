package org.http4s.netty

import org.http4s.{Request, Response}
import io.netty.channel.ChannelHandlerContext
import scalaz.concurrent.Task

import java.net.InetAddress

import io.netty.handler.codec.spdy._

/**
 * @author Bryce Anderson
 *         Created on 11/28/13
 */
trait SpdySupport extends NettySupport[SpdyFrame] {
  type RequestType = SpdySynStreamFrame

  override protected def toRequest(ctx: ChannelHandlerContext, req: SpdySynStreamFrame): Request = ???

  override protected def renderResponse(ctx: ChannelHandlerContext, req: SpdySynStreamFrame, response: Response): Task[Unit] = ???
}
