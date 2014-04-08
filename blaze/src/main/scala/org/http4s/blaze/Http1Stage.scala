package org.http4s
package blaze

/**
 * @author Bryce Anderson
 *         Created on 1/10/14
 */

import pipeline.{Command => Cmd, TailStage}
import util.Execution._

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import scala.concurrent.Future
import scala.collection.mutable.ListBuffer
import scala.util.{Success, Failure}

import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.util.StringWriter
import org.http4s.util.CaseInsensitiveString._
import org.http4s.Header.{Connection, `Content-Length`}
import org.http4s.blaze.http_parser.BaseExceptions.{BadRequest, ParserException}
import http_parser.Http1ServerParser

import scalaz.stream.Process
import Process._
import scalaz.concurrent.Task
import scalaz.{\/-, -\/}
import org.parboiled2.ParseError


class Http1Stage(service: HttpService) extends Http1ServerParser with TailStage[ByteBuffer] {

  protected implicit def ec = directec

  val name = "Http4sStage"

  private var uri: String = null
  private var method: String = null
  private var minor: Int = -1
  private var major: Int = -1
  private val headers = new ListBuffer[Header]

  logger.trace(s"Http4sStage starting up")

  // Will act as our loop
  override def stageStartup() {
    logger.info("Starting HTTP pipeline")
    requestLoop()
  }

  private def requestLoop(): Unit = {
    channelRead().onComplete {
      case Success(buff) =>

        logger.trace {
          buff.mark()
          val sb = new StringBuilder
          println(buff)
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
          case t: ParserException => badRequest("Error parsing status or headers in requestLoop()", t, Request())
          case t: Throwable       => fatalError(t, "error in requestLoop()")
        }

      case Failure(Cmd.EOF) => stageShutdown()
      case Failure(t)       => fatalError(t, "Error in requestLoop()")
    }(trampoline)
  }

  private def collectRequest(body: HttpBody): Request = {
    val h = Headers(headers.result())
    headers.clear()

    Uri.fromString(this.uri) match {
      case Success(uri) =>
        Request(Method.getOrElseCreate(this.method),
          uri,
          if (minor == 1) ServerProtocol.`HTTP/1.1` else ServerProtocol.`HTTP/1.0`,
          h, body)

      case Failure(_: ParseError) =>
        val req = Request(requestUri = Uri(Some(this.uri.ci)), headers = h)
        badRequest("Error parsing Uri", new BadRequest(s"Bad request URI: ${this.uri}"), req)
        null

      case Failure(t) =>
        fatalError(t, s"Failed to generate response during Uri parsing phase: ${this.uri}")
        null
    }
  }

  private def runRequest(buffer: ByteBuffer): Unit = {
    // TODO: Do we expect a body?
    val body = collectBodyFromParser(buffer)

    val req = collectRequest(body)
    if (req != null) {
      val resp = try service.applyOrElse(req, NotFound(_: Request)) catch {
        case t: Throwable =>
          logger.error(s"Error running route: $req", t)
          InternalServerError("500 Internal Service Error\n" + t.getMessage)
      }

      resp.runAsync {
        case \/-(resp) => renderResponse(req, resp)
        case -\/(t)    => fatalError(t, "Error running route")
      }
    }
  }



  protected def renderResponse(req: Request, resp: Response) {
    val rr = new StringWriter(512)
    rr ~ req.protocol.value.toString ~ ' ' ~ resp.status.code ~ ' ' ~ resp.status.reason ~ '\r' ~ '\n'
    resp.headers.foreach( header => rr ~ header.name.toString ~ ": " ~ header ~ '\r' ~ '\n' )

    val respConn = Header.Connection.from(resp.headers)

    val lengthHeader = `Content-Length`.from(resp.headers)
    var closeOnFinish = respConn.isDefined && respConn.get.hasClose || minor == 0

    // Should we add a keep-alive header?
    if (respConn.isEmpty) Header.Connection.from(req.headers).foreach { h =>
      if (h.hasKeepAlive) {
        logger.trace("Found Keep-Alive header")

        // Only add keep-alive header if we are HTTP/1.1 or we have a known length
        if (minor != 0 || lengthHeader.isDefined) {
          closeOnFinish = false
          rr ~ Header.Connection.name.toString ~ ':' ~ "Keep-Alive" ~ '\r' ~ '\n'
        }

      } else if (h.hasClose) {
        closeOnFinish = true
        rr ~ Header.Connection.name.toString ~ ':' ~ "Close" ~ '\r' ~ '\n'

      } else {
        logger.info(s"Unknown connection header: '${h.value}'. Closing connection upon completion.")
        closeOnFinish = true
        rr ~ Header.Connection.name.toString ~ ':' ~ "Close" ~ '\r' ~ '\n'
      }
    }

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val bodyEncoder = chooseEncoder(rr, resp, lengthHeader)

    bodyEncoder.writeProcess(resp.body).runAsync {
      case \/-(_) =>
        if (closeOnFinish) {
          closeConnection()
          logger.trace("Request/route requested closing connection.")
        } else {
          reset()
          requestLoop()
        }  // Serve another connection

      case -\/(t) => logger.error("Error writing body", t)
    }

  }

  /** Decide which body encoder to use
    * If length is defined, default to a static writer, otherwise decide based on http version
    */
  private def chooseEncoder(rr: StringWriter, resp: Response, l: Option[`Content-Length`]): ProcessWriter = l match {
    case Some(h) =>
      rr ~ '\r' ~ '\n'
      val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
      new StaticWriter(b, h.length, this)

    case None =>
      if (minor == 0) {        // we are replying to a HTTP 1.0 request. Only do StaticWriters
        rr ~ '\r' ~ '\n'
        val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
        new StaticWriter(b, -1, this)
      } else {  // HTTP 1.1 request
        Header.`Transfer-Encoding`.from(resp.headers) match {
          case Some(h) =>
            if (!h.hasChunked) {
              logger.warn(s"Unknown transfer encoding: '${h.value}'. Defaulting to Chunked Encoding")
              rr ~ "Transfer-Encoding: chunked\r\n"
            }
            rr ~ '\r' ~ '\n'
  
          case None =>     // Transfer-Encoding not set, default to chunked for HTTP/1.1
            rr ~ "Transfer-Encoding: chunked\r\n\r\n"
        }
        
        val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
        new ChunkProcessWriter(b, this)
      }
  }

  // TODO: what should be the behavior for determining if we have some body coming?
  private def collectBodyFromParser(buffer: ByteBuffer): HttpBody = {
    if (contentComplete()) return HttpBody.empty

    var currentbuffer = buffer

    // TODO: we need to work trailers into here somehow
    val t = Task.async[Chunk]{ cb =>
      if (!contentComplete()) {
        def go(): Unit = try {
          val result = parseContent(currentbuffer)
          if (result != null) cb(\/-(BodyChunk(result))) // we have a chunk
          else if (contentComplete()) cb(-\/(End))
          else channelRead().onComplete {
            case Success(b) =>       // Need more data...
              currentbuffer = b
              go()
            case Failure(t) => cb(-\/(t))
          }(trampoline)
        } catch {
          case t: ParserException =>
            val req = collectRequest(halt)  // may be null, but thats ok.
            badRequest("Error parsing request body", t, req)
          case t: Throwable       => fatalError(t, "Error collecting body")
        }
        go()
      } else { cb(-\/(End))}
    }

    val cleanup = Task.async[Unit](cb =>
      drainBody(currentbuffer).onComplete {
        case Success(_) => cb(\/-())
        case Failure(t) =>
          logger.warn("Error draining body", t)
          cb(-\/(t))
      })

    await(t)(emit, cleanup = await(cleanup)(_ => halt)).repeat
  }

  private def drainBody(buffer: ByteBuffer): Future[Unit] = {
    if (!contentComplete()) {
      parseContent(buffer)
      channelRead().flatMap(drainBody)(trampoline)
    }
    else Future.successful()
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

  protected def fatalError(t: Throwable, msg: String = "") {
    logger.error(s"Fatal Error: $msg", t)
    stageShutdown()
    sendOutboundCommand(Cmd.Error(t))
  }

  private def parsingError(t: ParserException, message: String) {
    logger.debug(s"Parsing error: $message", t)
    stageShutdown()
    stageShutdown()
    sendOutboundCommand(Cmd.Disconnect)
  }

  private def badRequest(msg: String, t: ParserException, req: Request) {
    renderResponse(req, Response(Status.BadRequest).withHeaders(Connection("close"), `Content-Length`(0)))
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
