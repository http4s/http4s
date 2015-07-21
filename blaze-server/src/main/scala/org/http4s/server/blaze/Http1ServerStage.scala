package org.http4s
package server
package blaze


import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.{BodylessWriter, Http1Stage}
import org.http4s.blaze.pipeline.{Command => Cmd, TailStage}
import org.http4s.blaze.util.Execution._
import org.http4s.blaze.util.BufferTools.emptyBuffer
import org.http4s.blaze.http.http_parser.BaseExceptions.{BadRequest, ParserException}
import org.http4s.blaze.http.http_parser.Http1ServerParser
import org.http4s.blaze.channel.SocketConnection

import org.http4s.util.StringWriter
import org.http4s.util.CaseInsensitiveString._
import org.http4s.headers.{Connection, `Content-Length`}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import scala.collection.mutable.ListBuffer
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
                  extends Http1ServerParser
                  with TailStage[ByteBuffer]
                  with Http1Stage
{
  // micro-optimization: unwrap the service and call its .run directly
  private[this] val serviceFn = service.run

  protected val ec = ExecutionContext.fromExecutorService(pool)

  val name = "Http4sServerStage"

  private var uri: String = null
  private var method: String = null
  private var minor: Int = -1
  private var major: Int = -1
  private val headers = new ListBuffer[Header]

  logger.trace(s"Http4sStage starting up")

  final override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buffer))

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
        if (!requestLineComplete() && !parseRequestLine(buff)) {
          requestLoop()
          return
        }
        if (!headersComplete() && !parseHeaders(buff)) {
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

  final protected def collectMessage(body: EntityBody): Option[Request] = {
    val h = Headers(headers.result())
    headers.clear()
    val protocol = if (minor == 1) HttpVersion.`HTTP/1.1` else HttpVersion.`HTTP/1.0`

    (for {
      method <- Method.fromString(this.method)
      uri <- Uri.requestTarget(this.uri)
    } yield Some(Request(method, uri, protocol, h, body, requestAttrs))
    ).valueOr { e =>
      badMessage(e.details, new BadRequest(e.sanitized), Request().copy(httpVersion = protocol))
      None
    }
  }

  private def runRequest(buffer: ByteBuffer): Unit = {
    val (body, cleanup) = collectBodyFromParser(buffer, () => InvalidBodyException("Received premature EOF."))

    collectMessage(body) match {
      case Some(req) =>
        Task.fork(serviceFn(req))(pool)
          .runAsync {
          case \/-(resp) => renderResponse(req, resp, cleanup)
          case -\/(t)    => internalServerError(s"Error running route: $req", t, req, cleanup)
        }

      case None => // NOOP, this should be handled in the collectMessage method
    }
  }

  protected def renderResponse(req: Request, resp: Response, bodyCleanup: () => Future[ByteBuffer]) {
    val rr = new StringWriter(512)
    rr << req.httpVersion << ' ' << resp.status.code << ' ' << resp.status.reason << '\r' << '\n'

    val respTransferCoding = Http1Stage.encodeHeaders(resp.headers, rr, true) // kind of tricky method returns Option[Transfer-Encoding]
    val respConn = Connection.from(resp.headers)

    // Need to decide which encoder and if to close on finish
    val closeOnFinish = respConn.map(_.hasClose).orElse {
                          Connection.from(req.headers).map(checkCloseConnection(_, rr))
                        }.getOrElse(minor == 0)   // Finally, if nobody specifies, http 1.0 defaults to close

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val lengthHeader = `Content-Length`.from(resp.headers)

    val bodyEncoder = {
      if (!resp.status.isEntityAllowed && lengthHeader.isEmpty && respTransferCoding.isEmpty) {
        // We don't have a body so we just get the headers

        // add KeepAlive to Http 1.0 responses if the header isn't already present
        if (!closeOnFinish && minor == 0 && respConn.isEmpty) rr << "Connection:keep-alive\r\n\r\n"
        else rr << '\r' << '\n'

        val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.ISO_8859_1))
        new BodylessWriter(b, this, closeOnFinish)(ec)
      }
      else getEncoder(respConn, respTransferCoding, lengthHeader, resp.trailerHeaders, rr, minor, closeOnFinish)
    }

    bodyEncoder.writeProcess(resp.body).runAsync {
      case \/-(_) =>
        if (closeOnFinish || bodyEncoder.requireClose()) {
          closeConnection()
          logger.trace("Request/route requested closing connection.")
        } else bodyCleanup().onComplete {
          case s@ Success(_) => // Serve another connection
            reset()
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
    shutdownParser()
    super.stageShutdown()
  }

  /////////////////// Error handling /////////////////////////////////////////

  final protected def badMessage(debugMessage: String, t: ParserException, req: Request) {
    logger.debug(t)(s"Bad Request: $debugMessage")
    val resp = Response(Status.BadRequest).withHeaders(Connection("close".ci), `Content-Length`(0))
    renderResponse(req, resp, () => Future.successful(emptyBuffer))
  }
  
  final protected def internalServerError(errorMsg: String, t: Throwable, req: Request, bodyCleanup: () => Future[ByteBuffer]): Unit = {
    logger.error(t)(errorMsg)
    val resp = Response(Status.InternalServerError).withHeaders(Connection("close".ci), `Content-Length`(0))
    renderResponse(req, resp, bodyCleanup)  // will terminate the connection due to connection: close header
  }

  /////////////////// Stateful methods for the HTTP parser ///////////////////
  final override protected def headerComplete(name: String, value: String) = {
    logger.trace(s"Received header '$name: $value'")
    headers += Header(name, value)
    false
  }

  final override protected def submitRequestLine(methodString: String,
                                                          uri: String,
                                                       scheme: String,
                                                 majorversion: Int,
                                                 minorversion: Int) = {
    logger.trace(s"Received request($methodString $uri $scheme/$majorversion.$minorversion)")
    this.uri = uri
    this.method = methodString
    this.major = majorversion
    this.minor = minorversion
    false
  }
}
