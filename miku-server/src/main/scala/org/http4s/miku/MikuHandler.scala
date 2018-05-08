package org.http4s.miku

import java.io.IOException
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

import cats.effect.{Async, Effect, IO}
import cats.syntax.all._
import io.netty.channel.{ChannelInboundHandlerAdapter, _}
import io.netty.handler.codec.TooLongFrameException
import io.netty.handler.codec.http._
import io.netty.handler.timeout.IdleStateEvent
import org.http4s.server.ServiceErrorHandler
import org.http4s.util.execution.trampoline
import org.http4s.{HttpService, Request, Response}
import org.log4s.getLogger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/** Netty request handler
  *
  * Adapted from PlayRequestHandler.scala
  * in
  * https://github.com/playframework/playframework/blob/master/framework/src/play-netty-server
  *
  * Variables inside this handler are essentially local to a thread in the
  * MultithreadedEventLoopGroup, as they are not mutated anywhere else.
  *
  * A note about "lastResponseSent" to help understand:
  * By reassigning the variable with a `flatMap` (which doesn't require synchronization at all, since
  * you can consider this handler essentially single threaded), this means that, while we can run the
  * `handle` action asynchronously by forking it into a thread done in `handle`
  *
  */
sealed abstract class MikuHandler[F[_]](
    implicit F: Effect[F]
) extends ChannelInboundHandlerAdapter {

  // We keep track of whether there are requests in flight.  If there are, we don't respond to read
  // complete, since back pressure is the responsibility of the streams.
  private[this] val requestsInFlight = new AtomicLong()

  // This is used essentially as a queue, each incoming request attaches callbacks to this
  // and replaces it to ensure that responses are written out in the same order that they came
  // in.
  private[this] var lastResponseSent: Future[Unit] = Future.successful(())

  // Cache the formatter thread locally
  private[this] val RFC7231InstantFormatter =
    DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
      .withLocale(Locale.US)
      .withZone(ZoneId.of("GMT"))

  // Compute the formatted date string only once per second, and cache the result.
  // This should help microscopically under load.
  private[this] var cachedDate: Long = Long.MinValue
  private[this] var cachedDateString: String = _

  protected val logger = getLogger

  /**
    * Handle the given request.
    */
  def handle(channel: Channel, request: HttpRequest, dateString: String): F[DefaultHttpResponse]

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    logger.trace(s"channelRead: ctx = $ctx, msg = $msg")
    val newTick: Long = System.currentTimeMillis() / 1000
    if (cachedDate < newTick) {
      cachedDateString = RFC7231InstantFormatter.format(Instant.ofEpochSecond(newTick))
      cachedDate = newTick
    }

    msg match {
      case req: HttpRequest =>
        requestsInFlight.incrementAndGet()
        val p: Promise[HttpResponse] = Promise[HttpResponse]
        //Start execution of the handler.
        F.runAsync(handle(ctx.channel(), req, cachedDateString)) {
            case Left(error) =>
              IO {
                logger.error(error)("Exception caught in channelRead future")
                p.complete(Failure(error)); ()
              }

            case Right(httpResponse) =>
              IO {
                p.complete(Success(httpResponse)); ()
              }

          }
          .unsafeRunSync()
        val futureResponse: Future[HttpResponse] = p.future

        //This attaches all writes sequentially using
        //LastResponseSent as a queue. `trampoline` ensures we do not
        //CTX switch the writes.
        lastResponseSent = lastResponseSent.flatMap[Unit] { _ =>
          futureResponse
            .map[Unit] { response =>
              if (requestsInFlight.decrementAndGet() == 0) {
                // Since we've now gone down to zero, we need to issue a
                // read, in case we ignored an earlier read complete
                ctx.read()
              }
              ctx.writeAndFlush(response); ()
            }(trampoline)
            .recover[Unit] {
              case error: Exception =>
                logger.error(error)("Exception caught in channelRead future")
                sendSimpleErrorResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE); ()
            }(trampoline)
        }(trampoline)

      case _ =>
        logger.error("Invalid type")
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    logger.trace(s"channelReadComplete: ctx = $ctx")

    // The normal response to read complete is to issue another read,
    // but we only want to do that if there are no requests in flight,
    // this will effectively limit the number of in flight requests that
    // we'll handle by pushing back on the TCP stream, but it also ensures
    // we don't get in the way of the request body reactive streams,
    // which will be using channel read complete and read to implement
    // their own back pressure
    if (requestsInFlight.get() == 0) {
      ctx.read(); ()
    } else {
      // otherwise forward it, so that any handler publishers downstream
      // can handle it
      ctx.fireChannelReadComplete(); ()
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    cause match {
      // IO exceptions happen all the time, it usually just means that the client has closed the connection before fully
      // sending/receiving the response.
      case e: IOException =>
        logger.trace(e)("Benign IO exception caught in Netty")
        ctx.channel().close(); ()
      case e: TooLongFrameException =>
        logger.warn(e)("Handling TooLongFrameException")
        sendSimpleErrorResponse(ctx, HttpResponseStatus.REQUEST_URI_TOO_LONG); ()
      case e: IllegalArgumentException
          if Option(e.getMessage)
            .exists(_.contains("Header value contains a prohibited character")) =>
        // https://github.com/netty/netty/blob/netty-3.9.3.Final/src/main/java/org/jboss/netty/handler/codec/http/HttpHeaders.java#L1075-L1080
        logger.debug(e)("Handling Header value error")
        sendSimpleErrorResponse(ctx, HttpResponseStatus.BAD_REQUEST); ()
      case e =>
        logger.error(e)("Exception caught in Netty")
        ctx.channel().close(); ()
    }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    // AUTO_READ is off, so need to do the first read explicitly.
    // this method is called when the channel is registered with the event loop,
    // so ctx.read is automatically safe here w/o needing an isRegistered().
    ctx.read(); ()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: scala.Any): Unit =
    evt match {
      case _: IdleStateEvent if ctx.channel().isOpen =>
        logger.trace(s"Closing connection due to idle timeout")
        ctx.close(); ()
      case _ => super.userEventTriggered(ctx, evt)
    }

  private def sendSimpleErrorResponse(
      ctx: ChannelHandlerContext,
      status: HttpResponseStatus): ChannelFuture = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
    response.headers().set(HttpHeaderNames.CONNECTION, "close")
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "0")
    val f = ctx.channel().write(response)
    f.addListener(ChannelFutureListener.CLOSE)
    f
  }
}

object MikuHandler {
  private class DefaultHandler[F[_]](
      service: HttpService[F],
      serviceErrorHandler: ServiceErrorHandler[F])(
      implicit F: Effect[F],
      ec: ExecutionContext
  ) extends MikuHandler[F] {
    private[this] val unwrapped: Request[F] => F[Response[F]] =
      service.mapF(_.getOrElse(Response.notFound[F])).run

    override def handle(
        channel: Channel,
        request: HttpRequest,
        dateString: String): F[DefaultHttpResponse] = {
      logger.trace("Http request received by netty: " + request)
      Async.shift(ec) >> NettyModelConversion
        .fromNettyRequest[F](channel, request)
        .flatMap(
          request =>
            F.suspend(unwrapped(request))
              .recoverWith(serviceErrorHandler(request)))
        .map(NettyModelConversion.toNettyResponse[F](_, dateString))
    }
  }

  private class WebsocketHandler[F[_]](
      service: HttpService[F],
      serviceErrorHandler: ServiceErrorHandler[F])(
      implicit F: Effect[F],
      ec: ExecutionContext
  ) extends MikuHandler[F] {
    private[this] val unwrapped: Request[F] => F[Response[F]] =
      service.mapF(_.getOrElse(Response.notFound[F])).run

    override def handle(
        channel: Channel,
        request: HttpRequest,
        dateString: String): F[DefaultHttpResponse] = {
      logger.trace("Http request received by netty: " + request)
      Async.shift(ec) >> NettyModelConversion
        .fromNettyRequest[F](channel, request)
        .flatMap { request =>
          F.suspend(unwrapped(request))
            .recoverWith(serviceErrorHandler(request))
            .flatMap(NettyModelConversion.toNettyResponseWithWebsocket[F](request, _, dateString))
        }
    }
  }

  def default[F[_]](service: HttpService[F], serviceErrorHandler: ServiceErrorHandler[F])(
      implicit F: Effect[F],
      ec: ExecutionContext
  ): MikuHandler[F] = new DefaultHandler[F](service, serviceErrorHandler)

  def websocket[F[_]](service: HttpService[F], serviceErrorHandler: ServiceErrorHandler[F])(
      implicit F: Effect[F],
      ec: ExecutionContext
  ): MikuHandler[F] = new WebsocketHandler[F](service, serviceErrorHandler)
}
