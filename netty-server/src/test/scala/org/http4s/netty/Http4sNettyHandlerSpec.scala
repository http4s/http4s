package org.http4s
package netty

import java.util.concurrent.ArrayBlockingQueue

import dsl.io._
import cats.effect.IO
import fs2.Stream
import fs2.interop.reactivestreams._
import com.typesafe.netty.http.{
  DefaultStreamedHttpRequest,
  DefaultStreamedHttpResponse,
  StreamedHttpResponse
}
import io.netty.buffer.Unpooled
import io.netty.channel.{
  ChannelHandlerContext,
  ChannelInboundHandlerAdapter,
  ChannelOutboundHandlerAdapter,
  ChannelPromise
}
import io.netty.channel.embedded.EmbeddedChannel
import org.http4s.server.DefaultServiceErrorHandler
import io.netty.channel.group.DefaultChannelGroup
import io.netty.handler.codec.http.{
  DefaultHttpContent,
  HttpContent,
  HttpResponse,
  HttpResponseStatus
}
//import io.netty.channel.{ChannelHandler, ChannelOption}
import io.netty.handler.codec.http.{
  DefaultFullHttpRequest,
  HttpMethod,
  HttpVersion => NettyHttpVersion
}
import io.netty.util.ReferenceCounted
import org.log4s.getLogger

//import scala.collection.JavaConverters._

class Http4sNettyHandlerSpec extends Http4sSpec {
  def beReleased(r: ReferenceCounted) = r.refCnt() == 0

  def generateDefaultContent: HttpContent =
    new DefaultHttpContent(Unpooled.wrappedBuffer(Array[Byte](1, 2, 3, 4, 5)))

  val basicService: HttpService[IO] = HttpService {
    case r @ GET -> Root / "ping" =>
      r.body.compile.drain.flatMap(_ => Ok())
    case POST -> Root / "noConsumeBody" => Ok()
    case POST -> Root / "consumeBody" => Ok("hi!")
  }

  val interceptor: NettyOutboundInterceptor =
    NettyOutboundInterceptor.empty

  val defaultHandler =
    Http4sNettyHandler.default[IO](basicService, DefaultServiceErrorHandler[IO])

  val channel = new EmbeddedChannel(interceptor, defaultHandler)

  "Http4sNettyHandlerSpec" should {
    "Return a simple Ok response when calling the handler ping route" in {
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/ping")
      channel.writeInbound(request)
      val response = interceptor.readBlocking
      response match {
        case r: DefaultStreamedHttpResponse =>
          r.status() must_== HttpResponseStatus.OK
          r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        case _ =>
          ko("Invalid response type for an entity that's allowed a body")
      }
    }

    "Release all incoming bytes despite user not using the incoming body stream for a full request" in {
      val content = Unpooled.wrappedBuffer(new Array[Byte](1))
      val request =
        new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/ping", content)
      channel.writeInbound(request)
      val response = interceptor.readBlocking
      response match {
        case r: DefaultStreamedHttpResponse =>
          r.status() must_== HttpResponseStatus.OK
          r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
          //Check buffer was released
          content.refCnt() must_== 0
        case _ =>
          ko("Invalid response type for an entity that's allowed a body")
      }
    }

    "Release all incoming bytes despite user not using the incoming body stream for a full request" in {
      val buffers: List[HttpContent] = List.fill(3)(generateDefaultContent)
      val publisherStream = Stream.emits(buffers).covary[IO].toUnicastPublisher()
      val request = new DefaultStreamedHttpRequest(
        NettyHttpVersion.HTTP_1_1,
        HttpMethod.GET,
        "/ping",
        publisherStream)
      channel.writeInbound(request)
      val response = interceptor.readBlocking
      response match {
        case r: DefaultStreamedHttpResponse =>
          r.status() must_== HttpResponseStatus.OK
          r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
          forall(buffers)(_.refCnt() must_== 0)
        case _ =>
          ko("Invalid response type for an entity that's allowed a body")
      }
    }

  }

}

private[netty] class NettyOutboundInterceptor(q: ArrayBlockingQueue[HttpResponse])
    extends ChannelOutboundHandlerAdapter {

  private[this] val logger = getLogger

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit =
    msg match {
      case h: HttpResponse => q.put(h);

      case _ =>
        logger.error("Intercepted message other than http response: " + msg.toString)
    }

  def readBlocking: HttpResponse = q.take()

}

object NettyOutboundInterceptor {
  def empty = new NettyOutboundInterceptor(new ArrayBlockingQueue[HttpResponse](1))
}
