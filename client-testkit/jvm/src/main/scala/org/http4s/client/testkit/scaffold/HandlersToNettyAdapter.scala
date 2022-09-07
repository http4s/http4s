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

package org.http4s.client.testkit.scaffold

import cats.effect.Sync
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.handler.codec.http.HttpHeaderNames._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil

import java.net.URI

private[http4s] object HandlersToNettyAdapter {
  val defaultFallbackHandler: Handler = (ctx: ChannelHandlerContext, _: HttpRequest) => {
    HandlerHelpers.sendResponse(ctx, HttpResponseStatus.NOT_FOUND)
    ()
  }

  def apply[F[_]](
      handlers: Map[(HttpMethod, String), Handler],
      fallbackHandler: Handler = defaultFallbackHandler,
  )(implicit F: Sync[F]): F[ChannelInboundHandler] =
    F.delay(new HandlersToNettyAdapter(handlers, fallbackHandler))
}

private[http4s] class HandlersToNettyAdapter private (
    handlers: Map[(HttpMethod, String), Handler],
    fallbackHandler: Handler,
) extends SimpleChannelInboundHandler[HttpObject] {

  private val logger = org.http4s.Platform.loggerFactory.getLoggerFromClass(this.getClass)

  private var currentRequest: HttpRequest = null
  private var currentHandler: Handler = null

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

    msg match {
      case request: HttpRequest =>
        logger
          .trace(
            s"Recieved [${request.method()}] [${request.uri()}] request from [${ctx.channel.remoteAddress()}]."
          )
          .unsafeRunSync()
        if (HttpUtil.is100ContinueExpected(request)) {
          ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE))
        } else {
          currentRequest = request
          currentHandler = handlers.getOrElse(
            (request.method(), new URI(request.uri()).getPath()),
            fallbackHandler,
          )
          currentHandler.onRequestStart(ctx, currentRequest)
        }
      case _ =>
    }

    msg match {
      case content: HttpContent =>
        logger.trace("Recieved content.").unsafeRunSync()
        currentHandler.onContent(ctx, currentRequest, content)
      case _ =>
    }

    msg match {
      case _: LastHttpContent =>
        logger.trace("Request finished.").unsafeRunSync()
        currentHandler.onRequestEnd(ctx, currentRequest)
        currentRequest = null
        currentHandler = null
      case _ =>
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush()
    ()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.warn(cause)("").unsafeRunSync()
    ctx.close()
    ()
  }

}

private[http4s] trait Handler {

  def onRequestStart(
      @deprecated("unused", "") ctx: ChannelHandlerContext,
      @deprecated("unused", "") request: HttpRequest,
  ): Unit = ()

  def onContent(
      @deprecated("unused", "") ctx: ChannelHandlerContext,
      @deprecated("unused", "") request: HttpRequest,
      @deprecated("unused", "") content: HttpContent,
  ): Unit = ()

  def onRequestEnd(ctx: ChannelHandlerContext, request: HttpRequest): Unit

}

private[http4s] object HandlerHelpers {

  def sendResponse(
      ctx: ChannelHandlerContext,
      status: HttpResponseStatus,
      content: ByteBuf = Unpooled.buffer(0),
      headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      closeConnection: Boolean = false,
  ): ChannelFuture = {
    val response = new DefaultFullHttpResponse(HTTP_1_1, status, content)
    response.headers().setAll(headers)
    response.headers().set(CONTENT_LENGTH, response.content().readableBytes())
    response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    if (closeConnection) {
      // disconnect sends FIN.
      ctx.writeAndFlush(response).addListener { (f: ChannelFuture) =>
        f.channel.disconnect()
        ()
      }
    } else {
      ctx.writeAndFlush(response)
    }
  }

  def sendChunkedResponseHead(
      ctx: ChannelHandlerContext,
      status: HttpResponseStatus,
      headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
  ): ChannelFuture = {
    val response = new DefaultHttpResponse(HTTP_1_1, status)
    response.headers().setAll(headers)
    response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    ctx.writeAndFlush(response)
  }

  def sendChunk(ctx: ChannelHandlerContext, content: ByteBuf): ChannelFuture =
    ctx.writeAndFlush(new DefaultHttpContent(content))

  def sendEmptyLastChunk(ctx: ChannelHandlerContext): ChannelFuture =
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)

  def utf8Text(s: String): ByteBuf =
    Unpooled.copiedBuffer(s, CharsetUtil.UTF_8)
}
