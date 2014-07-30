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
import scala.util.{Try, Success, Failure}

import org.http4s.Status.{NoEntityResponseGenerator, InternalServerError, NotFound}
import org.http4s.util.StringWriter
import org.http4s.util.CaseInsensitiveString._
import org.http4s.Header.{Connection, `Content-Length`}

import scalaz.concurrent.{Strategy, Task}
import scalaz.{\/-, -\/}
import org.parboiled2.ParseError
import java.util.concurrent.ExecutorService


class Http1ServerStage(service: HttpService, conn: Option[SocketConnection])
                (implicit pool: ExecutorService = Strategy.DefaultExecutorService)
                  extends Http1ServerParser
                  with TailStage[ByteBuffer]
                  with Http1Stage
{

  protected implicit def ec = trampoline

  val name = "Http4sStage"

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

  override protected def doParseContent(buffer: ByteBuffer): ByteBuffer = parseContent(buffer)

  // Will act as our loop
  override def stageStartup() {
    logger.info("Starting HTTP pipeline")
    requestLoop()
  }

  private def requestLoop(): Unit = channelRead().onComplete(reqLoopCallback)

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

  protected def collectMessage(body: EntityBody): Request = {
    val h = Headers(headers.result())
    headers.clear()

    Uri.fromString(this.uri) match {
      case Success(uri) =>
        val method = Method.getOrElseCreate(this.method)
        val protocol = if (minor == 1) ServerProtocol.`HTTP/1.1` else ServerProtocol.`HTTP/1.0`
        Request(method, uri, protocol, h, body, requestAttrs)

      case Failure(_: ParseError) =>
        val req = Request(requestUri = Uri(Some(this.uri.ci)), headers = h)
        badMessage("Error parsing Uri", new BadRequest(s"Bad request URI: ${this.uri}"), req)
        null

      case Failure(t) =>
        fatalError(t, s"Failed to generate response during Uri parsing phase: ${this.uri}")
        null
    }
  }

  private def runRequest(buffer: ByteBuffer): Unit = {
    val body = collectBodyFromParser(buffer)
    val req = collectMessage(body)

    // if we get a non-null response, process the route. Else, error has already been dealt with.
    if (req != null) {
      val resp = Task.fork {
        try service.applyOrElse(req, NotFound(_: Request)) catch {
          case t: Throwable =>
            logger.error(s"Error running route: $req", t)
            InternalServerError("500 Internal Service Error\n" + t.getMessage)
              .withHeaders(Connection("close".ci))
        }
      }(pool)

      resp.runAsync {
        case \/-(resp) => renderResponse(req, resp)
        case -\/(t)    => fatalError(t, "Error running route")
      }
    }
  }

  protected def renderResponse(req: Request, resp: Response) {
    val rr = new StringWriter(512)
    rr ~ req.protocol.value.toString ~ ' ' ~ resp.status.code ~ ' ' ~ resp.status.reason ~ '\r' ~ '\n'

    val respTransferCoding = encodeHeaders(resp.headers, rr)    // kind of tricky method returns Option[Transfer-Encoding]
    val respConn =   Connection.from(resp.headers)

    // Need to decide which encoder and if to close on finish
    val closeOnFinish = respConn.map(_.hasClose).orElse {
        Header.Connection.from(req.headers).map(checkCloseConnection(_, rr))
      }.getOrElse(minor == 0)   // Finally, if nobody specifies, http 1.0 defaults to close

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val lengthHeader = `Content-Length`.from(resp.headers)

    val bodyEncoder = {
      if (resp.status.isInstanceOf[NoEntityResponseGenerator] && lengthHeader.isEmpty && respTransferCoding.isEmpty) {
        // We don't have a body so we just get the headers

        // add KeepAlive to Http 1.0 responses if the header isn't already present
        if (!closeOnFinish && minor == 0 && respConn.isEmpty) rr ~ "Connection:keep-alive\r\n\r\n"
        else rr ~ '\r' ~ '\n'

        val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
        new BodylessWriter(b, this, closeOnFinish)
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
