package org.http4s
package server
package netty

import java.util.concurrent.ArrayBlockingQueue

import cats.effect.IO
import cats.syntax.all._
import com.typesafe.netty.http.{DefaultStreamedHttpRequest, StreamedHttpResponse}
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.buffer.{ByteBufHolder, Unpooled}
import io.netty.channel._
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.{HttpVersion => NettyHttpVersion, _}
import org.http4s.dsl.io._
import org.http4s.headers.{Date, `Content-Length`, `Transfer-Encoding`}
import org.http4s.server.DefaultServiceErrorHandler
import org.log4s.getLogger
import org.specs2.matcher.MatchResult
import org.specs2.specification.core.Fragment

import scala.concurrent.duration._

class Http4sNettyHandlerSpec extends Http4sSpec {
  //////////////////////////////////////////////////
  // Handler tests Setup
  /////////////////////////////////////////////////

  private def generateDefaultContent: HttpContent =
    new DefaultHttpContent(Unpooled.wrappedBuffer(Array[Byte](1, 2, 3)))

  private def fillBuffer(): List[HttpContent] = List.fill(2)(generateDefaultContent)

  private def setupChannel(handler: ChannelHandler): (NettyInterceptor, EmbeddedChannel) = {
    val interceptor = NettyInterceptor.empty
    (interceptor, new EmbeddedChannel(interceptor, handler))
  }

  private def tryRelease(r: ByteBufHolder): Unit = {
    if (r.content().refCnt() > 0)
      r.content().release()
    ()
  }

  private def isTransferEncodingChunked(r: HttpResponse): Boolean =
    r.headers().contains(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED, true)

  private def defaultHandler(s: HttpService[IO]): Http4sNettyHandler[IO] =
    Http4sNettyHandler.default[IO](s, DefaultServiceErrorHandler[IO])

  private def webSocketService(s: HttpService[IO]): Http4sNettyHandler[IO] =
    Http4sNettyHandler.websocket[IO](s, DefaultServiceErrorHandler[IO], 65536)

  private def isConnectionClosed(h: HttpResponse): Boolean =
    h.headers().contains(HttpHeaders.Names.CONNECTION) && h
      .headers()
      .get(HttpHeaders.Names.CONNECTION)
      .equalsIgnoreCase("close")

  private def matchStreamedResponse[A](
      request: HttpRequest,
      channel: EmbeddedChannel,
      interceptor: NettyInterceptor)(
      f: StreamedHttpResponse => MatchResult[A]): MatchResult[Any] = {
    channel.writeInbound(request)
    val matchResult = interceptor.readBlocking match {
      case h: StreamedHttpResponse =>
        val r = f(h)
        h.toStream[IO]().map(tryRelease).compile.drain.unsafeRunSync()
        r
      case _ =>
        ko("Invalid Response Type: Streamed response expected")
    }
    if (channel.isOpen)
      channel.close().awaitUninterruptibly(3000L)
    matchResult
  }

  private def matchFullResponse[A](
      request: HttpRequest,
      channel: EmbeddedChannel,
      interceptor: NettyInterceptor)(f: FullHttpResponse => MatchResult[A]): MatchResult[Any] = {
    channel.writeInbound(request)
    val matchResult = interceptor.readBlocking match {
      case h: FullHttpResponse =>
        val result = f(h)
        tryRelease(h)
        result
      case _ =>
        ko("Invalid Response Type: Full response expected")
    }
    if (channel.isOpen)
      channel.close().awaitUninterruptibly(3000L)
    matchResult
  }

  private def nettyHandlerTests(
      getHandler: HttpService[IO] => Http4sNettyHandler[IO],
      handlerName: String): Fragment = {
    s"Http4sNettyHandlerSpec for $handlerName: common ops" should {
      lazy val basicService: HttpService[IO] = HttpService {
        case GET -> Root / "ping" => Ok()
        case POST -> Root / "noConsume" => Ok()
        case r @ POST -> Root / "consumeAll" =>
          r.body.compile.drain >> Ok("hi!")
        case GET -> Root / "noEntity" =>
          IO(Response(NoContent, body = Stream.emits(Array[Byte](1, 2, 3, 4, 5)).covary[IO]))
      }

      def setupBasicChannel: (NettyInterceptor, EmbeddedChannel) =
        setupChannel(getHandler(basicService))

      "Return a simple Ok response when calling the handler ping route" in {
        val (interceptor, channel) = setupBasicChannel
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/ping")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
        }
      }

      "Release all incoming bytes in a GET request regardless of outcome" in {
        val (interceptor, channel) = setupBasicChannel
        val content = Unpooled.wrappedBuffer(new Array[Byte](1))
        val request =
          new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/ping", content)
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          //Check buffer was released
          content.refCnt() must_== 0
        //Channel must have remained open
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
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          forall(buffers)(_.refCnt() must_== 0)
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
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          forall(buffers)(_.refCnt() must be_==(0).eventually(20, 300.milliseconds))
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
          r.getStatus() must_== HttpResponseStatus.NOT_FOUND
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          forall(buffers)(_.refCnt() must be_==(0).eventually(20, 300.milliseconds))
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
          r.getStatus() must_== HttpResponseStatus.NO_CONTENT
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          //`eventually` combinator just does the same thing
          forall(buffers)(_.refCnt() must be_==(0).eventually(20, 300.milliseconds))
        }
      }

    }

    s"Http4sNettyHandlerSpec for $handlerName errors" should {
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
        setupChannel(
          Http4sNettyHandler.default[IO](exceptionService, DefaultServiceErrorHandler[IO]))

      "Handle a synchronous, uncaught error" in {
        val (interceptor, channel) = setupErrorChannel
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/sync")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.INTERNAL_SERVER_ERROR
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          isConnectionClosed(r) must_== true
        }
      }

      "Handle an asynchronous error" in {
        val (interceptor, channel) = setupErrorChannel
        val request =
          new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/async")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.INTERNAL_SERVER_ERROR
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          isConnectionClosed(r) must_== true
        }
      }

      "Handle a synchronous error with unprocessable entity" in {
        val (interceptor, channel) = setupErrorChannel
        val request =
          new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/sync/422")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.UNPROCESSABLE_ENTITY
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          isConnectionClosed(r) must_== false
        }
      }

      "Handle an asynchronous error with unprocessable entity" in {
        val (interceptor, channel) = setupErrorChannel
        val request =
          new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/async/422")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.UNPROCESSABLE_ENTITY
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          isConnectionClosed(r) must_== false
        }
      }
    }

    s"Http4sNettyHandlerSpec for $handlerName: routes" should {
      "Honor a date header" in {
        val date = Date(HttpDate.Epoch)
        val dateService: HttpService[IO] = HttpService {
          case _ =>
            Ok(date)
        }
        val (interceptor, channel) = setupChannel(getHandler(dateService))
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          HttpDate.unsafeFromString(r.headers().get(HttpHeaders.Names.DATE)) must_== HttpDate.Epoch
        }
      }

      "Remove Chunked transfer encoding for Http 1.0" in {
        val service: HttpService[IO] = HttpService {
          case _ =>
            Ok(`Transfer-Encoding`(TransferCoding.chunked))
        }
        val (interceptor, channel) = setupChannel(getHandler(service))
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_0, HttpMethod.GET, "/")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_0
          r.headers().contains(HttpHeaders.Names.TRANSFER_ENCODING) must_== false
        }
      }

      "Ignore content-length if chunked transfer encoding is set for http 1.1" in {
        val service: HttpService[IO] = HttpService {
          case _ =>
            Ok(
              `Content-Length`.unsafeFromLong(10),
              `Transfer-Encoding`(TransferCoding.gzip, TransferCoding.chunked))
        }
        val (interceptor, channel) = setupChannel(getHandler(service))
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          r.headers().contains(HttpHeaders.Names.CONTENT_LENGTH) must_== false
          isTransferEncodingChunked(r) must_== true
        }
      }

      "Ignore transfer-encodings if chunked transfer is not set for http 1.1" in {
        val service: HttpService[IO] = HttpService {
          case _ =>
            Ok(`Content-Length`.unsafeFromLong(10), `Transfer-Encoding`(TransferCoding.gzip))
        }
        val (interceptor, channel) = setupChannel(getHandler(service))
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          r.headers().contains(HttpHeaders.Names.CONTENT_LENGTH) must_== true
          r.headers().contains(HttpHeaders.Names.TRANSFER_ENCODING) must_== false
        }
      }

      "Add content-length to HEAD despite having an empty body" in {
        val service: HttpService[IO] = HttpService {
          case _ =>
            Ok("WELCOME TO THE DANGER ZONEEE")
        }
        val (interceptor, channel) = setupChannel(getHandler(service))
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.HEAD, "/")
        matchFullResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          r.headers().contains(HttpHeaders.Names.CONTENT_LENGTH) must_== true
          r.headers().contains(HttpHeaders.Names.TRANSFER_ENCODING) must_== false
          r.content().readableBytes() must_== 0
        }
      }

      "Add transfer-encoding: chunked to Http1.1 response without any encoding" in {
        val service: HttpService[IO] = HttpService {
          case _ =>
            IO(Response[IO](Ok).withBodyStream(Stream.emits[Byte](List(1, 2, 3))))
        }
        val (interceptor, channel) = setupChannel(getHandler(service))
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          isTransferEncodingChunked(r) must_== true
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
        val (interceptor, channel) = setupChannel(getHandler(service))
        val request = new DefaultFullHttpRequest(NettyHttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        matchStreamedResponse(request, channel, interceptor) { r =>
          r.getStatus() must_== HttpResponseStatus.OK
          r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
          isTransferEncodingChunked(r) must_== true
          r.headers.contains(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.GZIP, true) must_== true
        }
      }

      "Echo request bodies without a hitch" in {
        val service: HttpService[IO] = HttpService {
          case r =>
            IO(Response[IO](Ok).withBodyStream(r.body))
        }
        val (interceptor, channel) = setupChannel(getHandler(service))
        val buffers: List[HttpContent] = fillBuffer()
        val publisherStream = Stream.emits(buffers).covary[IO].toUnicastPublisher()
        val request = new DefaultStreamedHttpRequest(
          NettyHttpVersion.HTTP_1_1,
          HttpMethod.GET,
          "/",
          publisherStream)
        // Do this part manually: Our test will fail otherwise. This is because
        // When we plug in input to output directly as a proxy,
        // our callback after writeInbound eventually releases our buffers
        // When we don't want it to.
        channel.writeInbound(request)
        interceptor.readBlocking match {
          case r: StreamedHttpResponse =>
            r.getStatus() must_== HttpResponseStatus.OK
            r.getProtocolVersion() must_== NettyHttpVersion.HTTP_1_1
            isTransferEncodingChunked(r) must_== true
            //`eventually` combinator just does the same thing
            forall(buffers)(_.refCnt() must be_==(0).eventually(20, 300.milliseconds))
          case _ =>
            ko("Invalid response type caught")
        }
      }
    }
  }

  //////////////////////////////////////////////////
  // Run Handler tests
  /////////////////////////////////////////////////
  nettyHandlerTests(defaultHandler, "default handler")
  nettyHandlerTests(webSocketService, "websocket handler")

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
