package org.http4s.client.blaze

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.headers.{`User-Agent`, Host, `Content-Length`}
import org.http4s.{headers => H}
import org.http4s.Uri.{Authority, RegName}
import org.http4s.blaze.Http1Stage
import org.http4s.blaze.util.{Cancellable, ProcessWriter}
import org.http4s.util.{StringWriter, Writer}
import org.http4s.{Request, Response, HttpVersion}

import scala.annotation.tailrec
import scala.concurrent.{TimeoutException, ExecutionContext}
import scala.concurrent.duration._

import scalaz.concurrent.Task
import scalaz.stream.Process.halt
import scalaz.{-\/, \/, \/-}

final class Http1ClientStage(userAgent: Option[`User-Agent`], timeout: Duration)(implicit protected val ec: ExecutionContext)
                      extends Http1ClientReceiver with Http1Stage {

  import Http1ClientStage._

  protected type Callback = Throwable\/Response => Unit
  
  override def name: String = getClass.getName


  override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buffer))

  /** Generate a `Task[Response]` that will perform an HTTP 1 request on execution */
  def runRequest(req: Request): Task[Response] = Task.suspend[Response] {
    // We need to race two Tasks, one that will result in failure, one that gives the Response

    val StartupCallbackTag = -\/(null)

    if (!stageState.compareAndSet(null, StartupCallbackTag)) Task.fail(InProgressException)
    else {
      val c = ClientTickWheel.schedule(new Runnable {
        @tailrec
        override def run(): Unit = {
          stageState.get() match {  // We must still be active, and the stage hasn't reset.
            case c@ \/-(_) =>
              val ex = mkTimeoutEx(req)
              if (stageState.compareAndSet(c, -\/(ex))) {
                logger.debug(ex.getMessage)
                shutdown()
              }

            // Somehow we have beaten the registration of this callback to stage
            case StartupCallbackTag =>
              val ex = -\/(mkTimeoutEx(req))
              if (!stageState.compareAndSet(StartupCallbackTag, ex)) run()
              else { /* NOOP the registered error should be handled below */ }

            case _ => // NOOP
          }
        }
      }, timeout)

      // There should only be two options at this point: StartupTag or the timeout exception registered above
      stageState.getAndSet(\/-(c)) match {
        case StartupCallbackTag =>
          executeRequest(req)

        case -\/(e) =>
          c.cancel()
          stageShutdown()
          Task.fail(e)

        case other =>
          c.cancel()
          stageShutdown()
          Task.fail(new IllegalStateException(s"Somehow the client stage got a different result: $other"))
      }
    }
  }

  private def mkTimeoutEx(req: Request) =
    new TimeoutException(s"Client request $req timed out after $timeout")

  private def executeRequest(req: Request): Task[Response] = {
    logger.debug(s"Beginning request: $req")
    validateRequest(req) match {
      case Left(e)    => Task.fail(e)
      case Right(req) =>
        Task.async { cb =>
          try {
            val rr = new StringWriter(512)
            encodeRequestLine(req, rr)
            Http1Stage.encodeHeaders(req.headers, rr, false)

            if (userAgent.nonEmpty && req.headers.get(`User-Agent`).isEmpty) {
              rr << userAgent.get << '\r' << '\n'
            }

            val closeHeader = H.Connection.from(req.headers)
              .map(checkCloseConnection(_, rr))
              .getOrElse(getHttpMinor(req) == 0)

            val enc = getChunkEncoder(req, closeHeader, rr)

            enc.writeProcess(req.body).runAsync {
              case \/-(_)    => receiveResponse(cb, closeHeader)
              case -\/(EOF)  => cb(-\/(new ClosedChannelException())) // Body failed to write.
              case e@ -\/(_) => cb(e)
            }
          } catch { case t: Throwable =>
            logger.error(t)("Error during request submission")
            cb(-\/(t))
          }
        }
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

  private def getHttpMinor(req: Request): Int = req.httpVersion.minor

  private def getChunkEncoder(req: Request, closeHeader: Boolean, rr: StringWriter): ProcessWriter =
    getEncoder(req, rr, getHttpMinor(req), closeHeader)

  private def encodeRequestLine(req: Request, writer: Writer): writer.type = {
    val uri = req.uri
    writer << req.method << ' ' << uri.copy(scheme = None, authority = None) << ' ' << req.httpVersion << '\r' << '\n'
    if (getHttpMinor(req) == 1 && Host.from(req.headers).isEmpty) { // need to add the host header for HTTP/1.1
      uri.host match {
        case Some(host) =>
          writer << "Host: " << host.value
          if (uri.port.isDefined)  writer << ':' << uri.port.get
          writer << '\r' << '\n'

        case None =>
           // TODO: do we want to do this by exception?
          throw new IllegalArgumentException("Request URI must have a host.")
      }
      writer
    } else writer
  }
}

object Http1ClientStage {
  case object InProgressException extends Exception("Stage has request in progress")
}


