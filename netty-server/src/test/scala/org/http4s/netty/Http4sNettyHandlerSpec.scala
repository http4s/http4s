package org.http4s
package netty

import java.util.concurrent.ArrayBlockingQueue

import cats.effect.IO
import cats.syntax.all._
import com.typesafe.netty.http.{DefaultStreamedHttpRequest, StreamedHttpResponse}
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.{HttpVersion => NettyHttpVersion, _}
import org.http4s.dsl.io._
import org.http4s.headers.{Date, `Content-Length`, `Transfer-Encoding`}
import org.http4s.server.DefaultServiceErrorHandler
import org.log4s.getLogger
import org.specs2.matcher.MatchResult

import scala.concurrent.duration._

class Http4sNettyHandlerSpec extends Http4sSpec {
  def generateDefaultContent: HttpContent =
    new DefaultHttpContent(Unpooled.wrappedBuffer(Array[Byte](1, 2, 3)))

  def fillBuffer(): List[HttpContent] = List.fill(2)(generateDefaultContent)

  def setupChannel(handler: ChannelHandler): (NettyInterceptor, EmbeddedChannel) = {
    val interceptor = NettyInterceptor.empty
    (interceptor, new EmbeddedChannel(interceptor, handler))
  }

  def defaultHandler(s: HttpService[IO]): Http4sNettyHandler[IO] =
    Http4sNettyHandler.default[IO](s, DefaultServiceErrorHandler[IO])

  def webSocketService(s: HttpService[IO]): Http4sNettyHandler[IO] =
    Http4sNettyHandler.websocket[IO](s, DefaultServiceErrorHandler[IO])

  def isConnectionClosed(h: HttpResponse): Boolean =
    h.headers().contains(HttpHeaderNames.CONNECTION) && h
      .headers()
      .get(HttpHeaderNames.CONNECTION)
      .equalsIgnoreCase("close")

  private def matchStreamedResponse[A](
      request: HttpRequest,
      channel: EmbeddedChannel,
      interceptor: NettyInterceptor)(
      f: StreamedHttpResponse => MatchResult[A]): MatchResult[Any] = {
    channel.writeInbound(request)
    val response = interceptor.readBlocking
    val matches = response match {
      case h: StreamedHttpResponse =>
        f(h)
      case _ =>
        ko("Invalid Response Type: Streamed response expected")
    }
    if (channel.isOpen)
      channel.close()
    matches
  }

  private def matchFullResponse[A](
      request: HttpRequest,
      channel: EmbeddedChannel,
      interceptor: NettyInterceptor)(f: FullHttpResponse => MatchResult[A]): MatchResult[Any] = {
    channel.writeInbound(request)
    val response = interceptor.readBlocking
    response match {
      case h: FullHttpResponse =>
        val result = f(h)
        h.content().release()
        result
      case _ =>
        ko("Invalid Response Type: Full response expected")
    }

  }

  "Http4sNettyHandlerSpec: common ops" should {
    lazy val basicService: HttpService[IO] = HttpService {
      case GET -> Root / "ping" => Ok()
      case POST -> Root / "noConsume" => Ok()
      case r @ POST -> Root / "consumeAll" =>
        r.body.compile.drain >> Ok("hi!")
      case GET -> Root / "noEntity" =>
        IO(Response(NoContent, body = Stream.emits(Array[Byte](1, 2, 3, 4, 5)).covary[IO]))
    }

    def setupBasicChannel: (NettyInterceptor, EmbeddedChannel) =
      setupChannel(defaultHandler(basicService))

    "Return a simple Ok response when calling the handler ping route" in {
      val (interceptor, channel) = setupBasicChannel
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/ping")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
      }
    }

    "Release all incoming bytes in a GET request regardless of outcome" in {
      val (interceptor, channel) = setupBasicChannel
      val content = Unpooled.wrappedBuffer(new Array[Byte](1))
      val request =
        new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/ping", content)
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        //Check buffer was released
        content.refCnt() must_== 0
        //Channel must have remained open
        channel.isOpen must_== true
        channel.close().await(100) must_== true
      }
    }

    "Release all incoming bytes when body stream has been drained and keep the connection alive" in {
      val (interceptor, channel) = setupBasicChannel
      val buffers: List[HttpContent] = fillBuffer()
      val publisherStream = Stream.emits(buffers).covary[IO].toUnicastPublisher()
      val request = new DefaultStreamedHttpRequest(
        NettyHttpVersion.HTTP_1_1,
        HttpMethod.POST,
        "/consumeAll",
        publisherStream)
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        forall(buffers)(_.refCnt() must_== 0)
        channel.isOpen must_== true
        channel.close().await(100) must_== true
      }
    }

    "Release all incoming bytes for an unconsumed body for a streamed request and close the connection" in {
      val (interceptor, channel) = setupBasicChannel
      val buffers: List[HttpContent] = fillBuffer()
      val publisherStream = Stream.emits(buffers).covary[IO].toUnicastPublisher()
      val request = new DefaultStreamedHttpRequest(
        NettyHttpVersion.HTTP_1_1,
        HttpMethod.POST,
        "/noConsume",
        publisherStream)
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        forall(buffers)(_.refCnt() must be_==(0).eventually(20, 300.milliseconds))
        //Unconsumed bodies closes the channel and connection
        channel.isOpen must_== false
      }

    }

    "Release all incoming bytes for a not found route and close the connection" in {
      val (interceptor, channel) = setupBasicChannel
      val buffers: List[HttpContent] = fillBuffer()
      val publisherStream = Stream.emits(buffers).covary[IO].toUnicastPublisher()
      val request = new DefaultStreamedHttpRequest(
        NettyHttpVersion.HTTP_1_1,
        HttpMethod.POST,
        "/TheMooseIsLoose",
        publisherStream)
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.NOT_FOUND
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        forall(buffers)(_.refCnt() must be_==(0).eventually(20, 300.milliseconds))
        //Unconsumed bodies closes the channel and connection
        channel.isOpen must_== false
      }
    }

    "Not emit the body for a response that does not allow one" in {
      val (interceptor, channel) = setupBasicChannel
      val buffers: List[HttpContent] = fillBuffer()
      val publisherStream = Stream.emits(buffers).covary[IO].toUnicastPublisher()
      val request = new DefaultStreamedHttpRequest(
        NettyHttpVersion.HTTP_1_1,
        HttpMethod.GET,
        "/noEntity",
        publisherStream)
      matchFullResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.NO_CONTENT
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        //`eventually` combinator just does the same thing
        forall(buffers)(_.refCnt() must be_==(0).eventually(20, 300.milliseconds))
        //Unconsumed bodies closes the channel and connection
        channel.isOpen must_== false
      }
    }

  }

  "Http4sNettyHandlerSpec errors" should {
    lazy val exceptionService = HttpService[IO] {
      case GET -> Root / "sync" =>
        sys.error("Waylon Jennings is Inferior to Hatsune Miku long live the Queen")
      case GET -> Root / "async" =>
        IO.raiseError(new Exception("Asynchronous error!"))
      case GET -> Root / "sync" / "422" =>
        throw InvalidMessageBodyFailure("lol, I didn't even look")
      case GET -> Root / "async" / "422" =>
        IO.raiseError(InvalidMessageBodyFailure("lol, I didn't even look"))
    }

    def setupErrorChannel: (NettyInterceptor, EmbeddedChannel) =
      setupChannel(Http4sNettyHandler.default[IO](exceptionService, DefaultServiceErrorHandler[IO]))

    "Handle a synchronous, uncaught error" in {
      val (interceptor, channel) = setupErrorChannel
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/sync")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.INTERNAL_SERVER_ERROR
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        isConnectionClosed(r) must_== true
      }
    }

    "Handle an asynchronous error" in {
      val (interceptor, channel) = setupErrorChannel
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/async")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.INTERNAL_SERVER_ERROR
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        isConnectionClosed(r) must_== true
      }
    }

    "Handle a synchronous error with unprocessable entity" in {
      val (interceptor, channel) = setupErrorChannel
      val request =
        new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/sync/422")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.UNPROCESSABLE_ENTITY
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        isConnectionClosed(r) must_== false
      }
    }

    "Handle an asynchronous error with unprocessable entity" in {
      val (interceptor, channel) = setupErrorChannel
      val request =
        new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/async/422")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.UNPROCESSABLE_ENTITY
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        isConnectionClosed(r) must_== false
      }
    }
  }

  "Http4sNettyHandlerSpec: routes" should {
    "Honor a date header" in {
      val date = Date(HttpDate.Epoch)
      val dateService: HttpService[IO] = HttpService {
        case _ =>
          Ok(date)
      }
      val (interceptor, channel) = setupChannel(defaultHandler(dateService))
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        HttpDate.unsafeFromString(r.headers().get(HttpHeaderNames.DATE)) must_== HttpDate.Epoch
      }
    }

    "Remove Chunked transfer encoding for Http 1.0" in {
      val service: HttpService[IO] = HttpService {
        case _ =>
          Ok(`Transfer-Encoding`(TransferCoding.chunked))
      }
      val (interceptor, channel) = setupChannel(defaultHandler(service))
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_0, HttpMethod.GET, "/")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_0
        r.headers().contains(HttpHeaderNames.TRANSFER_ENCODING) must_== false
      }
    }

    "Ignore content-length if chunked transfer encoding is set for http 1.1" in {
      val service: HttpService[IO] = HttpService {
        case _ =>
          Ok(
            `Content-Length`.unsafeFromLong(10),
            `Transfer-Encoding`(TransferCoding.gzip, TransferCoding.chunked))
      }
      val (interceptor, channel) = setupChannel(defaultHandler(service))
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        r.headers().contains(HttpHeaderNames.CONTENT_LENGTH) must_== false
        HttpUtil.isTransferEncodingChunked(r) must_== true
      }
    }

    "Ignore transfer-encodings if chunked transfer is not set for http 1.1" in {
      val service: HttpService[IO] = HttpService {
        case _ =>
          Ok(`Content-Length`.unsafeFromLong(10), `Transfer-Encoding`(TransferCoding.gzip))
      }
      val (interceptor, channel) = setupChannel(defaultHandler(service))
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        r.headers().contains(HttpHeaderNames.CONTENT_LENGTH) must_== true
        r.headers().contains(HttpHeaderNames.TRANSFER_ENCODING) must_== false
      }
    }

    "Add content-length to HEAD despite having an empty body" in {
      val service: HttpService[IO] = HttpService {
        case _ =>
          Ok("WELCOME TO THE DANGER ZONEEE")
      }
      val (interceptor, channel) = setupChannel(defaultHandler(service))
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.HEAD, "/")
      matchFullResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        r.headers().contains(HttpHeaderNames.CONTENT_LENGTH) must_== true
        r.headers().contains(HttpHeaderNames.TRANSFER_ENCODING) must_== false
        r.content().readableBytes() must_== 0
      }
    }

    "Add transfer-encoding: chunked to Http1.1 response without any encoding" in {
      val service: HttpService[IO] = HttpService {
        case _ =>
          IO(Response[IO](Ok).withBodyStream(Stream.emits[Byte](List(1, 2, 3))))
      }
      val (interceptor, channel) = setupChannel(defaultHandler(service))
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        HttpUtil.isTransferEncodingChunked(r) must_== true
      }
    }

    "Add transfer-encoding: chunked to Http1.1 response with transfer-encoding but without chunked" in {
      val service: HttpService[IO] = HttpService {
        case _ =>
          IO(
            Response[IO](Ok)
              .withBodyStream(Stream.emits[Byte](List(1, 2, 3)))
              .putHeaders(`Transfer-Encoding`(TransferCoding.gzip)))
      }
      val (interceptor, channel) = setupChannel(defaultHandler(service))
      val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      matchStreamedResponse(request, channel, interceptor) { r =>
        r.status() must_== HttpResponseStatus.OK
        r.protocolVersion() must_== NettyHttpVersion.HTTP_1_1
        HttpUtil.isTransferEncodingChunked(r) must_== true
        r.headers.contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.GZIP, true) must_== true
      }
    }
  }

}

/** A class that simply lets us intercept the
  * netty outbound response and signals a successful read,
  * for the sake of executing the post-response finalizers.
  *
  * EmbeddedChannel is weird. Without the interceptor,
  * despite being passed down the writes chain, writes aren't
  * added to the out buffer (possibly because it's a forked action).
  */
private[netty] class NettyInterceptor(q: ArrayBlockingQueue[HttpResponse])
    extends ChannelOutboundHandlerAdapter {

  private[this] val logger = getLogger

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit =
    msg match {
      case h: HttpResponse =>
        //Necessary, as it allows us to run the consumption
        //And bytebuf release finalizers.
        ctx.writeAndFlush(msg, promise).awaitUninterruptibly(2000)
        q.put(h)

      case _ =>
        logger.error("Intercepted message other than http response: " + msg.toString)
    }

  def readBlocking: HttpResponse = q.take()

}

object NettyInterceptor {
  def empty = new NettyInterceptor(new ArrayBlockingQueue[HttpResponse](1))
}
