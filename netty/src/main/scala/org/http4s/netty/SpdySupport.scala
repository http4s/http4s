package org.http4s
package netty

import scalaz.concurrent.Task
import scalaz.stream.Process._

import scala.collection.mutable.HashMap

import java.net.{URI, InetSocketAddress}
import java.util.concurrent.atomic.AtomicInteger

import io.netty.handler.codec.spdy._
import io.netty.channel.{Channel, ChannelHandlerContext}
import io.netty.util.ReferenceCountUtil
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.http4s.PushSupport.PushResponse
import io.netty.buffer.Unpooled
import org.http4s.Header.`Content-Length`

/**
 * @author Bryce Anderson
 *         Created on 11/28/13
 */
class SpdySupport(srvc: HttpService,
                  spdyversion: Int,
                  val localAddress:
                  InetSocketAddress,
                  val remoteAddress: InetSocketAddress) extends NettySupport[SpdyFrame, SpdySynStreamFrame] {

  private val activeStreams = new HashMap[Int, ChunkHandler]

  private val currentID = new AtomicInteger(0)

  val serverSoftware = ServerSoftware("HTTP4S / Netty / SPDY")

  val service = PushSupport(srvc)

  private def newPushStreamID(): Int = {  // Need an odd integer
    def go(): Int = {
      val current = currentID.get()
      val inc = if (current % 2 == 0) 2 else 1
      val next = current + inc
      if (!currentID.compareAndSet(current, next)) go()
      else next
    }
    go()
  }

  private def streamID(id: Int) {
    def go(): Unit = {
      val current = currentID.get()
      if (id > current) {  // Valid ID
        if(!currentID.compareAndSet(current, id)) go()
        else ()
      }
      else logger.warn(s"StreamID $id is less than the current: $current")
    }
    go()
  }

  private def streamFinished(id: Int) {
    logger.trace(s"Stream $id finished. Removing.")
    if (activeStreams.remove(id).isEmpty)
      logger.warn(s"Stream id $id for address $remoteAddress was empty")
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
    activeStreams.put(req.getStreamId, manager)
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
      if (header.name == `Content-Length`)
        size = header.parsed.asInstanceOf[`Content-Length`].length
      spdyresp.headers.set(header.name.toString, header.value)
    }
    size
  }

  override protected def renderResponse(ctx: ChannelHandlerContext, req: SpdySynStreamFrame, response: Response): Task[Unit] = {

    val streamid = req.getStreamId
    val resp = new DefaultSpdySynReplyFrame(streamid)
    val size = copyResponse(resp, response)

    val t: Task[Any] = response.attributes.get(PushSupport.pushResponsesKey) match {
      case None => Task.now(())

      case Some(t) =>  t.flatMap {  // Push the heads of all the push resources. Sync on the Task
           _.foldLeft(Task.now(()))((t, r) => t.flatMap( _ => pushResource(r, req, ctx)))
            .map(_ => ctx.channel.write(resp))  // TODO: should we want for each write to finish?
         }
    }

    t.flatMap{ _ =>
      ctx.channel.write(resp).map { _ =>    // TODO: when should we flush?
        renderBody(req.getStreamId, response.body, ctx.channel(), size <= 0)
      }
    }
  }

  private def pushResource(push: PushResponse, req: SpdySynStreamFrame, ctx: ChannelHandlerContext): Task[Unit] = {

    val id = newPushStreamID()
    val parentid = req.getStreamId
    val host = SpdyHeaders.getHost(req)
    val scheme = SpdyHeaders.getScheme(spdyversion, req)
    val response = push.resp
    logger.trace(s"Pushing content on stream $id associated with stream $parentid, url ${push.location}")

                                            // TODO: Default to priority 2. Fix this?
    val msg = new DefaultSpdySynStreamFrame(id, parentid, 2.asInstanceOf[Byte])
    SpdyHeaders.setUrl(spdyversion, msg, push.location)
    SpdyHeaders.setScheme(spdyversion, msg, scheme)
    SpdyHeaders.setHost(msg, host)
    val size = copyResponse(msg, response)

    ctx.channel().write(msg).map{ c =>
      renderBody(id, response.body, c, size <= 0)
    }
  }

  private def renderBody(streamid: Int, body: HttpBody, channel: Channel, flush: Boolean) {
    // Should have a ChunkHandler ready for us, start pushing stuff through either a Process1 or a sink
    def folder(chunk: Chunk): Process1[Chunk, Unit] = {
      chunk match {
        case c: BodyChunk =>
          val out = new DefaultSpdyDataFrame(streamid, Unpooled.wrappedBuffer(chunk.toArray))
          if (flush) channel.writeAndFlush(out)
          else channel.write(out)
          await(Get[Chunk])(folder)

        case c: TrailerChunk =>
          val respTrailer = new DefaultSpdyHeadersFrame(streamid)
          for ( h <- c.headers ) respTrailer.headers().set(h.name.toString, h.value)
          respTrailer.setLast(true)
          val f = channel.writeAndFlush(respTrailer)
          halt
      }
    }

    val process1: Process1[Chunk, Unit] = await(Get[Chunk])(folder)

    body.pipe(process1).run.map { _ =>
      logger.trace("Finished stream.")
      if (activeStreams.remove(streamid).isEmpty)
        logger.warn("ChunkHandler removed before response stream was finished")
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
      if (headers.isLast) activeStreams.get(headers.getStreamId).foreach(_.close())

    case chunk: SpdyDataFrame =>  // Will throw an exception if not found
      activeStreams.get(chunk.getStreamId) match {
        case Some(handler) =>
          handler.enque(buffToBodyChunk(chunk.content()))
          if (chunk.isLast) handler.close()

        case None =>
          logger.info(s"Dropping chunk on stream ${chunk.getStreamId}: no handler.")
      }

    case msg => ReferenceCountUtil.retain(msg); ctx.fireChannelRead(msg)
  }

  // TODO: Need to implement a Spdy HttpVersion
  private def getProtocol(req: SpdySynStreamFrame) = HttpVersion.`Http/1.1`
}
