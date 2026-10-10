/*
 * Copyright 2026 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.wire

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.effect.std.Semaphore
import cats.syntax.all._
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import fs2.Chunk
import fs2.Stream
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.codec.http2._
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.{Future => NettyFuture}
import scodec.bits.ByteVector

import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

import scala.collection.mutable
import scala.concurrent.Promise

/** Application bindings for Netty's native HTTP codecs, over loopback TCP.
  *
  * HTTP/1 uses HttpServerCodec plus HttpServerKeepAliveHandler. Decoder failures in
  * request heads become a 400 response; failures after delivery of a head close the
  * connection. HTTP/2 uses Http2FrameCodec and Http2MultiplexHandler with default protocol
  * settings; resource shutdown does not wait for outstanding streams to finish.
  */
final class NettyTarget[F[_]](implicit F: Async[F]) extends WireTarget[F] {
  import NettyTarget._

  val name: String = "netty"
  val version: String = "4.2.19.Final"

  def server(protocol: Protocol, scenario: ServerScenario): Resource[F, ServerHandle[F]] =
    for {
      group <- eventLoops
      dispatcher <- Dispatcher.sequential[F]
      received <- Resource.eval(Queue.unbounded[F, RequestData])
      connections <- Resource.make(F.delay(new DefaultChannelGroup(group.next())))(channels =>
        awaitFuture(channels.close()).void
      )
      channel <- Resource.make(
        channelFuture {
          new ServerBootstrap()
            .group(group)
            .channel(classOf[NioServerSocketChannel])
            .childHandler(new ChannelInitializer[NioSocketChannel] {
              def initChannel(channel: NioSocketChannel): Unit = {
                connections.add(channel)
                def handler = new ServerHandler(
                  protocol,
                  scenario,
                  request => dispatcher.unsafeRunAndForget(received.offer(request)),
                )
                protocol match {
                  case Protocol.Http1 =>
                    channel
                      .pipeline()
                      .addLast(
                        new HttpServerCodec(),
                        new HttpServerKeepAliveHandler(),
                        handler,
                      )
                  case Protocol.Http2 =>
                    channel
                      .pipeline()
                      .addLast(
                        Http2FrameCodecBuilder
                          .forServer()
                          .gracefulShutdownTimeoutMillis(0L)
                          .build(),
                        new Http2MultiplexHandler(new ChannelInitializer[Http2StreamChannel] {
                          def initChannel(stream: Http2StreamChannel): Unit = {
                            stream.pipeline().addLast(handler)
                            ()
                          }
                        }),
                      )
                }
                ()
              }
            })
            .bind(InetAddress.getLoopbackAddress, 0)
        }
      )(close)
      address <- Resource.eval(F.delay {
        val socket = channel.localAddress().asInstanceOf[InetSocketAddress]
        SocketAddress(
          IpAddress.fromString(socket.getAddress.getHostAddress).get,
          Port.fromInt(socket.getPort).get,
        )
      })
    } yield ServerHandle(address, received.take, received.tryTake)

  def client(
      protocol: Protocol,
      address: SocketAddress[IpAddress],
  ): Resource[F, ClientDriver[F]] =
    for {
      group <- eventLoops
      connection <- Resource.eval(F.delay(new ClientConnection(protocol)))
      channel <- Resource.make(
        channelFuture {
          new Bootstrap()
            .group(group)
            .channel(classOf[NioSocketChannel])
            .option[java.lang.Boolean](ChannelOption.AUTO_READ, protocol == Protocol.Http2)
            .handler(new ChannelInitializer[NioSocketChannel] {
              def initChannel(channel: NioSocketChannel): Unit = {
                protocol match {
                  case Protocol.Http1 => channel.pipeline().addLast(new HttpClientCodec())
                  case Protocol.Http2 =>
                    channel
                      .pipeline()
                      .addLast(
                        Http2FrameCodecBuilder
                          .forClient()
                          .gracefulShutdownTimeoutMillis(0L)
                          .build(),
                        new Http2MultiplexHandler(new ChannelInitializer[Http2StreamChannel] {
                          def initChannel(stream: Http2StreamChannel): Unit = {
                            // This driver does not expose unsolicited server push streams.
                            stream.close()
                            ()
                          }
                        }),
                      )
                }
                channel.pipeline().addLast(connection)
                ()
              }
            })
            .connect(new InetSocketAddress(address.host.toString, address.port.value))
        }
      )(close)
      serial <- Resource.eval(Semaphore[F](1L))
    } yield new ClientDriver[F] {
      def exchange(request: RequestData): Resource[F, ResponseData[F]] =
        run(request, Stream.chunk(Chunk.byteVector(request.body)), Some(request.body.size))

      def exchangeStream(
          request: RequestData,
          body: Stream[F, Byte],
      ): Resource[F, ResponseData[F]] = run(request, body, None)

      private def run(
          request: RequestData,
          body: Stream[F, Byte],
          length: Option[Long],
      ): Resource[F, ResponseData[F]] = {
        val stream = protocol match {
          case Protocol.Http1 => serial.permit.as(channel)
          case Protocol.Http2 =>
            Resource.make(
              awaitFuture(
                new Http2StreamChannelBootstrap(channel)
                  .option[java.lang.Boolean](ChannelOption.AUTO_READ, false)
                  .handler(new ChannelInboundHandlerAdapter())
                  .open()
              ).widen[Channel]
            )(close)
        }
        for {
          exchangeChannel <- stream
          receiver <- Resource.eval(F.delay(new ResponseReceiver(protocol)))
          _ <- Resource.make(onEventLoop(channel) {
            connection.add(receiver)
            if (protocol == Protocol.Http2) exchangeChannel.pipeline().addLast(receiver)
            else receiver.attach(connection.context)
            exchangeChannel.read()
            ()
          })(_ =>
            onEventLoop(channel) {
              connection.remove(receiver)
              protocol == Protocol.Http1 && !receiver.isComplete
            }.flatMap(incomplete => if (incomplete) close(channel) else F.unit)
          )
          _ <- Resource.make(
            F.start(
              F.onCancel(
                sendRequest(exchangeChannel, protocol, address, request, body, length)
                  .handleErrorWith(error =>
                    onEventLoop(channel) {
                      receiver.fail(error)
                      if (protocol == Protocol.Http2)
                        exchangeChannel.pipeline().fireExceptionCaught(error)
                      exchangeChannel.close()
                      ()
                    }
                  ),
                close(exchangeChannel),
              )
            )
          )(_.cancel)
          head <- Resource.eval(F.fromFuture(F.delay(receiver.head.future)))
        } yield ResponseData(
          head._1,
          head._2,
          Stream
            .repeatEval(takeBody(exchangeChannel, receiver))
            .unNoneTerminate
            .flatMap(Stream.chunk),
          F.fromFuture(F.delay(receiver.trailers.future)),
        )
      }
    }

  private def sendRequest(
      channel: Channel,
      protocol: Protocol,
      address: SocketAddress[IpAddress],
      request: RequestData,
      body: Stream[F, Byte],
      length: Option[Long],
  ): F[Unit] = {
    val sendHead = F
      .delay {
        protocol match {
          case Protocol.Http1 =>
            val message = new DefaultHttpRequest(
              HttpVersion.HTTP_1_1,
              HttpMethod.valueOf(request.method),
              request.path,
            )
            request.headers.foreach { case (name, value) => message.headers().add(name, value) }
            if (!message.headers().contains(HttpHeaderNames.HOST))
              message.headers().set(HttpHeaderNames.HOST, address.toString)
            if (
              !HttpUtil.isContentLengthSet(message) && !HttpUtil.isTransferEncodingChunked(message)
            )
              length match {
                case Some(value) => HttpUtil.setContentLength(message, value)
                case None => HttpUtil.setTransferEncodingChunked(message, true)
              }
            message
          case Protocol.Http2 =>
            val headers = new DefaultHttp2Headers()
              .method(request.method)
              .scheme("http")
              .authority(address.toString)
              .path(request.path)
            request.headers.foreach { case (name, value) =>
              headers.add(name.toLowerCase(Locale.ROOT), value)
            }
            new DefaultHttp2HeadersFrame(headers, false)
        }
      }
      .flatMap(message => awaitFuture(channel.writeAndFlush(message)).void)

    sendHead *> body.chunks
      .evalMap { chunk =>
        F.delay {
          val content = Unpooled.wrappedBuffer(chunk.toArray)
          protocol match {
            case Protocol.Http1 => new DefaultHttpContent(content)
            case Protocol.Http2 => new DefaultHttp2DataFrame(content, false)
          }
        }.flatMap(message => awaitFuture(channel.writeAndFlush(message)).void)
      }
      .compile
      .drain *> awaitFuture(channel.writeAndFlush(protocol match {
      case Protocol.Http1 => LastHttpContent.EMPTY_LAST_CONTENT
      case Protocol.Http2 => new DefaultHttp2DataFrame(true)
    })).void
  }

  private def takeBody(
      channel: Channel,
      receiver: ResponseReceiver,
  ): F[Option[Chunk[Byte]]] =
    F.async { callback =>
      val token = new Object
      onEventLoop(channel)(receiver.demand(token, callback)).as(
        Some(onEventLoop(channel)(receiver.cancelDemand(token)))
      )
    }

  private def onEventLoop[A](channel: Channel)(action: => A): F[A] =
    F.async_[A] { callback =>
      channel
        .eventLoop()
        .execute(new Runnable {
          def run(): Unit = callback(Either.catchNonFatal(action))
        })
    }

  private def eventLoops: Resource[F, MultiThreadIoEventLoopGroup] =
    Resource.make(F.delay(new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())))(group =>
      awaitFuture(group.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS)).void
    )

  private def close(channel: Channel): F[Unit] = awaitFuture(channel.close()).void

  private def channelFuture(future: => ChannelFuture): F[Channel] =
    F.defer {
      val started = future
      awaitFuture(started).as(started.channel())
    }

  private def awaitFuture[A](future: => NettyFuture[A]): F[A] =
    F.async_[A] { callback =>
      future.addListener { (completed: NettyFuture[A]) =>
        if (completed.isSuccess) callback(Right(completed.getNow))
        else callback(Left(completed.cause()))
      }
      ()
    }
}

object NettyTarget {
  def apply[F[_]: Async](): NettyTarget[F] = new NettyTarget[F]

  private type Headers = Vector[(String, String)]
  private type BodyResult = Either[Throwable, Option[Chunk[Byte]]]
  private val MaxEchoBytes = 1024L * 1024L

  private def bytes(buffer: ByteBuf): Chunk[Byte] = {
    val result = new Array[Byte](buffer.readableBytes())
    buffer.getBytes(buffer.readerIndex(), result)
    Chunk.array(result)
  }

  private def headers(value: HttpHeaders): Headers = {
    val builder = Vector.newBuilder[(String, String)]
    val entries = value.iteratorAsString()
    while (entries.hasNext) {
      val entry = entries.next()
      builder += entry.getKey -> entry.getValue
    }
    builder.result()
  }

  private def headers(value: Http2Headers): Headers = {
    val builder = Vector.newBuilder[(String, String)]
    val entries = value.iterator()
    while (entries.hasNext) {
      val entry = entries.next()
      if (!Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat(entry.getKey))
        builder += entry.getKey.toString -> entry.getValue.toString
    }
    builder.result()
  }

  /** State below is confined to the channel's event loop. */
  private final class ResponseReceiver(protocol: Protocol) extends ChannelInboundHandlerAdapter {
    val head: Promise[(Int, Headers)] = Promise()
    val trailers: Promise[Headers] = Promise()
    private val pending = mutable.Queue.empty[Chunk[Byte]]
    private var terminal: Option[BodyResult] = None
    private var waiter: Option[(Object, BodyResult => Unit)] = None
    private var context: ChannelHandlerContext = _
    private var finalHead = false

    def isComplete: Boolean = terminal.isDefined

    def attach(ctx: ChannelHandlerContext): Unit = context = ctx

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = attach(ctx)

    def demand(token: Object, callback: BodyResult => Unit): Unit = {
      waiter = Some(token -> callback)
      deliver()
      if (waiter.isDefined) context.read()
      ()
    }

    def cancelDemand(token: Object): Unit =
      if (waiter.exists(_._1 eq token)) waiter = None

    private def deliver(): Unit =
      waiter.foreach { case (_, callback) =>
        val result = if (pending.nonEmpty) Some(Right(Some(pending.dequeue()))) else terminal
        result.foreach { next =>
          waiter = None
          callback(next)
        }
      }

    private def data(content: ByteBuf): Unit = {
      if (content.isReadable) pending.enqueue(bytes(content))
      deliver()
    }

    private def finish(value: Headers): Unit =
      if (terminal.isEmpty) {
        trailers.trySuccess(value)
        terminal = Some(Right(None))
        deliver()
      }

    def fail(error: Throwable): Unit =
      if (terminal.isEmpty) {
        head.tryFailure(error)
        trailers.tryFailure(error)
        terminal = Some(Left(error))
        deliver()
      }

    override def channelRead(ctx: ChannelHandlerContext, message: Any): Unit =
      try {
        message match {
          case value: HttpObject if value.decoderResult().isFailure =>
            throw value.decoderResult().cause()
          case _ => ()
        }
        protocol match {
          case Protocol.Http1 =>
            message match {
              case response: HttpResponse =>
                val status = response.status().code()
                if (status >= 200 || status == 101) {
                  finalHead = true
                  head.trySuccess(status -> headers(response.headers()))
                }
              case _ => ()
            }
            message match {
              case content: HttpContent if finalHead =>
                data(content.content())
                content match {
                  case last: LastHttpContent => finish(headers(last.trailingHeaders()))
                  case _ => ()
                }
              case _ => ()
            }
          case Protocol.Http2 =>
            message match {
              case value: Http2HeadersFrame =>
                if (!finalHead) {
                  val status = value.headers().status().toString.toInt
                  if (status >= 200) {
                    finalHead = true
                    head.trySuccess(status -> headers(value.headers()))
                    if (value.isEndStream) finish(Vector.empty)
                  }
                } else if (value.isEndStream) finish(headers(value.headers()))
              case value: Http2DataFrame =>
                data(value.content())
                if (value.isEndStream) finish(Vector.empty)
              case value: Http2ResetFrame =>
                fail(new IOException(s"HTTP/2 stream reset: ${value.errorCode()}"))
              case _ => ()
            }
        }
      } catch {
        case scala.util.control.NonFatal(error) =>
          fail(error)
          ctx.close()
          ()
      } finally {
        ReferenceCountUtil.release(message)
        ()
      }

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
      if (terminal.isEmpty && (!finalHead || waiter.isDefined)) ctx.read()
      ()
    }

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
      fail(new EOFException("Connection closed before the response completed"))
      ctx.fireChannelInactive()
      ()
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
      fail(error)
      // The native HTTP/2 tail closes streams with the embedded protocol error code.
      if (protocol == Protocol.Http2) ctx.fireExceptionCaught(error)
      else ctx.close()
      ()
    }

    override def userEventTriggered(ctx: ChannelHandlerContext, event: Any): Unit =
      event match {
        case reset: Http2ResetFrame =>
          fail(new IOException(s"HTTP/2 stream reset: ${reset.errorCode()}"))
          ()
        case goAway: Http2GoAwayFrame =>
          fail(new IOException(s"HTTP/2 stream refused by GOAWAY: ${goAway.errorCode()}"))
          ReferenceCountUtil.release(event)
          ()
        case _ =>
          ctx.fireUserEventTriggered(event)
          ()
      }
  }

  private final class ClientConnection(protocol: Protocol) extends ChannelInboundHandlerAdapter {
    private val receivers = mutable.LinkedHashSet.empty[ResponseReceiver]
    private var failure: Option[Throwable] = None
    var context: ChannelHandlerContext = _

    def add(receiver: ResponseReceiver): Unit = {
      failure.foreach(throw _)
      if (!context.channel().isActive) throw new ClosedChannelException
      receivers.add(receiver)
      ()
    }

    def remove(receiver: ResponseReceiver): Unit = {
      receivers.remove(receiver)
      ()
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = context = ctx

    override def channelRead(ctx: ChannelHandlerContext, message: Any): Unit =
      if (protocol == Protocol.Http1 && receivers.nonEmpty)
        receivers.head.channelRead(ctx, message)
      else {
        ReferenceCountUtil.release(message)
        ()
      }

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
      if (protocol == Protocol.Http1) receivers.headOption.foreach(_.channelReadComplete(ctx))
      ctx.fireChannelReadComplete()
      ()
    }

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
      val error = new EOFException("Connection closed before the response completed")
      failure = Some(error)
      // HTTP/2 child channels drain their already-received frames before becoming inactive.
      if (protocol == Protocol.Http1) receivers.foreach(_.fail(error))
      ctx.fireChannelInactive()
      ()
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
      failure = Some(error)
      receivers.foreach(_.fail(error))
      // Http2FrameCodec emits its GOAWAY after notifying this pipeline of the error.
      if (protocol == Protocol.Http2) ctx.fireExceptionCaught(error)
      else ctx.close()
      ()
    }
  }

  private final case class Reply(
      status: Int,
      headers: Headers,
      chunks: Vector[ByteVector],
      streamed: Boolean,
  )

  private def reply(scenario: ServerScenario, request: RequestData): Reply = scenario match {
    case ServerScenario.Reply(status, headers, body) => Reply(status, headers, Vector(body), false)
    case ServerScenario.EchoBody => Reply(200, Vector.empty, Vector(request.body), false)
    case ServerScenario.EchoHeaders =>
      val body = request.headers.map { case (name, value) => s"$name: $value\n" }.mkString
      Reply(200, Vector.empty, Vector(ByteVector(body.getBytes(StandardCharsets.UTF_8))), false)
    case ServerScenario.ReplyChunks(chunks) => Reply(200, Vector.empty, chunks, true)
  }

  private final class ServerHandler(
      protocol: Protocol,
      scenario: ServerScenario,
      received: RequestData => Unit,
  ) extends ChannelInboundHandlerAdapter {
    private var request: Option[RequestData] = None
    private var body = ByteVector.empty
    private val replies = mutable.Queue.empty[Reply]
    private var writing = false
    private var rejecting = false

    private def start(ctx: ChannelHandlerContext, value: RequestData): Unit = {
      request = Some(value)
      body = ByteVector.empty
      if (scenario != ServerScenario.EchoBody) respond(ctx, value)
    }

    private def content(value: ByteBuf): Unit =
      if (scenario == ServerScenario.EchoBody) {
        if (body.size + value.readableBytes().toLong > MaxEchoBytes)
          throw new IOException(s"EchoBody exceeds the $MaxEchoBytes-byte harness limit")
        body = body ++ bytes(value).toByteVector
      }

    private def end(ctx: ChannelHandlerContext): Unit = {
      if (scenario == ServerScenario.EchoBody)
        request.foreach(value => respond(ctx, value.copy(body = body)))
      request = None
      body = ByteVector.empty
    }

    private def respond(ctx: ChannelHandlerContext, value: RequestData): Unit = {
      received(value)
      replies.enqueue(reply(scenario, value))
      if (!writing) writeNext(ctx)
    }

    private def writeNext(ctx: ChannelHandlerContext): Unit =
      if (replies.nonEmpty) {
        writing = true
        val value = replies.dequeue()
        val message = protocol match {
          case Protocol.Http1 =>
            val response = new DefaultHttpResponse(
              HttpVersion.HTTP_1_1,
              HttpResponseStatus.valueOf(value.status),
            )
            value.headers.foreach { case (name, content) => response.headers().add(name, content) }
            if (
              !HttpUtil
                .isContentLengthSet(response) && !HttpUtil.isTransferEncodingChunked(response)
            ) {
              if (value.streamed) HttpUtil.setTransferEncodingChunked(response, true)
              else HttpUtil.setContentLength(response, value.chunks.foldLeft(0L)(_ + _.size))
            }
            response
          case Protocol.Http2 =>
            val response = new DefaultHttp2Headers().status(value.status.toString)
            value.headers.foreach { case (name, content) =>
              response.add(name.toLowerCase(Locale.ROOT), content)
            }
            new DefaultHttp2HeadersFrame(response, false)
        }
        write(ctx, message)(writeChunks(ctx, value.chunks.iterator))
      } else writing = false

    private def writeChunks(ctx: ChannelHandlerContext, chunks: Iterator[ByteVector]): Unit =
      if (chunks.hasNext) {
        val buffer = Unpooled.wrappedBuffer(chunks.next().toArray)
        val message = protocol match {
          case Protocol.Http1 => new DefaultHttpContent(buffer)
          case Protocol.Http2 => new DefaultHttp2DataFrame(buffer, false)
        }
        write(ctx, message)(writeChunks(ctx, chunks))
      } else {
        val last = protocol match {
          case Protocol.Http1 => LastHttpContent.EMPTY_LAST_CONTENT
          case Protocol.Http2 => new DefaultHttp2DataFrame(true)
        }
        write(ctx, last)(writeNext(ctx))
      }

    private def write(ctx: ChannelHandlerContext, message: Any)(next: => Unit): Unit = {
      ctx.writeAndFlush(message).addListener { (result: ChannelFuture) =>
        if (result.isSuccess)
          ctx
            .executor()
            .execute(new Runnable {
              def run(): Unit = next
            })
        else {
          if (protocol == Protocol.Http2) ctx.fireExceptionCaught(result.cause())
          ctx.close()
        }
        ()
      }
      ()
    }

    override def channelRead(ctx: ChannelHandlerContext, message: Any): Unit =
      try {
        val process = message match {
          case value: HttpRequest if value.decoderResult().isFailure =>
            rejecting = true
            replies.enqueue(Reply(400, Vector("connection" -> "close"), Vector.empty, false))
            if (!writing) writeNext(ctx)
            false
          case value: HttpObject if value.decoderResult().isFailure =>
            throw value.decoderResult().cause()
          case _ => !rejecting
        }
        if (process) protocol match {
          case Protocol.Http1 =>
            message match {
              case value: HttpRequest =>
                start(
                  ctx,
                  RequestData(value.method().name(), value.uri(), headers(value.headers())),
                )
              case _ => ()
            }
            message match {
              case value: HttpContent =>
                content(value.content())
                if (value.isInstanceOf[LastHttpContent]) end(ctx)
              case _ => ()
            }
          case Protocol.Http2 =>
            message match {
              case value: Http2HeadersFrame =>
                if (request.isEmpty)
                  start(
                    ctx,
                    RequestData(
                      value.headers().method().toString,
                      value.headers().path().toString,
                      headers(value.headers()),
                    ),
                  )
                if (value.isEndStream) end(ctx)
              case value: Http2DataFrame =>
                content(value.content())
                if (value.isEndStream) end(ctx)
              case _ => ()
            }
        }
      } catch {
        case scala.util.control.NonFatal(_) =>
          ctx.close()
          ()
      } finally {
        ReferenceCountUtil.release(message)
        ()
      }

    override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
      if (protocol == Protocol.Http2) ctx.fireExceptionCaught(error)
      else ctx.close()
      ()
    }
  }
}
