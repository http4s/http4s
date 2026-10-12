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
import cats.effect.Outcome
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
import io.netty.util.Version
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.{Future => NettyFuture}
import scodec.bits.ByteVector

import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException

import scala.collection.mutable
import scala.concurrent.Promise

/** Application bindings for Netty's native HTTP codecs, over loopback TCP.
  *
  * HTTP/1 uses HttpServerCodec plus HttpServerKeepAliveHandler. Decoder failures in
  * request heads become a 400 response; failures after delivery of a head close the
  * connection. HTTP/2 uses Http2FrameCodec and Http2MultiplexHandler with default protocol
  * settings; resource shutdown does not wait for outstanding streams to finish.
  * The driver exposes final response heads (and HTTP/1 upgrades); informational
  * heads are consumed internally. This is an API policy, not a wire requirement.
  */
final class NettyTarget[F[_]](observer: Option[AdapterFailure => F[Unit]] = None)(implicit
    F: Async[F]
) extends WireTarget[F] {
  import NettyTarget._

  override def withFailureObserver(observe: AdapterFailure => F[Unit]): WireTarget[F] =
    new NettyTarget[F](Some(observe))

  private def make[A](acquire: F[A])(finalize: A => F[Unit]): Resource[F, A] =
    AdapterFailure.make(acquire, observer)(finalize)

  val name: String = "netty"
  val version: String = Version
    .identify(classOf[Http2FrameCodec].getClassLoader)
    .get("netty-codec-http2")
    .artifactVersion()

  override def settings(protocol: Protocol, role: Role): Vector[(String, String)] =
    Vector(
      "binding" -> (if (protocol == Protocol.Http1) "native-http1-codecs-minimal-driver"
                    else "native-http2-frame-codec-and-http-conversion-util"),
      "event-loop-threads" -> EventLoopThreads.toString,
      "event-loop-shutdown-quiet-period-ms" -> ShutdownMillis.toString,
      "event-loop-shutdown-timeout-ms" -> ShutdownMillis.toString,
      "codec-options" -> "Netty defaults except listed overrides",
      "auto-read" -> (role == Role.Server || protocol == Protocol.Http2).toString,
    ) ++ (if (protocol == Protocol.Http2)
            Vector(
              "graceful-shutdown-timeout-ms" -> ShutdownMillis.toString,
              "stream-auto-read" -> (role == Role.Server).toString,
              "header-conversion-validate" -> ValidateHeaders.toString,
              "pipeline" -> "Http2FrameCodec,Http2MultiplexHandler",
            )
          else
            Vector(
              "pipeline" -> (if (role == Role.Server)
                               "HttpServerCodec,HttpServerKeepAliveHandler"
                             else "HttpClientCodec")
            )) ++
      (if (role == Role.Server) Vector("max-echo-bytes" -> MaxEchoBytes.toString) else Vector.empty)

  def server(protocol: Protocol, scenario: ServerScenario): Resource[F, ServerHandle[F]] =
    for {
      group <- eventLoops
      received <- make(F.delay(new Observations[F]))(_.checkFailure)
      connections <- make(F.delay(new DefaultChannelGroup(group.next())))(channels =>
        adapter("closing server connections")(awaitFuture(channels.close()).void)
      )
      channel <- make(
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
                  received.offer,
                  received.fail,
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
                          .gracefulShutdownTimeoutMillis(ShutdownMillis)
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
    } yield ServerHandle(address, received.take, received.poll)

  def client(
      protocol: Protocol,
      address: SocketAddress[IpAddress],
  ): Resource[F, ClientDriver[F]] =
    for {
      group <- eventLoops
      connection <- Resource.eval(F.delay(new ClientConnection(protocol)))
      channel <- make(
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
                          .gracefulShutdownTimeoutMillis(ShutdownMillis)
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
        run(request, _ => Stream.chunk(Chunk.byteVector(request.body)), Some(request.body.size))

      def exchangeStream(
          request: RequestData,
          body: FixtureCleanup[F] => Stream[F, Byte],
      ): Resource[F, ResponseData[F]] = run(request, body, None)

      private def run(
          request: RequestData,
          body: FixtureCleanup[F] => Stream[F, Byte],
          length: Option[Long],
      ): Resource[F, ResponseData[F]] = {
        val stream = protocol match {
          case Protocol.Http1 => serial.permit.as(channel)
          case Protocol.Http2 =>
            make(
              awaitFuture(
                new Http2StreamChannelBootstrap(channel)
                  .option[java.lang.Boolean](ChannelOption.AUTO_READ, false)
                  .handler(new ChannelInboundHandlerAdapter())
                  .open()
              ).widen[Channel]
            )(close)
        }
        for {
          failures <- AdapterFailure.recorder[F](observer)
          source = failures.source("reading request source")(
            Stream.suspend(body(failures.fixtureCleanup))
          )
          receiver <- make(F.delay(new ResponseReceiver(protocol)))(receiver =>
            onEventLoop(channel)(receiver.adapterFailure).flatMap(_.traverse_(F.raiseError[Unit]))
          )
          exchangeChannel <- stream
          _ <- make(onEventLoop(channel) {
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
              .handleErrorWith(error =>
                F.raiseError(new AdapterFailure("releasing receiver", error))
              )
          )
          _ <- requestUploader(exchangeChannel, protocol) { written =>
            sendRequest(exchangeChannel, protocol, address, request, source, length, written)
              .handleErrorWith(error =>
                F.uncancelable { poll =>
                  failures.retain(error) *> poll(onEventLoop(channel) {
                    receiver.fail(error)
                    if (protocol == Protocol.Http2) {
                      val _ = exchangeChannel.pipeline().fireExceptionCaught(error)
                    }
                    exchangeChannel.close()
                    ()
                  })
                }
              )
          }
          head <- Resource.eval(awaitPromise(receiver.head))
        } yield ResponseData(
          head._1,
          head._2,
          Stream
            .repeatEval(takeBody(exchangeChannel, receiver))
            .unNoneTerminate
            .flatMap(Stream.chunk),
          awaitPromise(receiver.trailers),
        )
      }
    }

  /** Cancellation after a successful terminal write only stops the effect continuation.
    * The HTTP/1 codec and peer have the complete request, so the connection is reusable.
    * Incomplete writes and HTTP/2 stream cancellation still close their channel.
    */
  private[wire] def requestUploader(
      channel: Channel,
      protocol: Protocol,
  )(send: ChannelPromise => F[Unit]): Resource[F, Unit] =
    Resource.eval(F.delay(channel.newPromise())).flatMap { written =>
      make(
        F.start(
          F.onCancel(
            send(written),
            F.delay(protocol == Protocol.Http1 && written.isSuccess).flatMap { complete =>
              if (complete) F.unit else close(channel)
            },
          )
        )
      )(fiber =>
        adapter("canceling request uploader") {
          fiber.cancel *> fiber.join.flatMap {
            case Outcome.Errored(error) => F.raiseError[Unit](error)
            case _ => F.unit
          }
        }
      ).void
    }

  private def sendRequest(
      channel: Channel,
      protocol: Protocol,
      address: SocketAddress[IpAddress],
      request: RequestData,
      body: Stream[F, Byte],
      length: Option[Long],
      written: ChannelPromise,
  ): F[Unit] = {
    val sendHead = F
      .delay {
        protocol match {
          case Protocol.Http1 =>
            // An in-memory request is one native write, including its end marker.
            // In particular, a prompt response to Content-Length: 0 cannot race
            // asynchronous source evaluation before the codec sees LastHttpContent.
            val message = length match {
              case Some(_) =>
                new DefaultFullHttpRequest(
                  HttpVersion.HTTP_1_1,
                  HttpMethod.valueOf(request.method),
                  request.path,
                  Unpooled.wrappedBuffer(request.body.toArray),
                )
              case None =>
                new DefaultHttpRequest(
                  HttpVersion.HTTP_1_1,
                  HttpMethod.valueOf(request.method),
                  request.path,
                )
            }
            request.headers.foreach { case (name, value) => message.headers().add(name, value) }
            if (!message.headers().contains(HttpHeaderNames.HOST)) {
              val _ = message.headers().set(HttpHeaderNames.HOST, address.toString)
            }
            if (
              !HttpUtil.isContentLengthSet(message) && !HttpUtil.isTransferEncodingChunked(message)
            )
              length match {
                case Some(value) => HttpUtil.setContentLength(message, value)
                case None => HttpUtil.setTransferEncodingChunked(message, true)
              }
            message
          case Protocol.Http2 =>
            val message = new DefaultHttpRequest(
              HttpVersion.HTTP_1_1,
              HttpMethod.valueOf(request.method),
              request.path,
            )
            request.headers.foreach { case (name, value) => message.headers().add(name, value) }
            if (!message.headers().contains(HttpHeaderNames.HOST)) {
              val _ = message.headers().set(HttpHeaderNames.HOST, address.toString)
            }
            message.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http")
            new DefaultHttp2HeadersFrame(
              HttpConversionUtil.toHttp2Headers(message, ValidateHeaders),
              false,
            )
        }
      }
      .adaptError { case error => new AdapterFailure("constructing request head", error) }
      .flatMap { message =>
        if (protocol == Protocol.Http1 && length.isDefined)
          awaitFuture(channel.writeAndFlush(message, written)).void
        else awaitFuture(channel.writeAndFlush(message)).void
      }

    if (protocol == Protocol.Http1 && length.isDefined) sendHead
    else
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
        .drain *> awaitFuture(
        channel.writeAndFlush(
          protocol match {
            case Protocol.Http1 => LastHttpContent.EMPTY_LAST_CONTENT
            case Protocol.Http2 => new DefaultHttp2DataFrame(true)
          },
          written,
        )
      ).void
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
    make(
      F.delay(new MultiThreadIoEventLoopGroup(EventLoopThreads, NioIoHandler.newFactory()))
    )(group =>
      F.defer {
        val shutdown =
          group.shutdownGracefully(ShutdownMillis, ShutdownMillis, TimeUnit.MILLISECONDS)
        adapter("shutting down event loops")(awaitFuture(shutdown).void)
      }
    )

  private def adapter[A](operation: String)(effect: F[A]): F[A] =
    effect.handleErrorWith { error =>
      val failure = AdapterFailure.find(error).getOrElse(new AdapterFailure(operation, error))
      F.uncancelable(_ => observer.traverse_(_(failure)) *> F.raiseError[A](failure))
    }

  private def close(channel: Channel): F[Unit] =
    adapter("closing channel")(awaitFuture(channel.close()).void)

  private def channelFuture(future: => ChannelFuture): F[Channel] =
    F.defer {
      val started = future
      awaitFuture(started).as(started.channel())
    }

  private def awaitPromise[A](promise: Promise[A]): F[A] =
    F.executionContext.flatMap { executionContext =>
      F.async[A] { callback =>
        F.delay {
          promise.future.onComplete(result => callback(result.toEither))(executionContext)
          Some(F.unit)
        }
      }
    }

  private[wire] def awaitFuture[A](future: => NettyFuture[A]): F[A] =
    F.async[A] { callback =>
      F.delay {
        val started = future
        val listener = new GenericFutureListener[NettyFuture[A]] {
          def operationComplete(completed: NettyFuture[A]): Unit =
            if (completed.isSuccess) callback(Right(completed.getNow))
            else callback(Left(Option(completed.cause()).getOrElse(new CancellationException)))
        }
        started.addListener(listener)
        Some(F.delay {
          started.removeListener(listener)
          started.cancel(false)
          ()
        })
      }
    }
}

object NettyTarget {
  def apply[F[_]: Async](): NettyTarget[F] = new NettyTarget[F]

  private type Headers = Vector[(String, String)]
  private type BodyResult = Either[Throwable, Option[Chunk[Byte]]]
  private val EventLoopThreads = 1
  private val ShutdownMillis = 0L
  private val ValidateHeaders = true
  private val MaxEchoBytes = 1024L * 1024L

  /** Publication happens before the handler writes its response. Waiting only signals
    * availability: cancellation cannot consume an observation intended for a later poll.
    */
  private[wire] final class Observations[F[_]](implicit F: Async[F]) {
    private val pending = mutable.Queue.empty[RequestData]
    private val waiters = mutable.Map.empty[Object, Either[Throwable, Unit] => Unit]
    private var failure: Option[AdapterFailure] = None

    def offer(request: RequestData): Unit = synchronized {
      pending.enqueue(request)
      wake()
    }

    def fail(error: AdapterFailure): Unit = synchronized {
      if (failure.isEmpty) failure = Some(error)
      wake()
    }

    private def wake(): Unit = {
      val callbacks = waiters.values.toVector
      waiters.clear()
      callbacks.foreach(_(Right(())))
    }

    def checkFailure: F[Unit] = F.defer {
      synchronized(failure).fold(F.unit)(F.raiseError[Unit])
    }

    def poll: F[Option[RequestData]] = F.delay {
      synchronized {
        failure.foreach(throw _)
        if (pending.nonEmpty) Some(pending.dequeue()) else None
      }
    }

    private def available: F[Unit] = F.async { callback =>
      F.delay {
        val token = new Object
        synchronized {
          if (pending.nonEmpty || failure.nonEmpty) callback(Right(()))
          else waiters.update(token, callback)
        }
        Some(F.delay(synchronized { waiters.remove(token); () }))
      }
    }

    def take: F[RequestData] = F.uncancelable { cancelable =>
      def loop: F[RequestData] = poll.flatMap {
        case Some(request) => F.pure(request)
        case None => cancelable(available) *> loop
      }
      loop
    }
  }

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
  private[wire] final class ResponseReceiver(protocol: Protocol)
      extends ChannelInboundHandlerAdapter {
    val head: Promise[(Int, Headers)] = Promise()
    val trailers: Promise[Headers] = Promise()
    private val pending = mutable.Queue.empty[Chunk[Byte]]
    private var terminal: Option[BodyResult] = None
    private var waiter: Option[(Object, BodyResult => Unit)] = None
    private var context: ChannelHandlerContext = _
    private var finalHead = false
    private var resetFailure: Option[IOException] = None
    var adapterFailure: Option[AdapterFailure] = None

    def isComplete: Boolean = terminal.isDefined

    def attach(ctx: ChannelHandlerContext): Unit = context = ctx

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = attach(ctx)

    def demand(token: Object, callback: BodyResult => Unit): Unit = {
      waiter = Some(token -> callback)
      deliver()
      if (waiter.isDefined) {
        val _ = context.read()
      }
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

    def fail(error: Throwable): Unit = {
      adapterFailure = adapterFailure.orElse(AdapterFailure.find(error))
      // Body completion does not imply that a final response head was received.
      head.tryFailure(error)
      trailers.tryFailure(error)
      if (terminal.isEmpty) {
        terminal = Some(Left(error))
        deliver()
      }
    }

    override def channelRead(ctx: ChannelHandlerContext, message: Any): Unit =
      try
        message match {
          case value: HttpObject if value.decoderResult().isFailure =>
            exceptionCaught(ctx, value.decoderResult().cause())
          case _ => readMessage(message)
        }
      catch {
        case error: Http2Exception => exceptionCaught(ctx, error)
        case scala.util.control.NonFatal(error) =>
          fail(new AdapterFailure("observing response", error))
          ctx.close()
          ()
      } finally {
        ReferenceCountUtil.release(message)
        ()
      }

    private def readMessage(message: Any): Unit =
      protocol match {
        case Protocol.Http1 =>
          message match {
            case response: HttpResponse =>
              val status = response.status().code()
              if (status >= 200 || status == 101) {
                finalHead = true
                head.trySuccess(status -> headers(response.headers()))
                ()
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
                val status = HttpConversionUtil.parseStatus(value.headers().status()).code()
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
    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
      if (terminal.isEmpty && (!finalHead || waiter.isDefined)) {
        val _ = ctx.read()
      }
      ()
    }

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
      fail(
        resetFailure.getOrElse(new EOFException("Connection closed before the response completed"))
      )
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
          val error = new IOException(s"HTTP/2 stream reset: ${reset.errorCode()}")
          if (reset.errorCode() == Http2Error.NO_ERROR.code()) {
            // MultiplexHandler delivers resets ahead of buffered HEADERS/DATA.
            // Native stream closure waits for those frames to drain: retain a
            // completed response and fail unfinished promises at channelInactive.
            resetFailure = Some(error)
            ctx.read()
          } else fail(error)
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
      failed: AdapterFailure => Unit,
  ) extends ChannelInboundHandlerAdapter {
    private var request: Option[(RequestData, HttpVersion)] = None
    private var body = ByteVector.empty
    private val replies = mutable.Queue.empty[(HttpVersion, Reply)]
    private var writing = false
    private var rejecting = false

    private def start(
        ctx: ChannelHandlerContext,
        value: RequestData,
        version: HttpVersion,
    ): Unit = {
      request = Some(value -> version)
      body = ByteVector.empty
      if (scenario != ServerScenario.EchoBody) respond(ctx, value, version)
    }

    private def content(value: ByteBuf): Unit =
      if (scenario == ServerScenario.EchoBody) {
        if (body.size + value.readableBytes().toLong > MaxEchoBytes)
          throw new IOException(s"EchoBody exceeds the $MaxEchoBytes-byte harness limit")
        body = body ++ bytes(value).toByteVector
      }

    private def end(ctx: ChannelHandlerContext): Unit = {
      if (scenario == ServerScenario.EchoBody)
        request.foreach { case (value, version) => respond(ctx, value.copy(body = body), version) }
      request = None
      body = ByteVector.empty
    }

    private def respond(
        ctx: ChannelHandlerContext,
        value: RequestData,
        version: HttpVersion,
    ): Unit = {
      received(value)
      replies.enqueue(version -> reply(scenario, value))
      if (!writing) writeNext(ctx)
    }

    private def writeNext(ctx: ChannelHandlerContext): Unit =
      if (replies.nonEmpty) {
        writing = true
        val (version, value) = replies.dequeue()
        val message = protocol match {
          case Protocol.Http1 =>
            val response = new DefaultHttpResponse(
              version,
              HttpResponseStatus.valueOf(value.status),
            )
            value.headers.foreach { case (name, content) => response.headers().add(name, content) }
            if (
              !HttpUtil
                .isContentLengthSet(response) && !HttpUtil.isTransferEncodingChunked(response)
            ) {
              if (value.streamed) {
                // HTTP/1.0 streams have no length: the native keep-alive handler
                // closes the connection after the last content instead of chunking.
                if (version.compareTo(HttpVersion.HTTP_1_1) >= 0)
                  HttpUtil.setTransferEncodingChunked(response, true)
              } else HttpUtil.setContentLength(response, value.chunks.foldLeft(0L)(_ + _.size))
            }
            response
          case Protocol.Http2 =>
            val response = new DefaultHttpResponse(
              HttpVersion.HTTP_1_1,
              HttpResponseStatus.valueOf(value.status),
            )
            value.headers.foreach { case (name, content) => response.headers().add(name, content) }
            new DefaultHttp2HeadersFrame(
              HttpConversionUtil.toHttp2Headers(response, ValidateHeaders),
              false,
            )
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
              def run(): Unit =
                try next
                catch { case scala.util.control.NonFatal(error) => adapterFailed(ctx, error) }
            })
        else {
          if (protocol == Protocol.Http2) {
            val _ = ctx.fireExceptionCaught(result.cause())
          }
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
            replies.enqueue(
              value.protocolVersion() -> Reply(
                400,
                Vector("connection" -> "close"),
                Vector.empty,
                false,
              )
            )
            if (!writing) writeNext(ctx)
            false
          case value: HttpObject if value.decoderResult().isFailure =>
            exceptionCaught(ctx, value.decoderResult().cause())
            false
          case _ => !rejecting
        }
        if (process) protocol match {
          case Protocol.Http1 =>
            message match {
              case value: HttpRequest =>
                start(
                  ctx,
                  RequestData(value.method().name(), value.uri(), headers(value.headers())),
                  value.protocolVersion(),
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
                      // CONNECT legitimately omits :path. Retain absent values
                      // as empty observations instead of manufacturing a reset
                      // through an adapter null dereference. The native codec
                      // remains responsible for validating pseudo-header rules.
                      Option(value.headers().method()).fold("")(_.toString),
                      Option(value.headers().path()).fold("")(_.toString),
                      headers(value.headers()),
                    ),
                    HttpVersion.HTTP_1_1,
                  )
                if (value.isEndStream) end(ctx)
              case value: Http2DataFrame =>
                content(value.content())
                if (value.isEndStream) end(ctx)
              case _ => ()
            }
        }
      } catch {
        case scala.util.control.NonFatal(error) => adapterFailed(ctx, error)
      } finally {
        ReferenceCountUtil.release(message)
        ()
      }

    private def adapterFailed(ctx: ChannelHandlerContext, error: Throwable): Unit = {
      failed(new AdapterFailure("serving fixture response", error))
      ctx.close()
      ()
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
      if (protocol == Protocol.Http2) ctx.fireExceptionCaught(error)
      else ctx.close()
      ()
    }
  }
}
