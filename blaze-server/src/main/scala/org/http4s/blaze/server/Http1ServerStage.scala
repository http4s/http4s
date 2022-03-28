/*
 * Copyright 2014 http4s.org
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

package org.http4s
package blaze
package server

import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.syntax.all._
import org.http4s.blaze.http.parser.BaseExceptions.BadMessage
import org.http4s.blaze.http.parser.BaseExceptions.ParserException
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.pipeline.{Command => Cmd}
import org.http4s.blaze.util.BufferTools
import org.http4s.blaze.util.BufferTools.emptyBuffer
import org.http4s.blaze.util.Execution._
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.Http1Stage
import org.http4s.blazecore.IdleTimeoutStage
import org.http4s.blazecore.util.BodylessWriter
import org.http4s.blazecore.util.Http1Writer
import org.http4s.headers.Connection
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Transfer-Encoding`
import org.http4s.server.ServiceErrorHandler
import org.http4s.util.StringWriter
import org.http4s.websocket.WebSocketContext
import org.typelevel.vault._

import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Either
import scala.util.Failure
import scala.util.Left
import scala.util.Right
import scala.util.Success
import scala.util.Try

private[http4s] object Http1ServerStage {
  def apply[F[_]](
      routes: HttpApp[F],
      attributes: () => Vault,
      executionContext: ExecutionContext,
      wsKey: Key[WebSocketContext[F]],
      maxRequestLineLen: Int,
      maxHeadersLen: Int,
      chunkBufferMaxSize: Int,
      serviceErrorHandler: ServiceErrorHandler[F],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      scheduler: TickWheelExecutor,
      dispatcher: Dispatcher[F],
      maxWebSocketBufferSize: Option[Int],
  )(implicit F: Async[F]): Http1ServerStage[F] =
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
      scheduler,
      dispatcher,
    ) with WebSocketSupport[F] {
      val webSocketKey = wsKey
      override protected def maxBufferSize: Option[Int] = maxWebSocketBufferSize
    }
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
    scheduler: TickWheelExecutor,
    val dispatcher: Dispatcher[F],
)(implicit protected val F: Async[F])
    extends Http1Stage[F]
    with TailStage[ByteBuffer] {
  // micro-optimization: unwrap the routes and call its .run directly
  private[this] val runApp = httpApp.run

  // protected by synchronization on `parser`
  private[this] val parser = new Http1ServerParser[F](logger, maxRequestLineLen, maxHeadersLen)
  private[this] var isClosed = false
  @volatile private[this] var cancelToken: Option[() => Future[Unit]] = None

  val name = "Http4sServerStage"

  logger.trace(s"Http4sStage starting up")

  override protected final def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] =
    parser.synchronized {
      parser.doParseContent(buffer)
    }

  override protected final def contentComplete(): Boolean =
    parser.synchronized {
      parser.contentComplete()
    }

  // Will act as our loop
  override def stageStartup(): Unit = {
    logger.debug("Starting HTTP pipeline")
    initIdleTimeout()
    requestLoop()
  }

  private def initIdleTimeout(): Unit =
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
      if (!isClosed)
        try
          if (!parser.requestLineComplete() && !parser.doParseRequestLine(buff))
            requestLoop()
          else if (!parser.headersComplete() && !parser.doParseHeaders(buff))
            requestLoop()
          else
            // we have enough to start the request
            runRequest(buff)
        catch {
          case t: BadMessage =>
            badMessage("Error parsing status or headers in requestLoop()", t, Request[F]())
          case t: Throwable =>
            internalServerError(
              "error in requestLoop()",
              t,
              Request[F](),
              () => Future.successful(emptyBuffer),
            )
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
      () => Either.left(InvalidBodyException("Received premature EOF.")),
    )

    parser.collectMessage(body, requestAttrs()) match {
      case Right(req) =>
        executionContext.execute(new Runnable {
          def run(): Unit = {
            val action = raceTimeout(req)
              .recoverWith(serviceErrorHandler(req))
              .flatMap(resp => F.delay(renderResponse(req, resp, cleanup)))
              .attempt
              .flatMap {
                case Right(_) => F.unit
                case Left(t) =>
                  F.delay(logger.error(t)(s"Error running request: $req")).attempt *>
                    F.delay { cancelToken = None } *>
                    F.delay(closeConnection())
              }

            cancelToken = Some(dispatcher.unsafeToFutureCancelable(action)._2)
          }
        })
      case Left((e, protocol)) =>
        badMessage(e.details, new BadMessage(e.sanitized), Request[F]().withHttpVersion(protocol))
    }
  }

  protected def renderResponse(
      req: Request[F],
      resp: Response[F],
      bodyCleanup: () => Future[ByteBuffer],
  ): Unit = {
    val rr = new StringWriter(512)
    rr << req.httpVersion << ' ' << resp.status << "\r\n"

    Http1Stage.encodeHeaders(resp.headers.headers, rr, isServer = true)

    val respTransferCoding = resp.headers.get[`Transfer-Encoding`]
    val lengthHeader = resp.headers.get[`Content-Length`]
    val respConn = resp.headers.get[Connection]

    // Need to decide which encoder and if to close on finish
    val closeOnFinish = respConn
      .map(_.hasClose)
      .orElse {
        req.headers.get[Connection].map(checkCloseConnection(_, rr))
      }
      .getOrElse(
        parser.minorVersion() == 0
      ) // Finally, if nobody specifies, http 1.0 defaults to close

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val bodyEncoder: Http1Writer[F] =
      if (req.method == Method.HEAD || !resp.status.isEntityAllowed) {
        // We don't have a body (or don't want to send it) so we just get the headers

        if (
          !resp.status.isEntityAllowed &&
          (lengthHeader.isDefined || respTransferCoding.isDefined)
        )
          logger.warn(
            s"Body detected for response code ${resp.status.code} which doesn't permit an entity. Dropping."
          )

        if (req.method == Method.HEAD)
          // write message body header for HEAD response
          (parser.minorVersion(), respTransferCoding, lengthHeader) match {
            case (minor, Some(enc), _) if minor > 0 && enc.hasChunked =>
              rr << "Transfer-Encoding: chunked\r\n"
            case (_, _, Some(len)) => rr << len << "\r\n"
            case _ => // nop
          }

        // add KeepAlive to Http 1.0 responses if the header isn't already present
        rr << (if (!closeOnFinish && parser.minorVersion() == 0 && respConn.isEmpty)
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
          parser.minorVersion(),
          closeOnFinish,
          false,
        )

    // TODO: pool shifting: https://github.com/http4s/http4s/blob/main/core/src/main/scala/org/http4s/internal/package.scala#L45
    val fa = bodyEncoder
      .write(rr, resp.body)
      .recover { case EOF => true }
      .attempt
      .flatMap {
        case Right(requireClose) =>
          if (closeOnFinish || requireClose) {
            logger.trace("Request/route requested closing connection.")
            F.delay(closeConnection())
          } else
            F.delay {
              bodyCleanup().onComplete {
                case s @ Success(_) => // Serve another request
                  parser.reset()
                  handleReqRead(s)

                case Failure(EOF) => closeConnection()

                case Failure(t) => fatalError(t, "Failure in body cleanup")
              }(trampoline)
            }
        case Left(t) =>
          logger.error(t)("Error writing body")
          F.delay(closeConnection())
      }

    dispatcher.unsafeRunAndForget(fa)

    ()
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

  private def cancel(): Unit =
    cancelToken.foreach(_().onComplete {
      case Success(_) =>
        ()
      case Failure(t) =>
        logger.warn(t)(s"Error canceling request. No request details are available.")
    })

  protected final def badMessage(
      debugMessage: String,
      t: ParserException,
      req: Request[F],
  ): Unit = {
    logger.debug(t)(s"Bad Request: $debugMessage")
    val resp = Response[F](Status.BadRequest)
      .withHeaders(Connection.close, `Content-Length`.zero)
    renderResponse(req, resp, () => Future.successful(emptyBuffer))
  }

  // The error handler of last resort
  protected final def internalServerError(
      errorMsg: String,
      t: Throwable,
      req: Request[F],
      bodyCleanup: () => Future[ByteBuffer],
  ): Unit = {
    logger.error(t)(errorMsg)
    val resp = Response[F](Status.InternalServerError)
      .withHeaders(Connection.close, `Content-Length`.zero)
    renderResponse(
      req,
      resp,
      bodyCleanup,
    ) // will terminate the connection due to connection: close header
  }

  private[this] val raceTimeout: Request[F] => F[Response[F]] =
    responseHeaderTimeout match {
      case finite: FiniteDuration =>
        val timeoutResponse = F.async[Response[F]] { cb =>
          F.delay {
            val cancellable =
              scheduler.schedule(() => cb(Right(Response.timeout[F])), executionContext, finite)
            Some(F.delay(cancellable.cancel()))
          }
        }
        req => F.race(runApp(req), timeoutResponse).map(_.merge)
      case _ =>
        runApp
    }
}
