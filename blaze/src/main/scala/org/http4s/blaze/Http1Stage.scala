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
import scala.util.{Try, Success, Failure}

import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.util.StringWriter
import org.http4s.util.CaseInsensitiveString._
import org.http4s.Header.{`Transfer-Encoding`, Connection, `Content-Length`}

import http.http_parser.BaseExceptions.{BadRequest, ParserException}
import http.http_parser.Http1ServerParser

import scalaz.stream.Process
import Process._
import scalaz.concurrent.{Strategy, Task}
import scalaz.{\/-, -\/}
import org.parboiled2.ParseError
import java.util.concurrent.ExecutorService
import scodec.bits.ByteVector
import org.http4s.blaze.channel.SocketConnection


class Http1Stage(service: HttpService, conn: Option[SocketConnection])
                (implicit pool: ExecutorService = Strategy.DefaultExecutorService)
              extends Http1ServerParser with TailStage[ByteBuffer] {

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
  }

  private def collectRequest(body: HttpBody): Request = {
    val h = Headers(headers.result())
    headers.clear()

    Uri.fromString(this.uri) match {
      case Success(uri) =>
        Request(Method.getOrElseCreate(this.method),
          uri,
          if (minor == 1) ServerProtocol.`HTTP/1.1` else ServerProtocol.`HTTP/1.0`,
          h, body, requestAttrs)

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
    val body = collectBodyFromParser(buffer)
    val req = collectRequest(body)

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
    resp.headers.foreach( header => if (header.name != `Transfer-Encoding`.name) rr ~ header ~ '\r' ~ '\n' )

    val respConn =   Connection.from(resp.headers)
    val respCoding = `Transfer-Encoding`.from(resp.headers)

    // Need to decide which encoder and if to close on finish
    val closeOnFinish = respConn.map(_.hasClose) orElse
      Header.Connection.from(req.headers).map { h => // If the response doesn't designate a
      if (h.hasKeepAlive) {                          // connection, look to the request
        logger.trace("Found Keep-Alive header")
        false
      }
      else if (h.hasClose) {
        logger.trace("Found Connection:Close header")
        rr ~ "Connection:close\r\n"
        true
      }
      else {
        logger.info(s"Unknown connection header: '${h.value}'. Closing connection upon completion.")
        rr ~ "Connection:close\r\n"
        true
      }
    } getOrElse(minor == 0)   // Finally, if nobody specifies, http 1.0 defaults to close

    // choose a body encoder. Will add a Transfer-Encoding header if necessary
    val lengthHeader = `Content-Length`.from(resp.headers)

    val bodyEncoder = lengthHeader match {
      case Some(h) if respCoding.isEmpty =>
        logger.trace("Using static encoder")

        // add KeepAlive to Http 1.0 responses if the header isn't already present
        if (!closeOnFinish && minor == 0 && respConn.isEmpty) rr ~ "Connection:keep-alive\r\n\r\n"
        else rr ~ '\r' ~ '\n'

        val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
        new StaticWriter(b, h.length, this)

      case _ =>  // No Length designated for body or Transfer-Encoding included
        if (minor == 0) { // we are replying to a HTTP 1.0 request see if the length is reasonable
          if (closeOnFinish) {  // HTTP 1.0 uses a static encoder
            logger.trace("Using static encoder")
            rr ~ '\r' ~ '\n'
            val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
            new StaticWriter(b, -1, this)
          }
          else {  // HTTP 1.0, but request was Keep-Alive.
            logger.trace("Using static encoder without length")
            new CachingStaticWriter(rr, this) // will cache for a bit, then signal close if the body is long
          }
        }
        else {
          rr ~ "Transfer-Encoding: chunked\r\n\r\n"
          val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
          val trailer = resp.trailerHeaders

          respCoding match { // HTTP >= 1.1 request without length. Will use a chunked encoder
            case Some(h) => // Signaling chunked may mean flush every chunk
              if (!h.hasChunked) logger.warn(s"Unknown transfer encoding: '${h.value}'. Defaulting to Chunked Encoding")
              new ChunkProcessWriter(b, this, trailer)

            case None =>     // use a cached chunk encoder for HTTP/1.1 without length of transfer encoding
              logger.trace("Using Caching Chunk Encoder")
              new CachingChunkWriter(b, this, trailer)
          }
        }
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

  // TODO: what should be the behavior for determining if we have some body coming?
  private def collectBodyFromParser(buffer: ByteBuffer): HttpBody = {
    if (contentComplete()) return HttpBody.empty

    @volatile var currentbuffer = buffer

    // TODO: we need to work trailers into here somehow
    val t = Task.async[ByteVector]{ cb =>
      if (!contentComplete()) {

        def go(): Unit = try {
          val result = parseContent(currentbuffer)
          if (result != null) cb(\/-(ByteVector(result))) // we have a chunk
          else if (contentComplete()) cb(-\/(End))
          else channelRead().onComplete {
            case Success(b) =>       // Need more data...
              currentbuffer = b
              go()
            case Failure(t) => cb(-\/(t))
          }
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
        case Success(_) => cb(\/-(()))
        case Failure(t) =>
          logger.warn("Error draining body", t)
          cb(-\/(t))
      }(directec))

    repeatEval(t).onComplete(await(cleanup)(_ => halt))
  }

  private def drainBody(buffer: ByteBuffer): Future[Unit] = {
    if (!contentComplete()) {
      parseContent(buffer)
      channelRead().flatMap(drainBody)
    }
    else Future.successful(())
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
