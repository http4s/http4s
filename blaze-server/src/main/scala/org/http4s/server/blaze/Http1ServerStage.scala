package org.http4s
package server
package blaze

import cats.effect.{CancelToken, ConcurrentEffect, IO, Sync, Timer}
import cats.implicits._
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import org.http4s.blaze.http.parser.BaseExceptions.{BadMessage, ParserException}
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.pipeline.{TailStage, Command => Cmd}
import org.http4s.blaze.util.{BufferTools, TickWheelExecutor}
import org.http4s.blaze.util.BufferTools.emptyBuffer
import org.http4s.blaze.util.Execution._
import org.http4s.blazecore.{Http1Stage, IdleTimeoutStage}
import org.http4s.blazecore.util.{BodylessWriter, Http1Writer}
import org.http4s.headers.{Connection, `Content-Length`, `Transfer-Encoding`}
import org.http4s.internal.unsafeRunAsync
import org.http4s.syntax.string._
import org.http4s.util.StringWriter
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Either, Failure, Left, Right, Success, Try}
import io.chrisdavenport.vault._

private[blaze] object Http1ServerStage {

  def apply[F[_]](
      routes: HttpApp[F],
      attributes: () => Vault,
      executionContext: ExecutionContext,
      enableWebSockets: Boolean,
      maxRequestLineLen: Int,
      maxHeadersLen: Int,
      chunkBufferMaxSize: Int,
      serviceErrorHandler: ServiceErrorHandler[F],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      scheduler: TickWheelExecutor)(
      implicit F: ConcurrentEffect[F],
      timer: Timer[F]): Http1ServerStage[F] =
    if (enableWebSockets)
      new Http1ServerStage(
        routes,
        attributes,
        executionContext,
        maxRequestLineLen,
        maxHeadersLen,
        chunkBufferMaxSize,
        serviceErrorHandler,
        responseHeaderTimeout,
        idleTimeout,
        scheduler) with WebSocketSupport[F]
    else
      new Http1ServerStage(
        routes,
        attributes,
        executionContext,
        maxRequestLineLen,
        maxHeadersLen,
        chunkBufferMaxSize,
        serviceErrorHandler,
        responseHeaderTimeout,
        idleTimeout,
        scheduler)
}

private[blaze] class Http1ServerStage[F[_]](
    httpApp: HttpApp[F],
    requestAttrs: () => Vault,
    implicit protected val executionContext: ExecutionContext,
    maxRequestLineLen: Int,
    maxHeadersLen: Int,
    override val chunkBufferMaxSize: Int,
    serviceErrorHandler: ServiceErrorHandler[F],
    responseHeaderTimeout: Duration,
    idleTimeout: Duration,
    scheduler: TickWheelExecutor)(implicit protected val F: ConcurrentEffect[F], timer: Timer[F])
    extends Http1Stage[F]
    with TailStage[ByteBuffer] {

  // micro-optimization: unwrap the routes and call its .run directly
  private[this] val runApp = httpApp.run

  // protected by synchronization on `parser`
  private[this] val parser = new Http1ServerParser[F](logger, maxRequestLineLen, maxHeadersLen)
  private[this] var isClosed = false
  private[this] var cancelToken: Option[CancelToken[F]] = None

  val name = "Http4sServerStage"

  logger.trace(s"Http4sStage starting up")

  final override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] =
    parser.synchronized {
      parser.doParseContent(buffer)
    }

  final override protected def contentComplete(): Boolean =
    parser.synchronized {
      parser.contentComplete()
    }

  // Will act as our loop
  override def stageStartup(): Unit = {
    logger.debug("Starting HTTP pipeline")
    initIdleTimeout()
    requestLoop()
  }

  private def initIdleTimeout() =
    idleTimeout match {
      case f: FiniteDuration =>
        val cb: Callback[TimeoutException] = {
          case Left(t) =>
            fatalError(t, "Error in idle timeout callback")
          case Right(_) =>
            logger.debug("Shutting down due to idle timeout")
            closePipeline(None)
        }
        val stage = new IdleTimeoutStage[ByteBuffer](f, scheduler, executionContext)
        spliceBefore(stage)
        stage.init(cb)
      case _ =>
    }

  private val handleReqRead: Try[ByteBuffer] => Unit = {
    case Success(buff) => reqLoopCallback(buff)
    case Failure(Cmd.EOF) => closeConnection()
    case Failure(t) => fatalError(t, "Error in requestLoop()")
  }

  private def requestLoop(): Unit = channelRead().onComplete(handleReqRead)(trampoline)

  private def reqLoopCallback(buff: ByteBuffer): Unit = {
    logRequest(buff)
    parser.synchronized {
      if (!isClosed) {
        try {
          if (!parser.requestLineComplete() && !parser.doParseRequestLine(buff)) {
            requestLoop()
          } else if (!parser.headersComplete() && !parser.doParseHeaders(buff)) {
            requestLoop()
          } else {
            // we have enough to start the request
            runRequest(buff)
          }
        } catch {
          case t: BadMessage =>
            badMessage("Error parsing status or headers in requestLoop()", t, Request[F]())
          case t: Throwable =>
            internalServerError(
              "error in requestLoop()",
              t,
              Request[F](),
              () => Future.successful(emptyBuffer))
        }
      }
    }
  }

  private def logRequest(buffer: ByteBuffer): Unit =
    logger.trace {
      val msg = BufferTools
        .bufferToString(buffer.duplicate())
        .replace("\r", "\\r")
        .replace("\n", "\\n\n")
      s"Received Request:\n$msg"
    }

  // Only called while holding the monitor of `parser`
  private def runRequest(buffer: ByteBuffer): Unit = {
    val (body, cleanup) = collectBodyFromParser(
      buffer,
      () => Either.left(InvalidBodyException("Received premature EOF.")))

    parser.collectMessage(body, requestAttrs()) match {
      case Right(req) =>
        executionContext.execute(new Runnable {
          def run(): Unit = {
            val action = Sync[F]
              .suspend(raceTimeout(req))
              .recoverWith(serviceErrorHandler(req))
              .flatMap(resp => F.delay(renderResponse(req, resp, cleanup)))

            parser.synchronized {
              cancelToken = Some(
                F.runCancelable(action) {
                    case Right(()) => IO.unit
                    case Left(t) =>
                      IO(logger.error(t)(s"Error running request: $req")).attempt *> IO(
                        closeConnection())
                  }
                  .unsafeRunSync())
            }
          }
        })
      case Left((e, protocol)) =>
        badMessage(e.details, new BadMessage(e.sanitized), Request[F]().withHttpVersion(protocol))
    }
  }

  protected def renderResponse(
      req: Request[F],
      resp: Response[F],
      bodyCleanup: () => Future[ByteBuffer]): Unit = {
    val rr = new StringWriter(512)
    rr << req.httpVersion << ' ' << resp.status.code << ' ' << resp.status.reason << "\r\n"

    Http1Stage.encodeHeaders(resp.headers, rr, isServer = true)

    val respTransferCoding = `Transfer-Encoding`.from(resp.headers)
    val lengthHeader = `Content-Length`.from(resp.headers)
    val respConn = Connection.from(resp.headers)

    // Need to decide which encoder and if to close on finish
    val closeOnFinish = respConn
      .map(_.hasClose)
      .orElse {
        Connection.from(req.headers).map(checkCloseConnection(_, rr))
      }
      .getOrElse(parser.minorVersion == 0) // Finally, if nobody specifies, http 1.0 defaults to close

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val bodyEncoder: Http1Writer[F] = {
      if (req.method == Method.HEAD || !resp.status.isEntityAllowed) {
        // We don't have a body (or don't want to send it) so we just get the headers

        if (!resp.status.isEntityAllowed &&
          (lengthHeader.isDefined || respTransferCoding.isDefined)) {
          logger.warn(
            s"Body detected for response code ${resp.status.code} which doesn't permit an entity. Dropping.")
        }

        if (req.method == Method.HEAD) {
          // write message body header for HEAD response
          (parser.minorVersion, respTransferCoding, lengthHeader) match {
            case (minor, Some(enc), _) if minor > 0 && enc.hasChunked =>
              rr << "Transfer-Encoding: chunked\r\n"
            case (_, _, Some(len)) => rr << len << "\r\n"
            case _ => // nop
          }
        }

        // add KeepAlive to Http 1.0 responses if the header isn't already present
        rr << (if (!closeOnFinish && parser.minorVersion == 0 && respConn.isEmpty)
                 "Connection: keep-alive\r\n\r\n"
               else "\r\n")

        new BodylessWriter[F](this, closeOnFinish)
      } else
        getEncoder(
          respConn,
          respTransferCoding,
          lengthHeader,
          resp.trailerHeaders,
          rr,
          parser.minorVersion,
          closeOnFinish)
    }

    unsafeRunAsync(bodyEncoder.write(rr, resp.body)) {
      case Right(requireClose) =>
        if (closeOnFinish || requireClose) {
          logger.trace("Request/route requested closing connection.")
          IO(closeConnection())
        } else
          IO {
            bodyCleanup().onComplete {
              case s @ Success(_) => // Serve another request
                parser.reset()
                handleReqRead(s)

              case Failure(EOF) => closeConnection()

              case Failure(t) => fatalError(t, "Failure in body cleanup")
            }(trampoline)
          }

      case Left(EOF) =>
        IO(closeConnection())

      case Left(t) =>
        logger.error(t)("Error writing body")
        IO(closeConnection())
    }
  }

  private def closeConnection(): Unit = {
    logger.debug("closeConnection()")
    stageShutdown()
    closePipeline(None)
  }

  override protected def stageShutdown(): Unit = {
    logger.debug("Shutting down HttpPipeline")
    parser.synchronized {
      cancel()
      isClosed = true
      parser.shutdownParser()
    }
    super.stageShutdown()
  }

  private def cancel(): Unit = cancelToken.foreach { token =>
    F.runAsync(token) {
        case Right(_) => IO(logger.debug("Canceled request"))
        case Left(t) => IO(logger.error(t)("Error canceling request"))
      }
      .unsafeRunSync()
  }

  final protected def badMessage(
      debugMessage: String,
      t: ParserException,
      req: Request[F]): Unit = {
    logger.debug(t)(s"Bad Request: $debugMessage")
    val resp = Response[F](Status.BadRequest)
      .withHeaders(Connection("close".ci), `Content-Length`.zero)
    renderResponse(req, resp, () => Future.successful(emptyBuffer))
  }

  // The error handler of last resort
  final protected def internalServerError(
      errorMsg: String,
      t: Throwable,
      req: Request[F],
      bodyCleanup: () => Future[ByteBuffer]): Unit = {
    logger.error(t)(errorMsg)
    val resp = Response[F](Status.InternalServerError)
      .withHeaders(Connection("close".ci), `Content-Length`.zero)
    renderResponse(req, resp, bodyCleanup) // will terminate the connection due to connection: close header
  }

  private[this] val raceTimeout: Request[F] => F[Response[F]] =
    responseHeaderTimeout match {
      case finite: FiniteDuration =>
        val timeoutResponse = timer.sleep(finite).as(Response.timeout[F])
        req =>
          F.race(runApp(req), timeoutResponse).map(_.merge)
      case _ =>
        runApp
    }
}
