package org.http4s
package netty

import concurrent.{Future, ExecutionContext}
import handlers.ScalaUpstreamHandler
import org.jboss.netty.channel._
import scala.util.control.Exception.allCatch
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import io.Codec
import play.api.libs.iteratee._
import java.net.{InetSocketAddress, URI, InetAddress}
import collection.JavaConverters._
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http
import http._
import scala.language.{ implicitConversions, reflectiveCalls }
import org.http4s.{HttpHeaders, HttpChunk, Responder, RequestPrelude}
import scala.util.{ Failure, Success }
import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.HttpEncodings._
import scala.util.Failure
import scala.util.Success
import org.http4s.TrailerChunk
import org.jboss.netty.channel.socket.nio.NioSocketChannel
import org.jboss.netty.handler.codec.http.HttpHeaders.{Names, Values}
import org.jboss.netty.handler.queue.BufferedWriteHandler

object Http4sNetty {
  def apply(toMount: Route, contextPath: String= "/")(implicit executor: ExecutionContext = ExecutionContext.global) =
    new Http4sNetty(contextPath) {
      val route: Route = toMount

      //def handle: ScalaUpstreamHandler.UpstreamHandler = ???
    }
}
abstract class Http4sNetty(val contextPath: String)(implicit executor: ExecutionContext = ExecutionContext.global) extends ScalaUpstreamHandler {

  def route: Route

  import ScalaUpstreamHandler._

  val handle: UpstreamHandler = {
    case MessageReceived(ctx, req: HttpRequest, rem: InetSocketAddress) =>

      val request = toRequest(ctx, req, rem.getAddress)
      val parser = try {
        route.lift(request).getOrElse(Done(NotFound(request)))
      } catch { case t: Throwable => Done[HttpChunk, Responder](InternalServerError(t)) }

      val handler = parser.flatMap(renderResponse(ctx, req, _))

      Enumerator[HttpChunk](BodyChunk(req.getContent.toByteBuffer)).run[Channel](handler)
          .onComplete {
            case Success(c) => println("Closing channel."); if(c.isOpen) c.close()
            case Failure(t) => println("Failure."); t.printStackTrace()
          }


    case MessageReceived(ctx, chunk: HttpChunk, _) =>
      sys.error("Not supported")

    case ExceptionCaught(ctx, e) =>
      println("Caught exception!!! ----------------------------------------------------------")
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

  protected def renderResponse(ctx: ChannelHandlerContext, req: HttpRequest, responder: Responder) = {
//    def closeChannel(channel: Channel) = {
//      if (!http.HttpHeaders.isKeepAlive(req) && channel.isConnected) channel.close()
//    }


    val stat = new HttpResponseStatus(responder.prelude.status.code, responder.prelude.status.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)

    val isChunked = responder.prelude.headers.get(HttpHeaders.TransferEncoding).map(_.coding.matches(chunked)).getOrElse(false)
    if (isChunked) { resp.setChunked(true) }

    for (header <- responder.prelude.headers) {
      resp.addHeader(header.name, header.value)
    }

//    val channelBuffer = ChannelBuffers.dynamicBuffer(8912)
//    val writer: (ChannelBuffer, BodyChunk) => Unit = (c, x) => c.writeBytes(x.asByteBuffer)
//    val stringIteratee = Iteratee.fold[org.http4s.HttpChunk, ChannelBuffer](channelBuffer) {
//      case (c, e: BodyChunk) =>
//        writer(c, e)
//        c
//      case (c, e: TrailerChunk) =>
//        logger.warn("Not a chunked response. Trailer ignored.")
//        c
//    }

    def folder(f: Future[Channel], i: Input[HttpChunk]): Iteratee[HttpChunk, Channel] = i match {
      case Input.El(c: BodyChunk) =>
        val buff = new DefaultHttpChunk(ChannelBuffers.copiedBuffer(c.toArray))
        println("Got to folder. " + ctx.getChannel.isConnected)
      //ctx.getChannel.write(ChannelBuffers.copiedBuffer("Hello!".getBytes()))
        Thread.sleep(200)
        val ff = f.flatMap{ c =>
          println("Writing Buffer: " + c.isWritable)
          c.write(buff)
        }
        Cont(folder(ff, _))

      case Input.El(c: TrailerChunk) =>
        logger.warn("Not a chunked response. Trailer ignored.")
        Cont(folder(f, _))

      case Input.Empty => Cont(folder(f, _))

      case Input.EOF =>
        // End of file
        val ff = f.flatMap{c =>
          c.write(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER))
        }

        new Iteratee[HttpChunk, Channel] {
          def fold[B](folder: (Step[HttpChunk, Channel]) => Future[B]): Future[B] =
            ff.flatMap(c => folder(Step.Done(c, Input.Empty)))
        }
    }

    val f: Future[Channel] = ctx.getChannel.write(resp)
    f.onComplete(_ => println("Initial Future Complete."))

    responder.body &>> Cont(folder(f, _))

//    (responder.body ><> Enumeratee.grouped(stringIteratee) &>> Cont {
//      case Input.El(buffer) =>
//        resp.setHeader(HttpHeaders.Names.CONTENT_LENGTH, channelBuffer.readableBytes)
//        resp.setContent(buffer)
//        val f: Future[Channel] = ctx.getChannel.write(resp)
//        f onComplete {
//          case _ => closeChannel(ctx.getChannel)
//        }
//        Iteratee.flatten(f.map(_ => Done((),Input.Empty:Input[org.jboss.netty.buffer.ChannelBuffer])))
//
//      case other => Error("unexepected input",other)
//    })
  }

  val serverSoftware = ServerSoftware("HTTP4S / Netty")

  protected def toRequest(ctx: ChannelHandlerContext, req: HttpRequest, remote: InetAddress)  = {
    val uri = URI.create(req.getUri)
    val hdrs = toHeaders(req)
    val scheme = if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https" 
//    val scheme = {
//      hdrs.get(XForwardedProto).flatMap(_.value.blankOption) orElse {
//        val fhs = hdrs.get(FrontEndHttps).flatMap(_.value.blankOption)
//        fhs.filter(_ equalsIgnoreCase "ON").map(_ => "https")
//      } getOrElse {
//        if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https"
//      }
//    }
    val servAddr = ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress]
    println("pth info: " + uri.getPath.substring(contextPath.length))
    RequestPrelude(
      requestMethod = Method(req.getMethod.getName),
      scriptName = contextPath,
      pathInfo = uri.getRawPath.substring(contextPath.length),
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.getProtocolName),
      headers = hdrs,
      urlScheme = HttpUrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = serverSoftware,
      remote = remote // TODO using remoteName would trigger a lookup
    )
  }

  protected def toHeaders(req: HttpRequest) = org.http4s.HttpHeaders(
    (for {
      name  <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield org.http4s.HttpHeaders.RawHeader(name, value)).toSeq:_*
  )
}