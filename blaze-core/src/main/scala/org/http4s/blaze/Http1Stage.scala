package org.http4s
package blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.http4s.headers.`Transfer-Encoding`
import org.http4s.{headers => H}
import org.http4s.blaze.util.BufferTools.{concatBuffers, emptyBuffer}
import org.http4s.blaze.http.http_parser.BaseExceptions.ParserException
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.pipeline.{Command, TailStage}
import org.http4s.blaze.util._
import org.http4s.util.{Writer, StringWriter}
import scodec.bits.ByteVector

import scala.concurrent.{Future, ExecutionContext, Promise}
import scala.util.{Failure, Success}

import scalaz.stream.Process._
import scalaz.stream.Cause.{Terminated, End}
import scalaz.{-\/, \/-}
import scalaz.concurrent.Task

trait Http1Stage { self: TailStage[ByteBuffer] =>

  /** ExecutionContext to be used for all Future continuations
    * '''WARNING:''' The ExecutionContext should trampoline or risk possibly unhandled stack overflows */
  protected implicit def ec: ExecutionContext

  protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer]

  protected def contentComplete(): Boolean

  /** Check Connection header and add applicable headers to response */
  final protected def checkCloseConnection(conn: H.Connection, rr: StringWriter): Boolean = {
    if (conn.hasKeepAlive) {                          // connection, look to the request
      logger.trace("Found Keep-Alive header")
      false
    }
    else if (conn.hasClose) {
      logger.trace("Found Connection:Close header")
      rr << "Connection:close\r\n"
      true
    }
    else {
      logger.info(s"Unknown connection header: '${conn.value}'. Closing connection upon completion.")
      rr << "Connection:close\r\n"
      true
    }
  }

  /** Get the proper body encoder based on the message headers */
  final protected def getEncoder(msg: Message,
                                 rr: StringWriter,
                                 minor: Int,
                                 closeOnFinish: Boolean): ProcessWriter = {
    val headers = msg.headers
    getEncoder(H.Connection.from(headers),
               H.`Transfer-Encoding`.from(headers),
               H.`Content-Length`.from(headers),
               msg.trailerHeaders,
               rr,
               minor,
               closeOnFinish)
  }

  /** Get the proper body encoder based on the message headers,
    * adding the appropriate Connection and Transfer-Encoding headers along the way */
  final protected def getEncoder(connectionHeader: Option[H.Connection],
                                     bodyEncoding: Option[H.`Transfer-Encoding`],
                                     lengthHeader: Option[H.`Content-Length`],
                                          trailer: Task[Headers],
                                               rr: StringWriter,
                                            minor: Int,
                                    closeOnFinish: Boolean): ProcessWriter = lengthHeader match {
    case Some(h) if bodyEncoding.isEmpty =>
      logger.trace("Using static encoder")

      // add KeepAlive to Http 1.0 responses if the header isn't already present
      if (!closeOnFinish && minor == 0 && connectionHeader.isEmpty) rr << "Connection:keep-alive\r\n\r\n"
      else rr << '\r' << '\n'

      val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.ISO_8859_1))
      new IdentityWriter(b, h.length, this)

    case _ =>  // No Length designated for body or Transfer-Encoding included
      if (minor == 0) { // we are replying to a HTTP 1.0 request see if the length is reasonable
        if (closeOnFinish) {  // HTTP 1.0 uses a static encoder
          logger.trace("Using static encoder")
          rr << '\r' << '\n'
          val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.ISO_8859_1))
          new IdentityWriter(b, -1, this)
        }
        else {  // HTTP 1.0, but request was Keep-Alive.
          logger.trace("Using static encoder without length")
          new CachingStaticWriter(rr, this) // will cache for a bit, then signal close if the body is long
        }
      }
      else bodyEncoding match { // HTTP >= 1.1 request without length. Will use a chunked encoder
        case Some(enc) => // Signaling chunked means flush every chunk
          if (enc.hasChunked) new ChunkProcessWriter(rr, this, trailer)
          else  {   // going to do identity
            if (enc.hasIdentity) {
              rr << "Transfer-Encoding: identity\r\n"
            } else {
              logger.warn(s"Unknown transfer encoding: '${enc.value}'. Defaulting to Identity Encoding and stripping header")
            }
            rr << '\r' << '\n'
            val b = ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.ISO_8859_1))
            new IdentityWriter(b, -1, this)
          }

        case None =>     // use a cached chunk encoder for HTTP/1.1 without length of transfer encoding
          logger.trace("Using Caching Chunk Encoder")
          new CachingChunkWriter(rr, this, trailer)
      }
  }

  /** Makes a `Process[Task, ByteVector]` and a function used to drain the line if terminated early.
    *
    * @param buffer starting `ByteBuffer` to use in parsing.
    * @param eofCondition If the other end hangs up, this is the condition used in the Process for termination.
    *                     The desired result will differ between Client and Server as the former can interpret
    *                     and [[EOF]] as the end of the body while a server cannot.
    */
  final protected def collectBodyFromParser(buffer: ByteBuffer, eofCondition:() => Throwable): (EntityBody, () => Future[ByteBuffer]) = {
    if (contentComplete()) {
      if (buffer.remaining() == 0) Http1Stage.CachedEmptyBody
      else (EmptyBody, () => Future.successful(buffer))
    }
    else {
      @volatile var currentBuffer = buffer

      // TODO: we need to work trailers into here somehow
      val t = Task.async[ByteVector]{ cb =>
        if (!contentComplete()) {

          def go(): Unit = try {
            val parseResult = doParseContent(currentBuffer)
            logger.trace(s"ParseResult: $parseResult, content complete: ${contentComplete()}")
            parseResult match {
              case Some(result) =>
                cb(\/-(ByteVector(result)))

              case None if contentComplete() =>
                cb(-\/(Terminated(End)))

              case None =>
                channelRead().onComplete {
                  case Success(b)   =>
                    currentBuffer = BufferTools.concatBuffers(currentBuffer, b)
                    go()

                  case Failure(EOF) =>
                    cb(-\/(eofCondition()))

                  case Failure(t)   =>
                    logger.error(t)("Unexpected error reading body.")
                    cb(-\/(t))
                }
            }
          } catch {
            case t: ParserException =>
              fatalError(t, "Error parsing request body")
              cb(-\/(InvalidBodyException(t.getMessage())))

            case t: Throwable =>
              fatalError(t, "Error collecting body")
              cb(-\/(t))
          }
          go()
        }
        else cb(-\/(Terminated(End)))
      }

      (repeatEval(t).onHalt(_.asHalt), () => drainBody(currentBuffer))
    }
  }

  /** Called when a fatal error has occurred
    * The method logs an error and shuts down the stage, sending the error outbound
    * @param t
    * @param msg
    */
  protected def fatalError(t: Throwable, msg: String) {
    logger.error(t)(s"Fatal Error: $msg")
    stageShutdown()
    sendOutboundCommand(Command.Error(t))
  }

  /** Cleans out any remaining body from the parser */
  final protected def drainBody(buffer: ByteBuffer): Future[ByteBuffer] = {
    logger.trace(s"Draining body: $buffer")

    def drainBody(buffer: ByteBuffer, p: Promise[ByteBuffer]): Unit = {
      try {
        if (!contentComplete()) {
          while(!contentComplete() && doParseContent(buffer).nonEmpty) { } // we just discard the results

          if (!contentComplete()) {
            logger.trace("drainBody needs more data.")
            channelRead().onComplete {
              case Success(newBuffer) =>
                logger.trace(s"Drain buffer received: $newBuffer")
                drainBody(concatBuffers(buffer, newBuffer), p)

              case Failure(t) => p.tryFailure(t)
            }(Execution.trampoline)
          }
          else p.trySuccess(buffer)
        }
        else {
          logger.trace("Body drained.")
          p.trySuccess(buffer)
        }
      } catch { case t: Throwable => p.tryFailure(t) }
    }

    if (!contentComplete()) {
      val p = Promise[ByteBuffer]
      drainBody(buffer, p)
      p.future
    }
    else {
      logger.trace("No body to drain.")
      Future.successful(buffer)
    }
  }
}

object Http1Stage {
  val CachedEmptyBody = {
    val f = Future.successful(emptyBuffer)
    (EmptyBody, () => f)
  }

  /** Encodes the headers into the Writer, except the Transfer-Encoding header which may be returned
    * Note: this method is very niche but useful for both server and client. */
  def encodeHeaders(headers: Headers, rr: Writer, isServer: Boolean): Option[`Transfer-Encoding`] = {
    var encoding: Option[`Transfer-Encoding`] = None
    var dateEncoded = false
    headers.foreach { header =>
      if (isServer && header.name == H.Date.name) dateEncoded = true

      if (header.name != `Transfer-Encoding`.name) rr << header << '\r' << '\n'
      else encoding = `Transfer-Encoding`.matchHeader(header)
    }

    if (isServer && !dateEncoded) {
      rr << H.Date.name << ':' << ' '; DateTime.now.renderRfc1123DateTimeString(rr) << '\r' << '\n'
    }

    encoding
  }
}
