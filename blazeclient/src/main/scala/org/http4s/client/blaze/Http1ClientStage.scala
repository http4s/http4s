package org.http4s.client.blaze

import java.nio.ByteBuffer

import org.http4s.Header.{Host, `Content-Length`}
import org.http4s.ServerProtocol.HttpVersion
import org.http4s.Uri.{Authority, RegName}
import org.http4s.blaze.Http1Stage
import org.http4s.blaze.util.ProcessWriter
import org.http4s.util.{StringWriter, Writer}
import org.http4s.{Header, Request, Response, ServerProtocol}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process.halt
import scalaz.{-\/, \/, \/-}

class Http1ClientStage(protected val timeout: Duration = 60.seconds)
                      (implicit protected val ec: ExecutionContext)
                      extends Http1ClientReceiver with Http1Stage {

  protected type Callback = Throwable\/Response => Unit

  override def name: String = getClass.getName

  override protected def parserContentComplete(): Boolean = contentComplete()

  override protected def doParseContent(buffer: ByteBuffer): ByteBuffer = parseContent(buffer)

  def runRequest(req: Request): Task[Response] = {
    logger.debug(s"Beginning request: $req")
    validateRequest(req) match {
      case Left(e)    => Task.fail(e)
      case Right(req) =>
        Task.async { cb =>
          try {
            val rr = new StringWriter(512)
            encodeRequestLine(req, rr)
            encodeHeaders(req.headers, rr)

            val closeHeader = Header.Connection.from(req.headers)
              .map(checkCloseConnection(_, rr))
              .getOrElse(getHttpMinor(req) == 0)

            val enc = getChunkEncoder(req, closeHeader, rr)

            enc.writeProcess(req.body).runAsync {
              case \/-(_)    => receiveResponse(cb, closeHeader)
              case e@ -\/(t) => cb(e)
            }
          } catch { case t: Throwable =>
            logger.error("Error during request submission", t)
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
      validateRequest(req.copy(protocol = ServerProtocol.`HTTP/1.1`))
    }
      // Ensure we have a host header for HTTP/1.1
    else if (minor == 1 && req.requestUri.host.isEmpty) { // this is unlikely if not impossible
      if (Host.from(req.headers).isDefined) {
        val host = Host.from(req.headers).get
        val newAuth = req.requestUri.authority match {
          case Some(auth) => auth.copy(host = RegName(host.host), port = host.port)
          case None => Authority(host = RegName(host.host), port = host.port)
        }
        validateRequest(req.copy(requestUri = req.requestUri.copy(authority = Some(newAuth))))
      }
      else if (req.body.isHalt || `Content-Length`.from(req.headers).nonEmpty) {  // translate to HTTP/1.0
        validateRequest(req.copy(protocol = ServerProtocol.`HTTP/1.0`))
      } else {
        Left(new Exception("Host header required for HTTP/1.1 request"))
      }
    }
    else Right(req) // All appears to be well
  }

  private def getHttpMinor(req: Request): Int = req.protocol match {
    case HttpVersion(_, minor) => minor
    case p => sys.error(s"Don't know the server protocol: $p")
  }

  private def getChunkEncoder(req: Request, closeHeader: Boolean, rr: StringWriter): ProcessWriter = {
    getEncoder(req, rr, getHttpMinor(req), closeHeader)
  }

  private def encodeRequestLine(req: Request, writer: Writer): writer.type = {
    val uri = req.requestUri
    writer ~ req.requestMethod ~ ' ' ~ uri.path ~ ' ' ~ req.protocol ~ '\r' ~ '\n'
    if (getHttpMinor(req) == 1 && Host.from(req.headers).isEmpty) { // need to add the host header for HTTP/1.1
      uri.host match {
        case Some(host) =>
          writer ~ "Host: " ~ host.value
          if (uri.port.isDefined)  writer ~ ':' ~ uri.port.get
          writer ~ '\r' ~ '\n'

        case None =>
      }
      writer
    } else sys.error("Request URI must have a host.") // TODO: do we want to do this by exception?
  }
}


