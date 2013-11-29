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
                  spdyversion: Int,
                  val localAddress: InetSocketAddress,
                  val remoteAddress: InetSocketAddress) extends NettySupport[SpdyFrame, SpdySynStreamFrame]
                        with utils.SpdyStreamManager {

  /** Serves as a repository for active streams
    * If a stream is canceled, it get removed from the map. The allows the client to reject
    * data that it knows is already cached and this backend abort the outgoing stream
    */
  private val activeStreams = new ConcurrentHashMap[Int, ChunkHandler]

  val serverSoftware = ServerSoftware("HTTP4S / Netty / SPDY")

  val service = PushSupport(srvc)

  private def streamFinished(id: Int) {
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

    val manager = new ChannelManager(ctx)
    assert(activeStreams.put(req.getStreamId, manager) == null)
    Request(prelude, getStream(manager))
  }

  private def getStatus(response: Response) = {
    new HttpResponseStatus(response.prelude.status.code, response.prelude.status.reason)
  }

  private def copyResponse(spdyresp: SpdyHeadersFrame, response: Response): Int = {
    SpdyHeaders.setStatus(spdyversion, spdyresp, getStatus(response))
    SpdyHeaders.setVersion(spdyversion, spdyresp, HTTP_1_1)

    var size = -1
    response.prelude.headers.foreach { header =>
      if (header.is(`Content-Length`)) size = header.parsed.asInstanceOf[`Content-Length`].length
      spdyresp.headers.set(header.name.toString, header.value)
    }
    size
  }

  override protected def renderResponse(ctx: ChannelHandlerContext, req: SpdySynStreamFrame, response: Response): Task[Unit] = {
    logger.trace("Rendering response.")
    val streamid = req.getStreamId
    val resp = new DefaultSpdySynReplyFrame(streamid)
    val size = copyResponse(resp, response)

    val t: Task[Any] = response.attributes.get(PushSupport.pushResponsesKey) match {
      case None => Task.now(())

      case Some(t) => // Push the heads of all the push resources. Sync on the Task
        t.map (_.foreach( r => pushResource(r, req, ctx)))
    }

    t.map{ _ =>
      ctx.channel.write(resp)
      renderBody(req.getStreamId, response.body, ctx, size <= 0)
    }
  }

  private def pushResource(push: PushResponse, req: SpdySynStreamFrame, ctx: ChannelHandlerContext) {

    val id = newPushStreamID()
    val parentid = req.getStreamId
    val host = SpdyHeaders.getHost(req)
    val scheme = SpdyHeaders.getScheme(spdyversion, req)
    val response = push.resp
    logger.trace(s"Pushing content on stream $id associated with stream $parentid, url ${push.location}")

    assert(activeStreams.put(id, new ClosedChunkHandler) == null) // Add a dummy Handler to signal that the stream is active

                                            // TODO: Default to priority 2. Fix this?
    val msg = new DefaultSpdySynStreamFrame(id, parentid, 2.toByte)
    SpdyHeaders.setUrl(spdyversion, msg, push.location)
    SpdyHeaders.setScheme(spdyversion, msg, scheme)
    SpdyHeaders.setHost(msg, host)
    val size = copyResponse(msg, response)

    ctx.channel().write(msg)
    renderBody(id, response.body, ctx, size <= 0)
  }

  // TODO: need to honor Window size messages to maintain flow control
  // TODO: need to make this lazy on flushes so we don't end up overflowing the netty outbound buffers
  private def renderBody(streamid: Int, body: HttpBody, ctx: ChannelHandlerContext, flush: Boolean) {
    logger.trace(s"Rendering SPDY body on stream $streamid")
    // Should have a ChunkHandler ready for us, start pushing stuff through either a Process1 or a sink
    val channel = ctx.channel()
    var stillOpen = true
    def folder(chunk: Chunk): Process1[Chunk, Unit] = {

      // Make sure our stream is still open, if not, halt.
      if (activeStreams.containsKey(streamid)) chunk match {
        case c: BodyChunk =>
          val out = new DefaultSpdyDataFrame(streamid, Unpooled.wrappedBuffer(chunk.toArray))
          if (flush) channel.writeAndFlush(out)
          else channel.write(out)
          await(Get[Chunk])(folder)

        case c: TrailerChunk =>
          val respTrailer = new DefaultSpdyHeadersFrame(streamid)
          for ( h <- c.headers ) respTrailer.headers().set(h.name.toString, h.value)
          respTrailer.setLast(true)
          stillOpen = false
          channel.writeAndFlush(respTrailer)
          halt
      }
      else {
        logger.trace(s"Stream $streamid canceled. Dropping rest of frames.")
        halt
      }  // Stream has been closed
    }

    val process1: Process1[Chunk, Unit] = await(Get[Chunk])(folder)

    body.pipe(process1).run.runAsync {
      case \/-(_) =>
        logger.trace("Finished stream.")
      if (stillOpen) {
        val closer = new DefaultSpdyDataFrame(streamid)
        closer.setLast(true)
        channel.writeAndFlush(closer)
      }
      streamFinished(streamid)

      case -\/(t) => exceptionCaught(ctx, t)
    }
  }

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

  /** deal with incoming messages which belong to this service
    * @param ctx ChannelHandlerContext of the pipeline
    * @param msg received message
    */
  def onHttpMessage(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case req: SpdySynStreamFrame =>
      logger.trace(s"Received Request frame with id ${req.getStreamId}")
      streamID(req.getStreamId)
      startHttpRequest(ctx, req)

    case headers: SpdyHeadersFrame =>
      logger.error("Header frames not supported yet. Dropping.")
      if (headers.isLast) Option(activeStreams.get(headers.getStreamId)).foreach(_.close())

    case chunk: SpdyDataFrame =>
      val handler = activeStreams.get(chunk.getStreamId)
      if (handler!= null){
        handler.enque(buffToBodyChunk(chunk.content()))
        if (chunk.isLast) handler.close()
      }
      else  {
        logger.debug(s"Received chunk on stream ${chunk.getStreamId}: no handler.")
        val msg = new DefaultSpdyRstStreamFrame(chunk.getStreamId, 5)  // 5: Cancel the stream
        ctx.channel().writeAndFlush(msg)
      }

    case p: SpdyPingFrame =>
      if (p.getId % 2 == 1) {   // Must ignore Pings with even number id
        logger.trace(s"Sending ping reply frame with id ${p.getId}")
        val ping = new DefaultSpdyPingFrame(p.getId)
        ctx.channel().writeAndFlush(ping)
      }

    case s: SpdySettingsFrame => handleSpdySettings(s)



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

  // TODO: Need to implement a Spdy HttpVersion
  private def getProtocol(req: SpdySynStreamFrame) = HttpVersion.`Http/1.1`
}
