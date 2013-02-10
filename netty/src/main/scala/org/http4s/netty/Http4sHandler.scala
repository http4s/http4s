package org.http4s
package netty

import concurrent.{Promise, Future, ExecutionContext}
import org.jboss.netty.channel._
import scala.util.control.Exception.allCatch
import org.jboss.netty.buffer.ChannelBuffers
import io.Codec
import play.api.libs.iteratee._
import java.net.{InetSocketAddress, SocketAddress, URI, InetAddress}
import collection.JavaConverters._
import HeaderNames._
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http
import http._
import org.http4s.Responder
import org.http4s.Request
import org.http4s.Header
import util.{Failure, Success}
import scala.language.{ implicitConversions, reflectiveCalls }
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Responder
import util.Failure
import util.Success
import org.http4s.Request
import org.http4s.Header
import java.util.concurrent.{TimeUnit, LinkedBlockingDeque, ConcurrentLinkedDeque}
import org.http4s.Responder
import util.Failure
import util.Success
import org.http4s.Request
import org.http4s.Header
import collection.mutable
import play.api.libs.iteratee.Enumerator.TreatCont0

object ScalaUpstreamHandler {
  sealed trait NettyHandlerMessage
  sealed trait NettyUpstreamHandlerEvent extends NettyHandlerMessage
  case class MessageReceived(ctx: ChannelHandlerContext, e: Any, remoteAddress: SocketAddress) extends NettyUpstreamHandlerEvent
  case class ExceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) extends NettyUpstreamHandlerEvent
  case class ChannelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelBound(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelUnbound(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelInterestChanged(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class WriteComplete(ctx: ChannelHandlerContext, e: WriteCompletionEvent) extends NettyUpstreamHandlerEvent
  case class ChildChannelOpen(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChildChannelClosed(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) extends NettyUpstreamHandlerEvent

  type UpstreamHandler = PartialFunction[NettyUpstreamHandlerEvent, Unit]
}
trait ScalaUpstreamHandler extends SimpleChannelUpstreamHandler with Logging {
  import ScalaUpstreamHandler._
  def handle: UpstreamHandler

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val m = MessageReceived(ctx, e.getMessage, e.getRemoteAddress)
    if (handle.isDefinedAt(m)) handle(m) else super.messageReceived(ctx, e)
  }
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    val m = ExceptionCaught(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.exceptionCaught(ctx, e)
  }
  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelOpen(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelOpen(ctx, e)
  }
  override def channelBound(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelBound(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelBound(ctx, e)
  }
  override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelConnected(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelConnected(ctx, e)
  }
  override def channelInterestChanged(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelInterestChanged(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelInterestChanged(ctx, e)
  }
  override def channelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelDisconnected(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelDisconnected(ctx, e)
  }
  override def channelUnbound(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelUnbound(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelUnbound(ctx, e)
  }
  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelClosed(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelClosed(ctx, e)
  }
  override def writeComplete(ctx: ChannelHandlerContext, e: WriteCompletionEvent) {
    val m = WriteComplete(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.writeComplete(ctx, e)
  }
  override def childChannelOpen(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) {
    val m = ChildChannelOpen(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.childChannelOpen(ctx, e)
  }
  override def childChannelClosed(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) {
    val m = ChildChannelClosed(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.childChannelClosed(ctx, e)
  }
}

object Routes {
  def apply(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) = new Routes(route)
}
class Routes(val route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) extends Http4sHandler

//class RequestBodyEnumerator(req: HttpRequest, queue: mutable.Queue[HttpChunk]) extends Enumerator[Chunk] {
//  def apply[A](i: Iteratee[_root_.org.http4s.Chunk, A]): Future[Iteratee[_root_.org.http4s.Chunk, A]] = {
//    def step(it: Iteratee[Chunk, A]): Future[Iteratee[Chunk, A]] = it.fold {
//        case Step.Done(a, e) => Future.successful(Done(a,e))
//        case Step.Cont(k) => inner[A](step,k)
//        case Step.Error(msg, e) => Future.successful(Error(msg,e))
//    }
//    step(i)
//  }
//}

abstract class Http4sHandler(implicit executor: ExecutionContext = ExecutionContext.global) extends ScalaUpstreamHandler {

  def route: Route

  import ScalaUpstreamHandler._

  val chunks = new LinkedBlockingDeque[HttpChunk]()



  val handle: UpstreamHandler = {
    case MessageReceived(ctx, req: HttpRequest, rem: InetSocketAddress) =>
      println("offering another request")
      chunks.offer(new DefaultHttpChunk(req.getContent))
      val request = toRequest(ctx, req, rem.getAddress)
      val handler = route(request)
      logger.info(s"Got request $request")

      val responderAndRemainingBody = runAndCollect(request.body |>> handler)
      responderAndRemainingBody onSuccess {
        case (responder, leftOvers) =>
          logger.info(s"Response generated in the handler for request $request")
          val enum = leftOvers match {
            case Some(d) => Enumerator.enumInput[Chunk](d)
            case None => Enumerator.enumInput[Chunk](Input.Empty)
          }
          val newBody = enum >>> responder.body
          renderResponse(ctx, req, responder.copy(body = newBody))
      }
      responderAndRemainingBody onFailure {
        case t => logger.error(t.getMessage, t)
      }

    case MessageReceived(ctx, chunk: HttpChunk, _) =>
      chunks.offer(chunk)

    case ExceptionCaught(ctx, e) =>
      try {
        Option(e.getCause) foreach (t => logger.error(t.getMessage, t))
        val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        resp.setContent(ChannelBuffers.copiedBuffer((e.getCause.getMessage + "\n" + e.getCause.getStackTraceString).getBytes(Codec.UTF8.charSet)))
        ctx.getChannel.write(resp).addListener(ChannelFutureListener.CLOSE)
      } catch {
        case t: Throwable =>
          logger.error(t.getMessage, t)
          allCatch(ctx.getChannel.close().await())
      }
  }

  /** Helper method that gets any returned input by the enumerator it can be stacked back onto the body
  */
  private[this] def runAndCollect(it: Future[Handler]): Future[(Responder,Option[Input[Chunk]])] = {

    it.flatMap(iter => {
      println("In Run and collect")
      iter.fold({
        case Step.Done(a, d@Input.El(_)) => Future.successful((a,Some(d)))
        case Step.Done(a, _) => Future.successful((a,None))
        case Step.Cont(k) => k(Input.EOF).fold({
          case Step.Done(a1, d@Input.El(_)) => Future.successful((a1, Some(d)))
          case Step.Done(a1, _) => Future.successful((a1, None))
          case Step.Cont(_) => sys.error("diverging iteratee after Input.EOF")
          case Step.Error(msg, e) => sys.error(msg)
        })
        case Step.Error(msg, e) => sys.error(msg)
      })
    })
  }

  protected def renderResponse(ctx: ChannelHandlerContext, req: HttpRequest, responder: Responder) {
    def closeChannel(channel: Channel) = {
      logger.info("Closing the channel")
      if (!http.HttpHeaders.isKeepAlive(req)) channel.close()
    }


    val stat = new HttpResponseStatus(responder.statusLine.code, responder.statusLine.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)
    for (header <- responder.headers) {
      resp.addHeader(header.name, header.value)
    }

    val content = ChannelBuffers.buffer(512 * 1024)
    resp.setContent(content)
    val chf = Iteratee.foldM[Chunk, Option[HttpChunk]](None) {
      case (None, chunk) if ((content.readableBytes() + chunk.length) <= content.writableBytes()) =>
        println("collecting response buffer chunks")
        content.writeBytes(chunk)
        Future.successful(None)
      case (None, chunk) =>
        val msg = new http.DefaultHttpChunk(ChannelBuffers.wrappedBuffer(chunk))
        if (!(responder.headers.exists(h => (h.name equalsIgnoreCase "TRANSFER-ENCODING") && h.value.equalsIgnoreCase("CHUNKED"))))
          resp.addHeader(http.HttpHeaders.Names.TRANSFER_ENCODING, http.HttpHeaders.Values.CHUNKED)
        ctx.getChannel.write(resp) map (_ => Some(msg))
      case (Some(toWrite), chunk) =>
        val msg = new http.DefaultHttpChunk(ChannelBuffers.wrappedBuffer(chunk))
        ctx.getChannel.write(toWrite) map (_ => Some(msg))
    }

    responder.body.run(chf) flatMap {
      case None =>
        if (!responder.headers.exists(_.name equalsIgnoreCase "CONTENT-LENGTH"))
          resp.addHeader("Content-Length", resp.getContent.readableBytes())
        resp.setContent(content)
        ctx.getChannel.write(resp)
      case Some(chunk) =>
        val trailer = new DefaultHttpChunkTrailer()
        val ch = ctx.getChannel
        ch.write(chunk) flatMap (_ write trailer)
    } onComplete {
      case Success(ch) =>
        closeChannel(ch)
      case Failure(t) =>
        t.printStackTrace()
        closeChannel(ctx.getChannel)
    }

  }

  protected def toRequest(ctx: ChannelHandlerContext, req: HttpRequest, remote: InetAddress)  = {
    val uri = URI.create(req.getUri)
    val hdrs = toHeaders(req)
    val scheme = {
      hdrs.get(XForwardedProto).flatMap(_.blankOption) orElse {
        val fhs = hdrs.get(FrontEndHttps).flatMap(_.blankOption)
        fhs.filter(_ equalsIgnoreCase "ON").map(_ => "https")
      } getOrElse {
        if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https"
      }
    }
    val servAddr = ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress]


    def requestChunks = new Enumerator[HttpChunk] {
      def apply[A](i: Iteratee[HttpChunk, A]): Future[Iteratee[HttpChunk, A]] = {
        def step(i: Iteratee[HttpChunk, A]): Future[Iteratee[HttpChunk, A]] = i.fold({
          case Step.Cont(k) =>
            chunks.poll() match {
              case null => Future.successful(k(Input.Empty))
              case trailer: HttpChunkTrailer => Future.successful(k(Input.EOF))
              case chunk: HttpChunk =>
                if (!req.isChunked || chunk.isLast) {
                  Future.successful(k(Input.El(chunk)).flatMap(r => Done(r, Input.EOF)))
                } else {
                  val ii = k(Input.El(chunk))
                  Future.successful(ii)
                }
            }
          case Step.Done(r, re) =>
            Future.successful(Done(r, re))
          case Step.Error(msg, re) =>
            println("got error")
            Future.failed(sys.error(msg))
        })
        step(i)
      }
    }

    val et = Enumeratee.map[HttpChunk] { e =>
      e.getContent.array(): Chunk
    }

    Request(
      requestMethod = Method(req.getMethod.getName),
      scriptName = "",
      pathInfo = uri.getRawPath,
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.getProtocolName),
      headers = hdrs,
      body = requestChunks &> et,
      urlScheme = UrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = ServerSoftware("HTTP4S/Netty"),
      remote = remote // TODO using remoteName would trigger a lookup
    )
  }

  protected def toHeaders(req: HttpRequest) = Headers(
    (for {
      name  <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield Header(name, value)).toSeq:_*
  )
}