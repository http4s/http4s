package org.http4s
package server
package blaze


import org.http4s.blaze.{BodylessWriter, Http1Stage}
import org.http4s.blaze.pipeline.{Command => Cmd, TailStage}
import org.http4s.blaze.util.Execution._
import org.http4s.blaze.http.http_parser.BaseExceptions.{BadRequest, ParserException}
import org.http4s.blaze.http.http_parser.Http1ServerParser
import org.http4s.blaze.channel.SocketConnection

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.util.{Try, Success, Failure}

import org.http4s.Status.{InternalServerError}
import org.http4s.util.StringWriter
import org.http4s.util.CaseInsensitiveString._
import org.http4s.Header.{Connection, `Content-Length`}

import scalaz.concurrent.{Strategy, Task}
import scalaz.{\/-, -\/}
import java.util.concurrent.ExecutorService


class Http1ServerStage(service: HttpService,
                       conn: Option[SocketConnection],
                       pool: ExecutorService = Strategy.DefaultExecutorService)
                  extends Http1ServerParser
                  with TailStage[ByteBuffer]
                  with Http1Stage
{

  protected val ec = ExecutionContext.fromExecutorService(pool)

  val name = "Http4sServerStage"

  private val requestAttrs = conn.flatMap(_.remoteInetAddress).map{ addr =>
    AttributeMap(AttributeEntry(Request.Keys.Remote, addr))
  }.getOrElse(AttributeMap.empty)

  private var uri: String = null
  private var method: String = null
  private var minor: Int = -1
  private var major: Int = -1
  private val headers = new ListBuffer[Header]

  logger.trace(s"Http4sStage starting up")

  // TODO: Its stupid that I have to have these methods
  override protected def parserContentComplete(): Boolean = contentComplete()

  override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buffer))

  // Will act as our loop
  override def stageStartup() {
    logger.info("Starting HTTP pipeline")
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
        case t: ParserException => badMessage("Error parsing status or headers in requestLoop()", t, Request())
        case t: Throwable       => fatalError(t, "error in requestLoop()")
      }

    case Failure(Cmd.EOF) => stageShutdown()
    case Failure(t)       => fatalError(t, "Error in requestLoop()")
  }

  protected def collectMessage(body: EntityBody): Option[Request] = {
    val h = Headers(headers.result())
    headers.clear()
    val protocol = if (minor == 1) HttpVersion.`HTTP/1.1` else HttpVersion.`HTTP/1.0`
    (for {
      method <- Method.fromString(this.method)
      uri <- Uri.fromString(this.uri)
    } yield {
      Some(Request(method, uri, protocol, h, body, requestAttrs))
    }).valueOr { e =>
      badMessage(e.details, new BadRequest(e.sanitized), Request().copy(httpVersion = protocol))
      None
    }
  }

  private def runRequest(buffer: ByteBuffer): Unit = {
    val body = collectBodyFromParser(buffer)

    collectMessage(body) match {
      case Some(req) =>
        Task.fork(service(req))(pool)
          .runAsync {
          case \/-(Some(resp)) => renderResponse(req, resp)
          case \/-(None)       => ResponseBuilder.notFound(req)
          case -\/(t)    =>
            logger.error(s"Error running route: $req", t)
            val resp = ResponseBuilder(InternalServerError, "500 Internal Service Error\n" + t.getMessage)
              .run
              .withHeaders(Connection("close".ci))
            renderResponse(req, resp)   // will terminate the connection due to connection: close header
        }

      case None => // NOOP
    }
  }

  protected def renderResponse(req: Request, resp: Response) {
    val rr = new StringWriter(512)
    rr << req.httpVersion << ' ' << resp.status.code << ' ' << resp.status.reason << '\r' << '\n'

    val respTransferCoding = encodeHeaders(resp.headers, rr)    // kind of tricky method returns Option[Transfer-Encoding]
    val respConn = Connection.from(resp.headers)

    // Need to decide which encoder and if to close on finish
    val closeOnFinish = respConn.map(_.hasClose).orElse {
        Header.Connection.from(req.headers).map(checkCloseConnection(_, rr))
      }.getOrElse(minor == 0)   // Finally, if nobody specifies, http 1.0 defaults to close

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val lengthHeader = `Content-Length`.from(resp.headers)

    val bodyEncoder = {
      if (!resp.status.isEntityAllowed && lengthHeader.isEmpty && respTransferCoding.isEmpty) {
        // We don't have a body so we just get the headers

        // add KeepAlive to Http 1.0 responses if the header isn't already present
        if (!closeOnFinish && minor == 0 && respConn.isEmpty) rr << "Connection:keep-alive\r\n\r\n"
        else rr << '\r' << '\n'

        val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
        new BodylessWriter(b, this, closeOnFinish)(ec)
      }
      else getEncoder(respConn, respTransferCoding, lengthHeader, resp.trailerHeaders, rr, minor, closeOnFinish)
    }

    bodyEncoder.writeProcess(resp.body).runAsync {
      case \/-(_) =>
        if (closeOnFinish || bodyEncoder.requireClose()) {
          closeConnection()
          logger.trace("Request/route requested closing connection.")
        } else {
          reset()
          requestLoop()
        }  // Serve another connection

      case -\/(t) => logger.error("Error writing body", t)
    }
  }

  private def closeConnection() {
    logger.debug("closeConnection()")
    stageShutdown()
    sendOutboundCommand(Cmd.Disconnect)
  }

  override protected def stageShutdown(): Unit = {
    logger.info("Shutting down HttpPipeline")
    shutdownParser()
    super.stageShutdown()
  }

  /////////////////// Error handling /////////////////////////////////////////

  private def parsingError(t: ParserException, message: String) {
    logger.debug(s"Parsing error: $message", t)
    stageShutdown()
    stageShutdown()
    sendOutboundCommand(Cmd.Disconnect)
  }

  protected def badMessage(msg: String, t: ParserException, req: Request) {
    renderResponse(req, Response(Status.BadRequest).withHeaders(Connection("close".ci), `Content-Length`(0)))
    logger.debug(s"Bad Request: $msg", t)
  }

  /////////////////// Stateful methods for the HTTP parser ///////////////////
  override protected def headerComplete(name: String, value: String) = {
    logger.trace(s"Received header '$name: $value'")
    headers += Header(name, value)
    false
  }

  override protected def submitRequestLine(methodString: String,
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
