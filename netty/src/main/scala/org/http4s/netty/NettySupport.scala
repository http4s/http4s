package org.http4s
package netty

import io.netty.channel.{SimpleChannelInboundHandler, ChannelOption, ChannelHandlerContext}

import java.net.{InetSocketAddress, InetAddress}
import java.util.Map.Entry
import java.lang.Iterable

import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

import io.netty.buffer.ByteBuf
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.slf4j.Logging
import java.io.IOException

/**
 * @author Bryce Anderson
 *         Created on 11/28/13
 */

abstract class NettySupport[MsgType] extends SimpleChannelInboundHandler[MsgType] with Logging {

  type RequestType <: MsgType

  sealed trait StateChange
  case object Idle extends StateChange
  case object StartRequest extends StateChange

  def serverSoftware: ServerSoftware

  def service: HttpService

  def localAddress: InetSocketAddress

  def remoteAddress: InetAddress

  protected def toRequest(ctx: ChannelHandlerContext, req: RequestType): Request

  protected def renderResponse(ctx: ChannelHandlerContext, req: RequestType, response: Response): Task[Unit]

  /** Checks to make sure the handler is in the propper state for starting a request
    *
    * @return if it safe to proceed
    */
  def changeState(next: StateChange): Boolean

  /** Disable the netty read process
    *
    * @param ctx channel handler context for this channel
    */
  protected def disableRead(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setOption(ChannelOption.AUTO_READ, new java.lang.Boolean(false))
  }

  /** Enable the netty read process
    *
    * @param ctx channel handler context for this channel
    */
  protected def enableRead(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setOption(ChannelOption.AUTO_READ, new java.lang.Boolean(true))
  }

  protected def startHttpRequest(ctx: ChannelHandlerContext, req: RequestType, rem: InetAddress) {
    logger.trace("Starting http request.")

    if (!changeState(StartRequest)) ??? // TODO: what should we do?

    val request = toRequest(ctx, req)
    val task = try service(request).flatMap(renderResponse(ctx, req, _))
    catch {
      // TODO: don't rely on exceptions for bad requests
      case m: MatchError => Status.NotFound(request.prelude)
      case e: Throwable => throw e
    }
    task.runAsync {
      case -\/(t) =>
        logger.error("Final Task results in an Exception.", t)
        ctx.channel().close()

      // Make sure we are allowing reading at the end of the request
      case \/-(_) =>  if (ctx.channel.isOpen) enableRead(ctx)
    }
  }

  /** Method for converting collections of headers to http4s HeaderCollection
    * @param headers headers container
    * @return a collection of the raw http4s headers
    */
  protected def toHeaders(headers: Iterable[Entry[String, String]]): HeaderCollection = {
    val lb = new ListBuffer[Header]
    val i = headers.iterator()
    while (i.hasNext) { val n = i.next(); lb += Header(n.getKey, n.getValue) }
    HeaderCollection(lb.result())
  }

  /** Convert a Netty ByteBuf to a BodyChunk
    *
    * @param buff netty ByteBuf which to convert
    * @return
    */
  protected def buffToBodyChunk(buff: ByteBuf): BodyChunk = {
    val arr = new Array[Byte](buff.readableBytes())
    buff.getBytes(0, arr)
    BodyChunk(arr)
  }
}

