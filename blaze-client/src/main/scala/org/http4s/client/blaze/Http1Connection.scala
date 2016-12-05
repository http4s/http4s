package org.http4s
package client
package blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ExecutorService, TimeoutException}
import java.util.concurrent.atomic.AtomicReference

import org.http4s.Uri.{Authority, RegName}
import org.http4s.{headers => H}
import org.http4s.blaze.Http1Stage
import org.http4s.blaze.pipeline.Command
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.util.ProcessWriter
import org.http4s.headers.{Connection, Host, `Content-Length`, `User-Agent`}
import org.http4s.util.{StringWriter, Writer}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scalaz.concurrent.Task
import scalaz.stream.Cause.{End, Terminated}
import scalaz.stream.Process
import scalaz.stream.Process.{Halt, halt}
import scalaz.{-\/, \/-}

private final class Http1Connection(val requestKey: RequestKey,
                            config: BlazeClientConfig,
                            executor: ExecutorService,
                            protected val ec: ExecutionContext)
  extends Http1Stage with BlazeConnection
{
  import org.http4s.client.blaze.Http1Connection._

  override def name: String = getClass.getName
  private val parser =
    new BlazeHttp1ClientParser(config.maxResponseLineSize, config.maxHeaderLength,
                               config.maxChunkSize, config.lenientParser)

  private val stageState = new AtomicReference[State](Idle)

  override def isClosed: Boolean = stageState.get match {
    case Error(_) => true
    case _        => false
  }

  override def isRecyclable: Boolean =
    stageState.get == Idle

  override def shutdown(): Unit = stageShutdown()

  override def stageShutdown() = shutdownWithError(EOF)

  override protected def fatalError(t: Throwable, msg: String): Unit = {
    val realErr = t match {
      case _: TimeoutException => EOF
      case EOF                 => EOF
      case t                   => 
        logger.error(t)(s"Fatal Error: $msg")
        t
    }

    shutdownWithError(realErr)
  }

  @tailrec
  private def shutdownWithError(t: Throwable): Unit = stageState.get match {
    // If we have a real error, lets put it here.
    case st@ Error(EOF) if t != EOF => 
      if (!stageState.compareAndSet(st, Error(t))) shutdownWithError(t)
      else sendOutboundCommand(Command.Error(t))

    case Error(_) => // NOOP: already shutdown

    case x => 
      if (!stageState.compareAndSet(x, Error(t))) shutdownWithError(t)
      else {
        val cmd = t match { 
          case EOF => Command.Disconnect
          case _   => Command.Error(t)
        }
        sendOutboundCommand(cmd)
        super.stageShutdown()
      }
  }

  @tailrec
  def reset(): Unit = {
    stageState.get() match {
      case v@ (Running | Idle) =>
        if (stageState.compareAndSet(v, Idle)) parser.reset()
        else reset()
      case Error(_) => // NOOP: we don't reset on an error.
    }
  }

  def runRequest(req: Request, flushPrelude: Boolean): Task[Response] = Task.suspend[Response] {
    stageState.get match {
      case Idle =>
        if (stageState.compareAndSet(Idle, Running)) {
          logger.debug(s"Connection was idle. Running.")
          executeRequest(req, flushPrelude)
        }
        else {
          logger.debug(s"Connection changed state since checking it was idle. Looping.")
          runRequest(req, flushPrelude)
        }
      case Running =>
        logger.error(s"Tried to run a request already in running state.")
        Task.fail(InProgressException)
      case Error(e) =>
        logger.debug(s"Tried to run a request in closed/error state: ${e}")
        Task.fail(e)
    }
  }

  override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] = parser.doParseContent(buffer)

  override protected def contentComplete(): Boolean = parser.contentComplete()

  private def executeRequest(req: Request, flushPrelude: Boolean): Task[Response] = {
    logger.debug(s"Beginning request: $req")
    validateRequest(req) match {
      case Left(e)    => Task.fail(e)
      case Right(req) => Task.suspend {
        val rr = new StringWriter(512)
        encodeRequestLine(req, rr)
        Http1Stage.encodeHeaders(req.headers, rr, false)

        if (config.userAgent.nonEmpty && req.headers.get(`User-Agent`).isEmpty) {
          rr << config.userAgent.get << "\r\n"
        }

        val mustClose = H.Connection.from(req.headers) match {
          case Some(conn) => checkCloseConnection(conn, rr)
          case None       => getHttpMinor(req) == 0
        }

        val next: Task[StringWriter] = 
          if (!flushPrelude) Task.now(rr)
          else Task.async[StringWriter] { cb =>
            val bb = ByteBuffer.wrap(rr.result.getBytes(StandardCharsets.ISO_8859_1))
            channelWrite(bb).onComplete {
              case Success(_)    => cb(\/-(new StringWriter))
              case Failure(EOF)  => stageState.get match {
                  case Idle | Running => shutdown(); cb(-\/(EOF))
                  case Error(e)       => cb(-\/(e))
                }

              case Failure(t)    =>
                fatalError(t, s"Error during phase: flush prelude")
                cb(-\/(t))
            }(ec)
          }

        next.flatMap{ rr =>
          val bodyTask = getChunkEncoder(req, mustClose, rr)
            .writeProcess(req.body)
            .handle { case EOF => false } // If we get a pipeline closed, we might still be good. Check response
          val respTask = receiveResponse(mustClose, doesntHaveBody = req.method == Method.HEAD)
          Task.taskInstance.mapBoth(bodyTask, respTask)((_,r) => r)
            .handleWith { case t =>
              fatalError(t, "Error executing request")
              Task.fail(t)
            }
        }
      }
    }
  }

  private def receiveResponse(closeOnFinish: Boolean, doesntHaveBody: Boolean): Task[Response] =
    Task.async[Response](cb => readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Initial Read"))

  // this method will get some data, and try to continue parsing using the implicit ec
  private def readAndParsePrelude(cb: Callback[Response], closeOnFinish: Boolean, doesntHaveBody: Boolean, phase: String): Unit = {
    channelRead().onComplete {
      case Success(buff) => parsePrelude(buff, closeOnFinish, doesntHaveBody, cb)
      case Failure(EOF)  => stageState.get match {
        case Idle | Running => shutdown(); cb(-\/(EOF))
        case Error(e)       => cb(-\/(e))
      }

      case Failure(t)    =>
        fatalError(t, s"Error during phase: $phase")
        cb(-\/(t))
    }(ec)
  }

  private def parsePrelude(buffer: ByteBuffer, closeOnFinish: Boolean, doesntHaveBody: Boolean, cb: Callback[Response]): Unit = {
    try {
      if (!parser.finishedResponseLine(buffer)) readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Response Line Parsing")
      else if (!parser.finishedHeaders(buffer)) readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Header Parsing")
      else {
        // Get headers and determine if we need to close
        val headers = parser.getHeaders()
        val status = parser.getStatus()
        val httpVersion = parser.getHttpVersion()

        // we are now to the body
        def terminationCondition() = stageState.get match {  // if we don't have a length, EOF signals the end of the body.
          case Error(e) if e != EOF => e
          case _ =>
            if (parser.definedContentLength() || parser.isChunked()) InvalidBodyException("Received premature EOF.")
            else Terminated(End)
        }

        def cleanup(): Unit = {
          if (closeOnFinish || headers.get(Connection).exists(_.hasClose)) {
            logger.debug("Message body complete. Shutting down.")
            stageShutdown()
          }
          else {
            logger.debug(s"Resetting $name after completing request.")
            reset()
          }
        }

        val (attributes, body) = if (doesntHaveBody) {
          // responses to HEAD requests do not have a body
          cleanup()
          (AttributeMap.empty, EmptyBody)
        } else {
          // We are to the point of parsing the body and then cleaning up
          val (rawBody, _) = collectBodyFromParser(buffer, terminationCondition _)

          // to collect the trailers we need a cleanup helper and a Task in the attribute map
          val (trailerCleanup, attributes) =
            if (parser.getHttpVersion().minor == 1 && parser.isChunked()) {
              val trailers = new AtomicReference(Headers.empty)

              val attrs = AttributeMap.empty.put(Message.Keys.TrailerHeaders, Task.suspend {
                if (parser.contentComplete()) Task.now(trailers.get())
                else Task.fail(new IllegalStateException("Attempted to collect trailers before the body was complete."))
              })

              ({ () => trailers.set(parser.getHeaders()) }, attrs)
            }
            else ({ () => () }, AttributeMap.empty)

          if (parser.contentComplete()) {
            trailerCleanup(); cleanup()
            attributes -> rawBody
          } else {
            attributes -> rawBody.onHalt {
              case End => Process.eval_(Task{ trailerCleanup(); cleanup(); }(executor))

              case c => Process.await(Task {
                logger.debug(c.asThrowable)("Response body halted. Closing connection.")
                stageShutdown()
              }(executor))(_ => Halt(c))
            }
          }
        }

        cb(\/-(
          Response(status = status,
            httpVersion = httpVersion,
            headers = headers,
            body = body,
            attributes = attributes)
        ))
      }
    } catch {
      case t: Throwable =>
        logger.error(t)("Error during client request decode loop")
        cb(-\/(t))
    }
  }

  ///////////////////////// Private helpers /////////////////////////

  /** Validates the request, attempting to fix it if possible,
    * returning an Exception if invalid, None otherwise */
  @tailrec private def validateRequest(req: Request): Either[Exception, Request] = {
    val minor = getHttpMinor(req)

      // If we are HTTP/1.0, make sure HTTP/1.0 has no body or a Content-Length header
    if (minor == 0 && !req.body.isHalt && `Content-Length`.from(req.headers).isEmpty) {
      logger.warn(s"Request ${req.copy(body = halt)} is HTTP/1.0 but lacks a length header. Transforming to HTTP/1.1")
      validateRequest(req.copy(httpVersion = HttpVersion.`HTTP/1.1`))
    }
      // Ensure we have a host header for HTTP/1.1
    else if (minor == 1 && req.uri.host.isEmpty) { // this is unlikely if not impossible
      if (Host.from(req.headers).isDefined) {
        val host = Host.from(req.headers).get
        val newAuth = req.uri.authority match {
          case Some(auth) => auth.copy(host = RegName(host.host), port = host.port)
          case None => Authority(host = RegName(host.host), port = host.port)
        }
        validateRequest(req.copy(uri = req.uri.copy(authority = Some(newAuth))))
      }
      else if (req.body.isHalt || `Content-Length`.from(req.headers).nonEmpty) {  // translate to HTTP/1.0
        validateRequest(req.copy(httpVersion = HttpVersion.`HTTP/1.0`))
      } else {
        Left(new IllegalArgumentException("Host header required for HTTP/1.1 request"))
      }
    }
    else if (req.uri.path == "") Right(req.copy(uri = req.uri.copy(path = "/")))
    else Right(req) // All appears to be well
  }

  private def getChunkEncoder(req: Request, closeHeader: Boolean, rr: StringWriter): ProcessWriter =
    getEncoder(req, rr, getHttpMinor(req), closeHeader)
}

private object Http1Connection {
  case object InProgressException extends Exception("Stage has request in progress")

  // ADT representing the state that the ClientStage can be in
  private sealed trait State
  private case object Idle extends State
  private case object Running extends State
  private final case class Error(exc: Throwable) extends State

  private def getHttpMinor(req: Request): Int = req.httpVersion.minor

  private def encodeRequestLine(req: Request, writer: Writer): writer.type = {
    val uri = req.uri
    writer << req.method << ' ' << uri.copy(scheme = None, authority = None) << ' ' << req.httpVersion << "\r\n"
    if (getHttpMinor(req) == 1 && Host.from(req.headers).isEmpty) { // need to add the host header for HTTP/1.1
      uri.host match {
        case Some(host) =>
          writer << "Host: " << host.value
          if (uri.port.isDefined)  writer << ':' << uri.port.get
          writer << "\r\n"

        case None =>
           // TODO: do we want to do this by exception?
          throw new IllegalArgumentException("Request URI must have a host.")
      }
      writer
    } else writer
  }
}

