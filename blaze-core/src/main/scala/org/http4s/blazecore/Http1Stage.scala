package org.http4s
package blazecore

import cats.effect.Effect
import cats.implicits._
import fs2._
import fs2.Stream._
import java.nio.ByteBuffer
import java.time.Instant
import org.http4s.blaze.http.parser.BaseExceptions.ParserException
import org.http4s.blaze.pipeline.{Command, TailStage}
import org.http4s.blaze.util.BufferTools
import org.http4s.blaze.util.BufferTools.emptyBuffer
import org.http4s.blazecore.util._
import org.http4s.headers._
import org.http4s.util.{StringWriter, Writer}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Utility bits for dealing with the HTTP 1.x protocol */
trait Http1Stage[F[_]] { self: TailStage[ByteBuffer] =>

  /** ExecutionContext to be used for all Future continuations
    * '''WARNING:''' The ExecutionContext should trampoline or risk possibly unhandled stack overflows */
  protected implicit def executionContext: ExecutionContext

  protected implicit def F: Effect[F]

  protected def chunkBufferMaxSize: Int

  protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer]

  protected def contentComplete(): Boolean

  /** Check Connection header and add applicable headers to response */
  final protected def checkCloseConnection(conn: Connection, rr: StringWriter): Boolean =
    if (conn.hasKeepAlive) { // connection, look to the request
      logger.trace("Found Keep-Alive header")
      false
    } else if (conn.hasClose) {
      logger.trace("Found Connection:Close header")
      rr << "Connection:close\r\n"
      true
    } else {
      logger.info(
        s"Unknown connection header: '${conn.value}'. Closing connection upon completion.")
      rr << "Connection:close\r\n"
      true
    }

  /** Get the proper body encoder based on the message headers */
  final protected def getEncoder(
      msg: Message[F],
      rr: StringWriter,
      minor: Int,
      closeOnFinish: Boolean): Http1Writer[F] = {
    val headers = msg.headers
    getEncoder(
      Connection.from(headers),
      `Transfer-Encoding`.from(headers),
      `Content-Length`.from(headers),
      msg.trailerHeaders,
      rr,
      minor,
      closeOnFinish)
  }

  /** Get the proper body encoder based on the message headers,
    * adding the appropriate Connection and Transfer-Encoding headers along the way */
  final protected def getEncoder(
      connectionHeader: Option[Connection],
      bodyEncoding: Option[`Transfer-Encoding`],
      lengthHeader: Option[`Content-Length`],
      trailer: F[Headers],
      rr: StringWriter,
      minor: Int,
      closeOnFinish: Boolean): Http1Writer[F] = lengthHeader match {
    case Some(h) if bodyEncoding.forall(!_.hasChunked) || minor == 0 =>
      // HTTP 1.1: we have a length and no chunked encoding
      // HTTP 1.0: we have a length

      bodyEncoding.foreach(
        enc =>
          logger.warn(
            s"Unsupported transfer encoding: '${enc.value}' for HTTP 1.$minor. Stripping header."))

      logger.trace("Using static encoder")

      rr << h << "\r\n" // write Content-Length

      // add KeepAlive to Http 1.0 responses if the header isn't already present
      rr << (if (!closeOnFinish && minor == 0 && connectionHeader.isEmpty)
               "Connection: keep-alive\r\n\r\n"
             else "\r\n")

      // FIXME: This cast to int is bad, but needs refactoring of IdentityWriter to change
      new IdentityWriter[F](h.length.toInt, this)

    case _ => // No Length designated for body or Transfer-Encoding included for HTTP 1.1
      if (minor == 0) { // we are replying to a HTTP 1.0 request see if the length is reasonable
        if (closeOnFinish) { // HTTP 1.0 uses a static encoder
          logger.trace("Using static encoder")
          rr << "\r\n"
          new IdentityWriter[F](-1, this)
        } else { // HTTP 1.0, but request was Keep-Alive.
          logger.trace("Using static encoder without length")
          new CachingStaticWriter[F](this) // will cache for a bit, then signal close if the body is long
        }
      } else
        bodyEncoding match { // HTTP >= 1.1 request without length and/or with chunked encoder
          case Some(enc) => // Signaling chunked means flush every chunk
            if (!enc.hasChunked) {
              logger.warn(
                s"Unsupported transfer encoding: '${enc.value}' for HTTP 1.$minor. Stripping header.")
            }

            if (lengthHeader.isDefined) {
              logger.warn(
                s"Both Content-Length and Transfer-Encoding headers defined. Stripping Content-Length.")
            }

            new FlushingChunkWriter(this, trailer)

          case None => // use a cached chunk encoder for HTTP/1.1 without length of transfer encoding
            logger.trace("Using Caching Chunk Encoder")
            new CachingChunkWriter(this, trailer, chunkBufferMaxSize)
        }
  }

  /** Makes a [[EntityBody]] and a function used to drain the line if terminated early.
    *
    * @param buffer starting `ByteBuffer` to use in parsing.
    * @param eofCondition If the other end hangs up, this is the condition used in the stream for termination.
    *                     The desired result will differ between Client and Server as the former can interpret
    *                     and `Command.EOF` as the end of the body while a server cannot.
    */
  final protected def collectBodyFromParser(
      buffer: ByteBuffer,
      eofCondition: () => Either[Throwable, Option[Chunk[Byte]]])
    : (EntityBody[F], () => Future[ByteBuffer]) =
    if (contentComplete()) {
      if (buffer.remaining() == 0) Http1Stage.CachedEmptyBody
      else (EmptyBody, () => Future.successful(buffer))
    }
    // try parsing the existing buffer: many requests will come as a single chunk
    else if (buffer.hasRemaining) doParseContent(buffer) match {
      case Some(buff) if contentComplete() =>
        Stream.chunk(Chunk.byteBuffer(buff)).covary[F] -> Http1Stage
          .futureBufferThunk(buffer)

      case Some(buff) =>
        val (rst, end) = streamingBody(buffer, eofCondition)
        (Stream.chunk(Chunk.byteBuffer(buff)) ++ rst, end)

      case None if contentComplete() =>
        if (buffer.hasRemaining) EmptyBody -> Http1Stage.futureBufferThunk(buffer)
        else Http1Stage.CachedEmptyBody

      case None => streamingBody(buffer, eofCondition)
    }
    // we are not finished and need more data.
    else streamingBody(buffer, eofCondition)

  // Streams the body off the wire
  private def streamingBody(
      buffer: ByteBuffer,
      eofCondition: () => Either[Throwable, Option[Chunk[Byte]]])
    : (EntityBody[F], () => Future[ByteBuffer]) = {
    @volatile var currentBuffer = buffer

    // TODO: we need to work trailers into here somehow
    val t = F.async[Option[Chunk[Byte]]] { cb =>
      if (!contentComplete()) {

        def go(): Unit =
          try {
            val parseResult = doParseContent(currentBuffer)
            logger.debug(s"Parse result: $parseResult, content complete: ${contentComplete()}")
            parseResult match {
              case Some(result) =>
                cb(Either.right(Chunk.byteBuffer(result).some))

              case None if contentComplete() =>
                cb(End)

              case None =>
                channelRead().onComplete {
                  case Success(b) =>
                    currentBuffer = BufferTools.concatBuffers(currentBuffer, b)
                    go()

                  case Failure(Command.EOF) =>
                    cb(eofCondition())

                  case Failure(t) =>
                    logger.error(t)("Unexpected error reading body.")
                    cb(Either.left(t))
                }
            }
          } catch {
            case t: ParserException =>
              fatalError(t, "Error parsing request body")
              cb(Either.left(InvalidBodyException(t.getMessage())))

            case t: Throwable =>
              fatalError(t, "Error collecting body")
              cb(Either.left(t))
          }
        go()
      } else cb(End)
    }

    (repeatEval(t).unNoneTerminate.flatMap(chunk(_).covary[F]), () => drainBody(currentBuffer))
  }

  /** Called when a fatal error has occurred
    * The method logs an error and shuts down the stage, sending the error outbound
    * @param t
    * @param msg
    */
  protected def fatalError(t: Throwable, msg: String): Unit = {
    logger.error(t)(s"Fatal Error: $msg")
    stageShutdown()
    closePipeline(Some(t))
  }

  /** Cleans out any remaining body from the parser */
  final protected def drainBody(buffer: ByteBuffer): Future[ByteBuffer] = {
    logger.trace(s"Draining body: $buffer")

    while (!contentComplete() && doParseContent(buffer).nonEmpty) { /* NOOP */ }

    if (contentComplete()) Future.successful(buffer)
    else {
      // Send the EOF to trigger a connection shutdown
      logger.info(s"HTTP body not read to completion. Dropping connection.")
      Future.failed(Command.EOF)
    }
  }
}

object Http1Stage {

  private val CachedEmptyBufferThunk = {
    val b = Future.successful(emptyBuffer)
    () =>
      b
  }

  private val CachedEmptyBody = EmptyBody -> CachedEmptyBufferThunk

  private def futureBufferThunk(buffer: ByteBuffer): () => Future[ByteBuffer] =
    if (buffer.hasRemaining) { () =>
      Future.successful(buffer)
    } else CachedEmptyBufferThunk

  /** Encodes the headers into the Writer. Does not encode `Transfer-Encoding` or
    * `Content-Length` headers, which are left for the body encoder. Adds
    * `Date` header if one is missing and this is a server response.
    *
    * Note: this method is very niche but useful for both server and client. */
  def encodeHeaders(headers: Iterable[Header], rr: Writer, isServer: Boolean): Unit = {
    var dateEncoded = false
    headers.foreach { h =>
      if (h.name != `Transfer-Encoding`.name && h.name != `Content-Length`.name) {
        if (isServer && h.name == Date.name) dateEncoded = true
        rr << h << "\r\n"
      }
    }

    if (isServer && !dateEncoded) {
      rr << Date.name << ": " << Instant.now << "\r\n"
    }
    ()
  }
}
