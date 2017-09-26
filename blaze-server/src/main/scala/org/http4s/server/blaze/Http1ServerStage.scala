package org.http4s
package server
package blaze

import cats.data.OptionT
import cats.effect.{Effect, IO}
import cats.implicits._
import fs2._
import java.nio.ByteBuffer
import org.http4s.blaze.http.http_parser.BaseExceptions.{BadRequest, ParserException}
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.pipeline.{TailStage, Command => Cmd}
import org.http4s.blaze.util.BufferTools.emptyBuffer
import org.http4s.blaze.util.Execution._
import org.http4s.blazecore.Http1Stage
import org.http4s.blazecore.util.{BodylessWriter, Http1Writer}
import org.http4s.headers.{Connection, `Content-Length`, `Transfer-Encoding`}
import org.http4s.syntax.string._
import org.http4s.util.StringWriter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Either, Failure, Left, Right, Success, Try}

private[blaze] object Http1ServerStage {

  def apply[F[_]: Effect](
      service: HttpService[F],
      attributes: AttributeMap,
      executionContext: ExecutionContext,
      enableWebSockets: Boolean,
      maxRequestLineLen: Int,
      maxHeadersLen: Int,
      serviceErrorHandler: ServiceErrorHandler[F]): Http1ServerStage[F] =
    if (enableWebSockets)
      new Http1ServerStage(
        service,
        attributes,
        executionContext,
        maxRequestLineLen,
        maxHeadersLen,
        serviceErrorHandler) with WebSocketSupport[F]
    else
      new Http1ServerStage(
        service,
        attributes,
        executionContext,
        maxRequestLineLen,
        maxHeadersLen,
        serviceErrorHandler)
}

private[blaze] class Http1ServerStage[F[_]](
    service: HttpService[F],
    requestAttrs: AttributeMap,
    implicit protected val executionContext: ExecutionContext,
    maxRequestLineLen: Int,
    maxHeadersLen: Int,
    serviceErrorHandler: ServiceErrorHandler[F])(implicit protected val F: Effect[F])
    extends Http1Stage[F]
    with TailStage[ByteBuffer] {

  // micro-optimization: unwrap the service and call its .run directly
  private[this] val serviceFn = service.run
  private[this] val parser = new Http1ServerParser[F](logger, maxRequestLineLen, maxHeadersLen)

  val name = "Http4sServerStage"

  logger.trace(s"Http4sStage starting up")

  final override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] =
    parser.doParseContent(buffer)

  final override protected def contentComplete(): Boolean = parser.contentComplete()

  // Will act as our loop
  override def stageStartup(): Unit = {
    logger.debug("Starting HTTP pipeline")
    requestLoop()
  }

  private def requestLoop(): Unit = channelRead().onComplete(reqLoopCallback)(trampoline)

  private def reqLoopCallback(buff: Try[ByteBuffer]): Unit = buff match {
    case Success(buff) =>
      logger.trace {
        buff.mark()
        val sb = new StringBuilder
        while (buff.hasRemaining) sb.append(buff.get().toChar)

        buff.reset()
        s"Received request\n${sb.result}"
      }

      try {
        if (!parser.requestLineComplete() && !parser.doParseRequestLine(buff)) {
          requestLoop()
          return
        }
        if (!parser.headersComplete() && !parser.doParseHeaders(buff)) {
          requestLoop()
          return
        }
        // we have enough to start the request
        runRequest(buff)
      } catch {
        case t: BadRequest =>
          badMessage("Error parsing status or headers in requestLoop()", t, Request[F]())
        case t: Throwable =>
          internalServerError(
            "error in requestLoop()",
            t,
            Request[F](),
            () => Future.successful(emptyBuffer))
      }

    case Failure(Cmd.EOF) => stageShutdown()
    case Failure(t) => fatalError(t, "Error in requestLoop()")
  }

  private def runRequest(buffer: ByteBuffer): Unit = {
    val (body, cleanup) = collectBodyFromParser(
      buffer,
      () => Either.left(InvalidBodyException("Received premature EOF.")))

    parser.collectMessage(body, requestAttrs) match {
      case Right(req) =>
        executionContext.execute(new Runnable {
          def run(): Unit =
            F.runAsync {
                try serviceFn(req)
                  .handleErrorWith(serviceErrorHandler(req).andThen(OptionT.liftF(_)))
                  .value
                catch serviceErrorHandler(req).andThen(_.map(Option.apply))
              } {
                case Right(resp) =>
                  IO(renderResponse(req, resp.getOrElse(Response.notFound), cleanup))
                case Left(t) =>
                  IO(internalServerError(s"Error running route: $req", t, req, cleanup))
              }
              .unsafeRunSync()
        })
      case Left((e, protocol)) =>
        badMessage(e.details, new BadRequest(e.sanitized), Request[F]().withHttpVersion(protocol))
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

    async.unsafeRunAsync(bodyEncoder.write(rr, resp.body)) {
      case Right(requireClose) =>
        if (closeOnFinish || requireClose) {
          logger.trace("Request/route requested closing connection.")
          IO(closeConnection())
        } else
          IO {
            bodyCleanup().onComplete {
              case s @ Success(_) => // Serve another request
                parser.reset()
                reqLoopCallback(s)

              case Failure(EOF) => closeConnection()

              case Failure(t) => fatalError(t, "Failure in body cleanup")
            }(directec)
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
    sendOutboundCommand(Cmd.Disconnect)
  }

  override protected def stageShutdown(): Unit = {
    logger.debug("Shutting down HttpPipeline")
    parser.shutdownParser()
    super.stageShutdown()
  }

  /////////////////// Error handling /////////////////////////////////////////

  final protected def badMessage(
      debugMessage: String,
      t: ParserException,
      req: Request[F]): Unit = {
    logger.debug(t)(s"Bad Request: $debugMessage")
    val resp = Response[F](Status.BadRequest)
      .replaceAllHeaders(Connection("close".ci), `Content-Length`.zero)
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
      .replaceAllHeaders(Connection("close".ci), `Content-Length`.zero)
    renderResponse(req, resp, bodyCleanup) // will terminate the connection due to connection: close header
  }
}
