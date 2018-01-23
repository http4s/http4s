package org.http4s
package client
package blaze

import cats.ApplicativeError
import cats.effect._
import cats.implicits._
import fs2._
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import org.http4s.{headers => H}
import org.http4s.Uri.{Authority, RegName}
import org.http4s.blaze.pipeline.Command
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blazecore.Http1Stage
import org.http4s.blazecore.util.Http1Writer
import org.http4s.headers.{Connection, Host, `Content-Length`, `User-Agent`}
import org.http4s.util.{StringWriter, Writer}
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success}

private final class Http1Connection[F[_]](val requestKey: RequestKey, config: BlazeClientConfig)(
    implicit protected val F: Effect[F])
    extends Http1Stage[F]
    with BlazeConnection[F] {
  import org.http4s.client.blaze.Http1Connection._

  protected override val executionContext = config.executionContext

  override def name: String = getClass.getName
  private val parser =
    new BlazeHttp1ClientParser(
      config.maxResponseLineSize,
      config.maxHeaderLength,
      config.maxChunkSize,
      config.lenientParser)

  private val stageState = new AtomicReference[State](Idle)

  override def isClosed: Boolean = stageState.get match {
    case Error(_) => true
    case _ => false
  }

  override def isRecyclable: Boolean = stageState.get == Idle

  override def shutdown(): Unit = stageShutdown()

  override def stageShutdown(): Unit = shutdownWithError(EOF)

  override protected def fatalError(t: Throwable, msg: String): Unit = {
    val realErr = t match {
      case _: TimeoutException => EOF
      case EOF => EOF
      case t =>
        logger.error(t)(s"Fatal Error: $msg")
        t
    }
    shutdownWithError(realErr)
  }

  @tailrec
  private def shutdownWithError(t: Throwable): Unit = stageState.get match {
    // If we have a real error, lets put it here.
    case st @ Error(EOF) if t != EOF =>
      if (!stageState.compareAndSet(st, Error(t))) shutdownWithError(t)
      else sendOutboundCommand(Command.Error(t))

    case Error(_) => // NOOP: already shutdown

    case x =>
      if (!stageState.compareAndSet(x, Error(t))) shutdownWithError(t)
      else {
        val cmd = t match {
          case EOF => Command.Disconnect
          case _ => Command.Error(t)
        }
        sendOutboundCommand(cmd)
        super.stageShutdown()
      }
  }

  @tailrec
  def reset(): Unit =
    stageState.get() match {
      case v @ (Running | Idle) =>
        if (stageState.compareAndSet(v, Idle)) parser.reset()
        else reset()
      case Error(_) => // NOOP: we don't reset on an error.
    }

  def runRequest(req: Request[F]): F[Response[F]] = F.suspend[Response[F]] {
    stageState.get match {
      case Idle =>
        if (stageState.compareAndSet(Idle, Running)) {
          logger.debug(s"Connection was idle. Running.")
          executeRequest(req)
        } else {
          logger.debug(s"Connection changed state since checking it was idle. Looping.")
          runRequest(req)
        }
      case Running =>
        logger.error(s"Tried to run a request already in running state.")
        F.raiseError(InProgressException)
      case Error(e) =>
        logger.debug(s"Tried to run a request in closed/error state: $e")
        F.raiseError(e)
    }
  }

  override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] =
    parser.doParseContent(buffer)

  override protected def contentComplete(): Boolean = parser.contentComplete()

  private def executeRequest(req: Request[F]): F[Response[F]] = {
    logger.debug(s"Beginning request: ${req.method} ${req.uri}")
    validateRequest(req) match {
      case Left(e) => F.raiseError(e)
      case Right(req) =>
        F.suspend {
          val initWriterSize: Int = 512
          val rr: StringWriter = new StringWriter(initWriterSize)
          val isServer: Boolean = false

          // Side Effecting Code
          encodeRequestLine(req, rr)
          Http1Stage.encodeHeaders(req.headers, rr, isServer)
          if (config.userAgent.nonEmpty && req.headers.get(`User-Agent`).isEmpty) {
            rr << config.userAgent.get << "\r\n"
          }

          val mustClose: Boolean = H.Connection.from(req.headers) match {
            case Some(conn) => checkCloseConnection(conn, rr)
            case None => getHttpMinor(req) == 0
          }

          val renderTask: F[Boolean] = getChunkEncoder(req, mustClose, rr)
            .write(rr, req.body)
            .recover {
              case EOF => false
            }
            .attempt
            .flatMap { r =>
              F.delay(sendOutboundCommand(ClientTimeoutStage.RequestSendComplete)).flatMap { _ =>
                ApplicativeError[F, Throwable].fromEither(r)
              }
            }

          // If we get a pipeline closed, we might still be good. Check response
          val responseTask: F[Response[F]] =
            receiveResponse(mustClose, doesntHaveBody = req.method == Method.HEAD)

          renderTask
            .productR(responseTask)
            .handleErrorWith { t =>
              fatalError(t, "Error executing request")
              F.raiseError(t)
            }
        }
    }
  }

  private def receiveResponse(closeOnFinish: Boolean, doesntHaveBody: Boolean): F[Response[F]] =
    F.async[Response[F]](cb =>
      readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Initial Read"))

  // this method will get some data, and try to continue parsing using the implicit ec
  private def readAndParsePrelude(
      cb: Callback[Response[F]],
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      phase: String): Unit =
    channelRead().onComplete {
      case Success(buff) => parsePrelude(buff, closeOnFinish, doesntHaveBody, cb)
      case Failure(EOF) =>
        stageState.get match {
          case Idle | Running => shutdown(); cb(Left(EOF))
          case Error(e) => cb(Left(e))
        }

      case Failure(t) =>
        fatalError(t, s"Error during phase: $phase")
        cb(Left(t))
    }(config.executionContext)

  private def parsePrelude(
      buffer: ByteBuffer,
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      cb: Callback[Response[F]]): Unit =
    try {
      if (!parser.finishedResponseLine(buffer))
        readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Response Line Parsing")
      else if (!parser.finishedHeaders(buffer))
        readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Header Parsing")
      else {
        sendOutboundCommand(ClientTimeoutStage.ResponseHeaderComplete)

        // Get headers and determine if we need to close
        val headers: Headers = parser.getHeaders()
        val status: Status = parser.getStatus()
        val httpVersion: HttpVersion = parser.getHttpVersion()

        // we are now to the body
        def terminationCondition(): Either[Throwable, Option[Chunk[Byte]]] =
          stageState.get match { // if we don't have a length, EOF signals the end of the body.
            case Error(e) if e != EOF => Either.left(e)
            case _ =>
              if (parser.definedContentLength() || parser.isChunked())
                Either.left(InvalidBodyException("Received premature EOF."))
              else Either.right(None)
          }

        def cleanup(): Unit =
          if (closeOnFinish || headers.get(Connection).exists(_.hasClose)) {
            logger.debug("Message body complete. Shutting down.")
            stageShutdown()
          } else {
            logger.debug(s"Resetting $name after completing request.")
            reset()
          }

        val (attributes, body): (AttributeMap, EntityBody[F]) = if (doesntHaveBody) {
          // responses to HEAD requests do not have a body
          cleanup()
          (AttributeMap.empty, EmptyBody)
        } else {
          // We are to the point of parsing the body and then cleaning up
          val (rawBody, _): (EntityBody[F], () => Future[ByteBuffer]) =
            collectBodyFromParser(buffer, terminationCondition _)

          // to collect the trailers we need a cleanup helper and an effect in the attribute map
          val (trailerCleanup, attributes): (() => Unit, AttributeMap) = {
            if (parser.getHttpVersion().minor == 1 && parser.isChunked()) {
              val trailers = new AtomicReference(Headers.empty)

              val attrs = AttributeMap.empty.put[F[Headers]](
                Message.Keys.TrailerHeaders[F],
                F.suspend {
                  if (parser.contentComplete()) F.pure(trailers.get())
                  else
                    F.raiseError(
                      new IllegalStateException(
                        "Attempted to collect trailers before the body was complete."))
                }
              )

              (() => trailers.set(parser.getHeaders()), attrs)
            } else
              ({ () =>
                ()
              }, AttributeMap.empty)
          }

          if (parser.contentComplete()) {
            trailerCleanup()
            cleanup()
            attributes -> rawBody
          } else {
            attributes -> rawBody.onFinalize(
              Stream
                .eval_(F.shift(executionContext) *> F.delay {
                  trailerCleanup(); cleanup(); stageShutdown()
                })
                .run)
          }
        }
        cb(
          Right(
            Response[F](
              status = status,
              httpVersion = httpVersion,
              headers = headers,
              body = body,
              attributes = attributes)
          ))
      }
    } catch {
      case t: Throwable =>
        logger.error(t)("Error during client request decode loop")
        cb(Left(t))
    }

  ///////////////////////// Private helpers /////////////////////////

  /** Validates the request, attempting to fix it if possible,
    * returning an Exception if invalid, None otherwise */
  @tailrec private def validateRequest(req: Request[F]): Either[Exception, Request[F]] = {
    val minor: Int = getHttpMinor(req)

    // If we are HTTP/1.0, make sure HTTP/1.0 has no body or a Content-Length header
    if (minor == 0 && `Content-Length`.from(req.headers).isEmpty) {
      logger.warn(s"Request $req is HTTP/1.0 but lacks a length header. Transforming to HTTP/1.1")
      validateRequest(req.withHttpVersion(HttpVersion.`HTTP/1.1`))
    }
    // Ensure we have a host header for HTTP/1.1
    else if (minor == 1 && req.uri.host.isEmpty) { // this is unlikely if not impossible
      if (Host.from(req.headers).isDefined) {
        val host = Host.from(req.headers).get
        val newAuth = req.uri.authority match {
          case Some(auth) => auth.copy(host = RegName(host.host), port = host.port)
          case None => Authority(host = RegName(host.host), port = host.port)
        }
        validateRequest(req.withUri(req.uri.copy(authority = Some(newAuth))))
      } else if (`Content-Length`.from(req.headers).nonEmpty) { // translate to HTTP/1.0
        validateRequest(req.withHttpVersion(HttpVersion.`HTTP/1.0`))
      } else {
        Left(new IllegalArgumentException("Host header required for HTTP/1.1 request"))
      }
    } else if (req.uri.path == "") Right(req.withUri(req.uri.copy(path = "/")))
    else Right(req) // All appears to be well
  }

  private def getChunkEncoder(
      req: Request[F],
      closeHeader: Boolean,
      rr: StringWriter): Http1Writer[F] =
    getEncoder(req, rr, getHttpMinor(req), closeHeader)
}

private object Http1Connection {
  case object InProgressException extends Exception("Stage has request in progress")

  // ADT representing the state that the ClientStage can be in
  private sealed trait State
  private case object Idle extends State
  private case object Running extends State
  private final case class Error(exc: Throwable) extends State

  private def getHttpMinor[F[_]](req: Request[F]): Int = req.httpVersion.minor

  private def encodeRequestLine[F[_]](req: Request[F], writer: Writer): writer.type = {
    val uri = req.uri
    writer << req.method << ' ' << uri.copy(scheme = None, authority = None, fragment = None) << ' ' << req.httpVersion << "\r\n"
    if (getHttpMinor(req) == 1 && Host
        .from(req.headers)
        .isEmpty) { // need to add the host header for HTTP/1.1
      uri.host match {
        case Some(host) =>
          writer << "Host: " << host.value
          if (uri.port.isDefined) writer << ':' << uri.port.get
          writer << "\r\n"

        case None =>
          // TODO: do we want to do this by exception?
          throw new IllegalArgumentException("Request URI must have a host.")
      }
      writer
    } else writer
  }
}
