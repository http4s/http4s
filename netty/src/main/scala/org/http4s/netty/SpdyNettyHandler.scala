package org.http4s
package netty

import org.http4s.netty.utils.{ChunkHandler, ClosedChunkHandler}

import scala.util.control.Exception.allCatch

import java.net.{URI, InetSocketAddress}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

import io.netty.handler.codec.spdy._
import io.netty.channel.{ChannelFutureListener, Channel, ChannelHandlerContext}
import io.netty.util.ReferenceCountUtil
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.http4s.PushSupport.PushResponse
import io.netty.buffer.Unpooled
import org.http4s.Header.`Content-Length`

import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.{-\/, \/-}


/**
 * @author Bryce Anderson
 *         Created on 11/28/13
 */
class SpdyNettyHandler(srvc: HttpService,
                  val spdyversion: Int,
                  val localAddress: InetSocketAddress,
                  val remoteAddress: InetSocketAddress) extends NettySupport[SpdyFrame, SpdySynStreamFrame]
                        with utils.SpdyStreamManager {

  /** Serves as a repository for active streams
    * If a stream is canceled, it get removed from the map. The allows the client to reject
    * data that it knows is already cached and this backend abort the outgoing stream
    */
  private val activeStreams = new ConcurrentHashMap[Int, SpdyStreamContext]

  def registerStream(ctx: SpdyStreamContext): Boolean = {
    activeStreams.put(ctx.streamid, ctx) == null
  }

  val serverSoftware = ServerSoftware("HTTP4S / Netty / SPDY")

  val service = PushSupport(srvc)

  def streamFinished(id: Int) {
    logger.trace(s"Stream $id finished.")
    if (activeStreams.remove(id) == null) logger.warn(s"Stream id $id for address $remoteAddress was empty.")
  }

  override protected def toRequest(ctx: ChannelHandlerContext, req: SpdySynStreamFrame): Request = {
    val uri = new URI(SpdyHeaders.getUrl(spdyversion, req))
    val scheme = Option(SpdyHeaders.getScheme(spdyversion, req)).getOrElse{
      logger.warn(s"${remoteAddress}: Request doesn't have scheme header")
      "https"
    }

    val servAddr = ctx.channel.remoteAddress.asInstanceOf[InetSocketAddress]
    val prelude = RequestPrelude(
      requestMethod = Method(SpdyHeaders.getMethod(spdyversion, req).name),
      //scriptName = contextPath,
      pathInfo = uri.getRawPath,
      queryString = uri.getRawQuery,
      protocol = getProtocol(req),
      headers = toHeaders(req.headers),
      urlScheme = HttpUrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = serverSoftware,
      remote = remoteAddress.getAddress // TODO using remoteName would trigger a lookup
    )

    val chunkmanager = new SpdyChunkHandler(5, 3, req.getStreamId)
    val streamctx = new SpdyStreamContext(this, req.getStreamId, chunkmanager)
    assert(activeStreams.put(req.getStreamId, streamctx) == null)
    Request(prelude, getStream(streamctx.manager))
  }

  override protected def renderResponse(ctx: ChannelHandlerContext, req: SpdySynStreamFrame, response: Response): Task[Int] = {
    val handler = activeStreams.get(req.getStreamId)
    if (handler != null) handler.renderResponse(ctx, req, response)
    else sys.error("Newly created stream disappeared!")
  }

  // TODO: need to honor Window size messages to maintain flow control
  // TODO: need to make this lazy on flushes so we don't end up overflowing the netty outbound buffers
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    try {
      logger.error(s"Exception on connection with $remoteAddress", cause)
      val it = activeStreams.values().iterator()
      while (it.hasNext) { val handler = it.next(); handler.kill(cause) }
      activeStreams.clear()
      if (ctx.channel().isOpen) {  // Send GOAWAY frame to signal disconnect if we are still connected
        val goaway = new DefaultSpdyGoAwayFrame(lastOpenedStream, 2) // Internal Error
        allCatch(ctx.channel().writeAndFlush(goaway).addListener(ChannelFutureListener.CLOSE))
      }
    } catch {    // Don't end up in an infinite loop of exceptions
      case t: Throwable =>
        val causestr = if (cause != null) cause.getStackTraceString else "NULL."
        logger.error("Caught exception in exception handling: " + causestr, t)
    }
  }

  /** Forwards messages to the appropriate SpdyStreamContext
    * @param ctx ChannelHandlerContext of this channel
    * @param msg SpdyStreamFrame to be forwarded
    */
  private def forwardMsg(ctx: ChannelHandlerContext, msg: SpdyStreamFrame) {
    val handler = activeStreams.get(msg.getStreamId)
    if (handler!= null) handler.spdyMessage(msg)
    else  {
      logger.debug(s"Received chunk on stream ${msg.getStreamId}: no handler.")
      val rst = new DefaultSpdyRstStreamFrame(msg.getStreamId, 5)  // 5: Cancel the stream
      ctx.channel().writeAndFlush(rst)
    }
  }

  /** deal with incoming messages which belong to this service
    * @param ctx ChannelHandlerContext of the pipeline
    * @param msg received message
    */
  def onHttpMessage(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case req: SpdySynStreamFrame =>
      logger.trace(s"Received Request frame with id ${req.getStreamId}")
      streamID(req.getStreamId)
      startHttpRequest(ctx, req)

    case p: SpdyPingFrame =>
      if (p.getId % 2 == 1) {   // Must ignore Pings with even number id
        logger.trace(s"Sending ping reply frame with id ${p.getId}")
        val ping = new DefaultSpdyPingFrame(p.getId)
        ctx.channel().writeAndFlush(ping)
      }

    case s: SpdySettingsFrame => handleSpdySettings(s)

    case msg: SpdyStreamFrame => forwardMsg(ctx, msg)

    case msg => logger.warn("Received unknown message type: " + msg + ". Dropping.")
  }

  private def handleSpdySettings(settings: SpdySettingsFrame) {
    import SpdySettingsFrame._
    logger.trace(s"Received SPDY settings frame: $settings")

    val maxStreams = settings.getValue(SETTINGS_MAX_CONCURRENT_STREAMS)
    if (maxStreams > 0) setMaxStreams(maxStreams)

    val initWindow = settings.getValue(SETTINGS_INITIAL_WINDOW_SIZE)
    // TODO: Deal with window sizes and buffering. http://dev.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3#TOC-2.6.8-WINDOW_UPDATE
    if (initWindow > 0) logger.warn(s"Ignoring initial window size of $initWindow")
  }

  class SpdyChunkHandler(high: Int, low: Int, streamid: Int) extends ChunkHandler(high, low) {
    def onQueueFull(): Unit = logger.warn(s"Inbound queue full for stream $streamid")

    def onQueueReady(): Unit = logger.trace(s"Queue ready for stream $streamid")
  }

  // TODO: Need to implement a Spdy HttpVersion
  private def getProtocol(req: SpdySynStreamFrame) = HttpVersion.`Http/1.1`
}
