package org.http4s
package server
package blaze


import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.Http1Stage
import org.http4s.blaze.pipeline.{Command => Cmd, TailStage}
import org.http4s.blaze.util.BodylessWriter
import org.http4s.blaze.util.Execution._
import org.http4s.blaze.util.BufferTools.emptyBuffer
import org.http4s.blaze.http.http_parser.BaseExceptions.{BadRequest, ParserException}

import org.http4s.util.StringWriter
import org.http4s.util.CaseInsensitiveString._
import org.http4s.headers.{Connection, `Content-Length`}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{Try, Success, Failure}

import scalaz.concurrent.{Strategy, Task}
import scalaz.{\/-, -\/}
import java.util.concurrent.ExecutorService


object Http1ServerStage {

  def apply(service: HttpService,
            attributes: AttributeMap = AttributeMap.empty,
            pool: ExecutorService = Strategy.DefaultExecutorService,
            enableWebSockets: Boolean = false ): Http1ServerStage = {
    if (enableWebSockets) new Http1ServerStage(service, attributes, pool) with WebSocketSupport
    else                  new Http1ServerStage(service, attributes, pool)
  }
}

class Http1ServerStage(service: HttpService,
                       requestAttrs: AttributeMap,
                       pool: ExecutorService)
                  extends Http1Stage
                  with TailStage[ByteBuffer]
{
  // micro-optimization: unwrap the service and call its .run directly
  private[this] val serviceFn = service.run
  private[this] val parser = new Http1ServerParser(logger)

  protected val ec = ExecutionContext.fromExecutorService(pool)

  val name = "Http4sServerStage"

  logger.trace(s"Http4sStage starting up")

  final override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] =
    parser.doParseContent(buffer)

  final override protected def contentComplete(): Boolean = parser.contentComplete()

  // Will act as our loop
  override def stageStartup() {
    logger.debug("Starting HTTP pipeline")
    requestLoop()
  }

  private def requestLoop(): Unit = channelRead().onComplete(reqLoopCallback)(trampoline)

  private def reqLoopCallback(buff: Try[ByteBuffer]): Unit = buff match {
    case Success(buff) =>
      logger.trace {
        buff.mark()
        val sb = new StringBuilder
        println(buff) /// ------- Only for tracing purposes!
        while(buff.hasRemaining) sb.append(buff.get().toChar)

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
      }
      catch {
        case t: BadRequest => badMessage("Error parsing status or headers in requestLoop()", t, Request())
        case t: Throwable  => internalServerError("error in requestLoop()", t, Request(), () => Future.successful(emptyBuffer))
      }

    case Failure(Cmd.EOF) => stageShutdown()
    case Failure(t)       => fatalError(t, "Error in requestLoop()")
  }

  private def runRequest(buffer: ByteBuffer): Unit = {
    val (body, cleanup) = collectBodyFromParser(buffer, () => InvalidBodyException("Received premature EOF."))

    parser.collectMessage(body, requestAttrs) match {
      case \/-(req) =>
        Task.fork(serviceFn(req))(pool)
          .runAsync {
          case \/-(resp) => renderResponse(req, resp, cleanup)
          case -\/(t)    => internalServerError(s"Error running route: $req", t, req, cleanup)
        }

      case -\/((e,protocol)) => badMessage(e.details, new BadRequest(e.sanitized), Request().copy(httpVersion = protocol))
    }
  }

  protected def renderResponse(req: Request, resp: Response, bodyCleanup: () => Future[ByteBuffer]) {
    val rr = new StringWriter(512)
    rr << req.httpVersion << ' ' << resp.status.code << ' ' << resp.status.reason << "\r\n"

    val respTransferCoding = Http1Stage.encodeHeaders(resp.headers, rr, true) // kind of tricky method returns Option[Transfer-Encoding]
    val respConn = Connection.from(resp.headers)

    // Need to decide which encoder and if to close on finish
    val closeOnFinish = respConn.map(_.hasClose).orElse {
                          Connection.from(req.headers).map(checkCloseConnection(_, rr))
                        }.getOrElse(parser.minorVersion() == 0)   // Finally, if nobody specifies, http 1.0 defaults to close

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val lengthHeader = `Content-Length`.from(resp.headers)

    val bodyEncoder = {
      if (req.method == Method.HEAD ||
            (!resp.status.isEntityAllowed && lengthHeader.isEmpty && respTransferCoding.isEmpty)) {
        // We don't have a body (or don't want to send it) so we just get the headers

        if (req.method == Method.HEAD) {
          // write the explicitly set Transfer-Encoding header
          respTransferCoding.filter(_.hasChunked).map(_ => "Transfer-Encoding: chunked\r\n" ).
            foreach(rr << _)
        }

        // add KeepAlive to Http 1.0 responses if the header isn't already present
        if (!closeOnFinish && parser.minorVersion() == 0 && respConn.isEmpty) rr << "Connection:keep-alive\r\n\r\n"
        else rr << "\r\n"

        val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.ISO_8859_1))
        new BodylessWriter(b, this, closeOnFinish)(ec)
      }
      else getEncoder(respConn, respTransferCoding, lengthHeader, resp.trailerHeaders, rr, parser.minorVersion(), closeOnFinish)
    }

    bodyEncoder.writeProcess(resp.body).runAsync {
      case \/-(requireClose) =>
        if (closeOnFinish || requireClose) {
          closeConnection()
          logger.trace("Request/route requested closing connection.")
        } else bodyCleanup().onComplete {
          case s@ Success(_) => // Serve another request
            parser.reset()
            reqLoopCallback(s)

          case Failure(EOF) => closeConnection()

          case Failure(t) => fatalError(t, "Failure in body cleanup")
        }(directec)

      case -\/(EOF) =>
        closeConnection()

      case -\/(t) =>
        logger.error(t)("Error writing body")
        closeConnection()
    }
  }

  private def closeConnection() {
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

  final protected def badMessage(debugMessage: String, t: ParserException, req: Request) {
    logger.debug(t)(s"Bad Request: $debugMessage")
    val resp = Response(Status.BadRequest).replaceAllHeaders(Connection("close".ci), `Content-Length`(0))
    renderResponse(req, resp, () => Future.successful(emptyBuffer))
  }
  
  final protected def internalServerError(errorMsg: String, t: Throwable, req: Request, bodyCleanup: () => Future[ByteBuffer]): Unit = {
    logger.error(t)(errorMsg)
    val resp = Response(Status.InternalServerError).replaceAllHeaders(Connection("close".ci), `Content-Length`(0))
    renderResponse(req, resp, bodyCleanup)  // will terminate the connection due to connection: close header
  }
}
