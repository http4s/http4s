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

package org.http4s.client.scaffold

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import io.netty.buffer.ByteBuf
import java.net.URI
import org.log4s.getLogger

class HandlersToNettyAdapter(handlers: Map[(HttpMethod, String), Handler]) extends SimpleChannelInboundHandler[HttpObject] {

    private val logger = getLogger(this.getClass)

    private var currentRequest: HttpRequest = null
    private var currentHandler: Handler = null

    private val rejectionHandler: Handler = (ctx: ChannelHandlerContext, request: HttpRequest) =>
    HandlerHelpers.sendResponse(ctx, request, HttpResponseStatus.BAD_REQUEST)

    override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

      msg match {
        case request: HttpRequest =>
          logger.trace(
            s"Recieved [${request.method()}] [${request.uri()}] request from [${ctx.channel.remoteAddress()}].")
          if (HttpUtil.is100ContinueExpected(request)) {
            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE))
          } else {
            currentRequest = request
            currentHandler = handlers.getOrElse(
              (
                request.method(),
                new URI(request.uri())
                  .getPath()
              ),
              rejectionHandler)
          }
        case _ =>
      }

      msg match {
        case content: HttpContent =>
          logger.trace("Recieved content.")
          currentHandler.onContent(ctx, currentRequest, content)
        case _ =>
      }

      msg match {
        case content: LastHttpContent =>
          logger.trace("Request finished.")
          currentHandler.onFinish(ctx, currentRequest)
          currentRequest = null
          currentHandler = null
        case _ =>
      }
    }

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = ctx.flush()

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      logger.warn(cause)("")
      ctx.close()
    }

}

trait Handler {

  def onContent(ctx: ChannelHandlerContext, request: HttpRequest, content: HttpContent): Unit = ()

  def onFinish(ctx: ChannelHandlerContext, request: HttpRequest): Unit

}

object HandlerHelpers {
  def sendResponse(
      ctx: ChannelHandlerContext,
      request: HttpRequest,
      status: HttpResponseStatus,
      content: ByteBuf = Unpooled.buffer(0),
      closeConnection: Boolean = false): ChannelFuture = {
    val response = new DefaultFullHttpResponse(
      HTTP_1_1,
      status,
      content
    )
    if (HttpUtil.isKeepAlive(request)) {
      response.headers().set(CONTENT_LENGTH, response.content().readableBytes())
      response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
      if (closeConnection) ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
      else ctx.writeAndFlush(response)
    } else {
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }
  }

  def utf8Text(s: String): ByteBuf = Unpooled.copiedBuffer(s, CharsetUtil.UTF_8)
}
