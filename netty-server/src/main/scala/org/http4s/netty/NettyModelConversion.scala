package org.http4s.netty

import java.net.InetSocketAddress

import cats.effect.{Async, Effect, IO}
import cats.implicits.{catsSyntaxEither => _, _}
import com.typesafe.netty.http._
import fs2.interop.reactivestreams._
import fs2.{Chunk, Pull, Stream}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.Channel
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => WSFrame, _}
import io.netty.handler.ssl.SslHandler
import org.http4s.Request.Connection
import org.http4s.headers.`Content-Length`
import org.http4s.server.websocket.websocketKey
import org.http4s.util.execution.trampoline
import org.http4s.websocket.WebSocketContext
import org.http4s.websocket.WebsocketBits._
import org.http4s.{HttpVersion => HV, _}
import org.reactivestreams.{Processor, Subscriber, Subscription}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

/** Helpers for converting http4s request/response
  * objects to and from the netty model
  *
  * Adapted from NettyModelConversion.scala
  * in
  * https://github.com/playframework/playframework/blob/master/framework/src/play-netty-server
  *
  */
object NettyModelConversion {

  /** Turn a netty http request into an http4s request
    *
    * @param channel the netty channel
    * @param request the netty http request impl
    * @return Http4s request
    */
  def fromNettyRequest[F[_]](channel: Channel, request: HttpRequest)(
      implicit F: Effect[F]
  ): F[Request[F]] = {
    //Useful for testing, since embedded channels will _not_
    //have connection info
    val attributeMap = createRemoteConnection(channel) match {
      case Some(conn) => AttributeMap(AttributeEntry(Request.Keys.ConnectionInfo, conn))
      case None => AttributeMap.empty
    }
    if (request.decoderResult().isFailure)
      F.raiseError(ParseFailure("Malformed request", "Netty codec parsing unsuccessful"))
    else {
      val requestBody = convertRequestBody(request)
      val uri: ParseResult[Uri] = Uri.fromString(request.uri())
      val headerBuf = new ListBuffer[Header]
      val headersIterator = request.headers().iteratorAsString()
      var mapEntry: java.util.Map.Entry[String, String] = null
      while (headersIterator.hasNext) {
        mapEntry = headersIterator.next()
        headerBuf += Header(mapEntry.getKey, mapEntry.getValue)
      }

      val method: ParseResult[Method] =
        Method.fromString(request.method().name())
      val version: ParseResult[HV] = HV.fromString(request.protocolVersion().text())
      //Avoid extra map(x => x) until we enable better-monadic-for
      version.flatMap { v =>
        uri.flatMap { u =>
          method.map { m =>
            Request[F](
              m,
              u,
              v,
              Headers(headerBuf.toList),
              requestBody,
              attributeMap
            )
          }
        }
      } match { //Micro-optimization: No fold call
        case Right(http4sRequest) => F.pure(http4sRequest)
        case Left(err) => F.raiseError(err)
      }
    }
  }

  /** Capture a request's connection info from its channel and headers. */
  private[this] def createRemoteConnection(channel: Channel): Option[Connection] =
    channel.localAddress() match {
      case address: InetSocketAddress =>
        Some(
          Connection(
            address,
            channel.remoteAddress().asInstanceOf[InetSocketAddress],
            channel.pipeline().get(classOf[SslHandler]) != null
          ))
      case _ => None
    }

  /** Create the source for the request body
    * Todo: Turn off scalastyle due to non-exhaustive match
    */
  private[this] def convertRequestBody[F[_]](request: HttpRequest)(
      implicit F: Effect[F]
  ): Stream[F, Byte] =
    request match {
      case full: FullHttpRequest =>
        val content = full.content()
        val buffers = content.nioBuffers()
        if (buffers.isEmpty)
          Stream.empty.covary[F]
        else {
          val content = full.content()
          val arr = new Array[Byte](content.readableBytes())
          content.readBytes(arr)
          content.release()
          Stream
            .chunk(Chunk.bytes(arr))
            .covary[F]
        }
      case streamed: StreamedHttpRequest =>
        new NettySafePublisher(streamed).toStream[F]()(F, trampoline).flatMap(Stream.chunk(_))
    }

  /** Create a Netty streamed response. */
  private[this] def responseToPublisher[F[_]](
      response: Response[F]
  )(implicit F: Effect[F], ec: ExecutionContext): StreamUnicastPublisher[F, HttpContent] = {
    def go(s: Stream[F, Byte]): Pull[F, HttpContent, Unit] =
      s.pull.unconsChunk.flatMap {
        case Some((chnk, stream)) =>
          Pull.output1[F, HttpContent](chunkToNetty(chnk)) >> go(stream)
        case None =>
          Pull.eval(response.trailerHeaders).flatMap { h =>
            if (h.isEmpty)
              Pull.done
            else {
              val c = new DefaultLastHttpContent()
              h.foreach(appendToNettyHeaders(_, c.trailingHeaders()))
              Pull.output1(c) >> Pull.done
            }
          }
      }
    go(response.body).stream.toUnicastPublisher()
  }

  // Method reference for performance
  private[this] def appendToNettyHeaders(header: Header, nettyHeaders: HttpHeaders) =
    nettyHeaders.add(header.name.toString(), header.value)

  /** Create a Netty response from the result */
  def toNettyResponse[F[_]](
      http4sResponse: Response[F],
      dateString: String
  )(implicit F: Effect[F], ec: ExecutionContext): DefaultHttpResponse = {
    val httpVersion: HttpVersion =
      if (http4sResponse.httpVersion == HV.`HTTP/1.1`)
        HttpVersion.HTTP_1_1
      else
        HttpVersion.HTTP_1_0

    toNonWSResponse[F](http4sResponse, httpVersion, dateString)
  }

  /** Create a Netty response from the result */
  def toNettyResponseWithWebsocket[F[_]](
      httpRequest: Request[F],
      httpResponse: Response[F],
      dateString: String
  )(implicit F: Effect[F], ec: ExecutionContext): F[DefaultHttpResponse] = {
    val httpVersion: HttpVersion =
      if (httpResponse.httpVersion == HV.`HTTP/1.1`)
        HttpVersion.HTTP_1_1
      else if (httpResponse.httpVersion == HV.`HTTP/1.0`)
        HttpVersion.HTTP_1_0
      else
        HttpVersion.valueOf(httpResponse.httpVersion.toString)

    httpResponse.attributes.get(websocketKey[F]) match {
      case None => F.pure(toNonWSResponse[F](httpResponse, httpVersion, dateString))
      case Some(wsContext) =>
        toWSResponse[F](httpRequest, httpResponse, httpVersion, wsContext, dateString)
    }
  }

  private[this] def toNonWSResponse[F[_]](
      httpResponse: Response[F],
      httpVersion: HttpVersion,
      dateString: String)(
      implicit F: Effect[F],
      ec: ExecutionContext
  ): DefaultHttpResponse = {
    val defaultResponse = if (httpResponse.status.isEntityAllowed) {
      val publisher = responseToPublisher[F](httpResponse)
      val response =
        new DefaultStreamedHttpResponse(
          httpVersion,
          HttpResponseStatus.valueOf(httpResponse.status.code),
          publisher
        )
      httpResponse.headers.foreach(appendToNettyHeaders(_, response.headers()))
      response
    } else {
      val response = new DefaultFullHttpResponse(
        httpVersion,
        HttpResponseStatus.valueOf(httpResponse.status.code)
      )
      httpResponse.headers.foreach(appendToNettyHeaders(_, response.headers()))
      if (HttpUtil.isContentLengthSet(response))
        response.headers().remove(`Content-Length`.name.toString())
      response
    }
    if (!defaultResponse.headers().contains(HttpHeaderNames.DATE))
      defaultResponse.headers().add(HttpHeaderNames.DATE, dateString)
    defaultResponse
  }

  private[this] def toWSResponse[F[_]](
      httpRequest: Request[F],
      httpResponse: Response[F],
      httpVersion: HttpVersion,
      wsContext: WebSocketContext[F],
      dateString: String
  )(
      implicit F: Effect[F],
      ec: ExecutionContext
  ): F[DefaultHttpResponse] =
    if (httpRequest.headers.exists(
        h => h.name.toString.equalsIgnoreCase("Upgrade") && h.value.equalsIgnoreCase("websocket")
      )) {
      val wsProtocol = if (httpRequest.isSecure.exists(identity)) "wss" else "ws"
      val wsUrl = s"$wsProtocol://${httpRequest.serverAddr}${httpRequest.pathInfo}"
      val bufferLimit = 65535 //Todo: Configurable. Probably param
      val factory = new WebSocketServerHandshakerFactory(wsUrl, "*", true, bufferLimit)
      StreamSubscriber[F, WebSocketFrame].flatMap { subscriber =>
        F.delay {
            val processor = new Processor[WSFrame, WSFrame] {
              def onError(t: Throwable): Unit = subscriber.onError(t)

              def onComplete(): Unit = subscriber.onComplete()

              def onNext(t: WSFrame): Unit = subscriber.onNext(nettyWsToHttp4s(t))

              def onSubscribe(s: Subscription): Unit = subscriber.onSubscribe(s)

              def subscribe(s: Subscriber[_ >: WSFrame]): Unit =
                wsContext.webSocket.send.map(wsbitsToNetty).toUnicastPublisher().subscribe(s)
            }

            F.runAsync {
                Async.shift[F](ec) >> subscriber.stream
                  .through(wsContext.webSocket.receive)
                  .compile
                  .drain
              }(_ => IO.unit)
              .unsafeRunSync()
            val resp: DefaultHttpResponse =
              new DefaultWebSocketHttpResponse(
                httpVersion,
                HttpResponseStatus.OK,
                processor,
                factory)
            wsContext.headers.foreach(appendToNettyHeaders(_, resp.headers()))
            resp
          }
          .handleErrorWith(_ =>
            wsContext.failureResponse.map(toNonWSResponse[F](_, httpVersion, dateString)))
      }
    } else {
      F.pure(toNonWSResponse[F](httpResponse, httpVersion, dateString))
    }

  private[this] def wsbitsToNetty(w: WebSocketFrame): WSFrame =
    w match {
      case Text(str, last) => new TextWebSocketFrame(last, 0, str)
      case Binary(data, last) => new BinaryWebSocketFrame(last, 0, Unpooled.wrappedBuffer(data))
      case Ping(data) => new PingWebSocketFrame(Unpooled.wrappedBuffer(data))
      case Pong(data) => new PongWebSocketFrame(Unpooled.wrappedBuffer(data))
      case Continuation(data, last) =>
        new ContinuationWebSocketFrame(last, 0, Unpooled.wrappedBuffer(data))
      case Close(data) => new CloseWebSocketFrame(true, 0, Unpooled.wrappedBuffer(data))
    }

  private[this] def nettyWsToHttp4s(w: WSFrame): WebSocketFrame =
    w match {
      case c: TextWebSocketFrame => Text(bytebufToArray(c.content()), c.isFinalFragment)
      case c: BinaryWebSocketFrame => Binary(bytebufToArray(c.content()), c.isFinalFragment)
      case c: PingWebSocketFrame => Ping(bytebufToArray(c.content()))
      case c: PongWebSocketFrame => Pong(bytebufToArray(c.content()))
      case c: ContinuationWebSocketFrame =>
        Continuation(bytebufToArray(c.content()), c.isFinalFragment)
      case c: CloseWebSocketFrame => Close(bytebufToArray(c.content()))
    }

  /** Convert a Chunk to a Netty ByteBuf. */
  private[this] def chunkToNetty(bytes: Chunk[Byte]): HttpContent =
    if (bytes.isEmpty)
      CachedEmpty
    else
      bytes match {
        case c: Chunk.Bytes =>
          new DefaultHttpContent(Unpooled.wrappedBuffer(c.values, c.offset, c.length))
        case c: Chunk.ByteBuffer =>
          new DefaultHttpContent(Unpooled.wrappedBuffer(c.buf))
        case _ =>
          new DefaultHttpContent(Unpooled.wrappedBuffer(bytes.toArray))
      }

  private[this] def bytebufToArray(buf: ByteBuf): Array[Byte] = {
    val array = new Array[Byte](buf.readableBytes())
    buf.readBytes(array)
    buf.release()
    array
  }

  private[this] val CachedEmpty: DefaultHttpContent =
    new DefaultHttpContent(Unpooled.EMPTY_BUFFER)

}
