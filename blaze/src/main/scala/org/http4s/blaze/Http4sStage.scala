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
import scala.util.Success
import scala.util.Failure

import Status.{InternalServerError, NotFound}
import org.http4s.util.StringWriter
import Header.`Content-Length`
import http_parser.BaseExceptions.ParserException
import http_parser.Http1ServerParser

import scalaz.stream.Process
import Process._
import scalaz.concurrent.Task
import scalaz.{\/-, -\/}


class Http4sStage(route: HttpService) extends Http1ServerParser with TailStage[ByteBuffer] {

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
          case t: ParserException => parsingError(t, "Error parsing in requestLoop()")
          case t: Throwable       => fatalError(t, "error in requestLoop()")
        }

      case Failure(Cmd.EOF)    => stageShutdown()
      case Failure(t)          => fatalError(t, "Error in requestLoop()")
    }(trampoline)
  }

  private def runRequest(buffer: ByteBuffer): Unit = {
    val h = HeaderCollection(headers.result())
    headers.clear()

    // Do we expect a body?
    val body = collectBodyFromParser(buffer)

    val req = Request(Method.resolve(this.method),
                      Uri.fromString(this.uri),
                      if (minor == 1) ServerProtocol.`HTTP/1.1` else ServerProtocol.`HTTP/1.0`,
                      h, body)

    val result = try route(req) catch {
      case _: MatchError => NotFound(req)
      case t: Throwable =>
        logger.error(s"Error running route: $req", t)
        InternalServerError("500 Internal Service Error\n" + t.getMessage)
    }

    result.runAsync {
      case \/-(resp) => renderResponse(req, resp)
      case -\/(t)    => fatalError(t, "Error running route")
    }
  }

  private def renderResponse(req: Request, resp: Response) {
    val rr = new StringWriter(512)
    rr ~ req.protocol.value.toString ~ ' ' ~ resp.status.code ~ ' ' ~ resp.status.reason ~ '\r' ~ '\n'

    var closeOnFinish = minor == 0

    resp.headers.foreach( header => rr ~ header.name.toString ~ ": " ~ header ~ '\r' ~ '\n' )

    val connectionHeader = Header.Connection.from(req.headers)
    val lengthHeader = `Content-Length`.from(resp.headers)

    // Should we add a keep-alive header?
    connectionHeader.map { h =>
      if (h.values.head.equalsIgnoreCase("Keep-Alive")) {
        logger.trace("Found Keep-Alive header")

        // Only add keep-alive header if we are http1.1 or we have a known length
        if (minor != 0 || lengthHeader.isDefined) {
          closeOnFinish = false
          rr ~ Header.Connection.name.toString ~ ':' ~ "Keep-Alive" ~ '\r' ~ '\n'
        }

      } else if (h.values.head.equalsIgnoreCase("close")) closeOnFinish = true
      else sys.error("Unknown Connection header")
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

  private def chooseEncoder(rr: StringWriter, resp: Response, lengthHeader: Option[`Content-Length`]): ProcessWriter = {
    if (minor == 0) {        // we are replying to a HTTP 1.0 request. Only do StaticWriters
      val length = lengthHeader.map(_.length).getOrElse(-1)
      rr ~ '\r' ~ '\n'
      val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
      new StaticWriter(b, length, this)
    }
    else {                 // HTTP 1.1 request, can do chunked
      Header.`Transfer-Encoding`.from(resp.headers) match {
        case Some(h) =>
          if (h.values.head != TransferCoding.chunked) sys.error("Unknown transfer encoding")
          rr ~ '\r' ~ '\n'
          val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
          new ChunkProcessWriter(b, this)

        case None =>     // Transfer-Encoding not set
          lengthHeader match {
            case Some(c) =>
              rr ~ '\r' ~ '\n'
              val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
              new StaticWriter(b, c.length, this)

            case None =>    // Need to write the Transfer-Encoding Header and go
              rr ~ "Transfer-Encoding: chunked\r\n\r\n"
              val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
              new ChunkProcessWriter(b, this)
          }
      }
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
          case t: ParserException => parsingError(t, "Error parsing request body")
          case t: Throwable       => fatalError(t, "Error collecting body")
        }
        go()
      } else { cb(-\/(End))}
    }

    val cleanup = Task.async[Unit](cb =>
      drainBody(currentbuffer).onComplete {
        case Success(_) => cb(\/-())
        case Failure(t) => cb(-\/(t))
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

  private def fatalError(t: Throwable, msg: String = "") {
    logger.error(s"Fatal Error: $msg", t)
    stageShutdown()
    sendOutboundCommand(Cmd.Error(t))
  }

  private def parsingError(t: ParserException, message: String) {
    logger.debug(s"Parsing error: $message", t)
    stageShutdown()
    sendOutboundCommand(Cmd.Disconnect)
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
