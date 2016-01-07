package org.http4s
package server
package blaze

import java.util.Locale

import org.http4s.Header.Raw
import org.http4s.Status._
import org.http4s.blaze.http.Headers
import org.http4s.blaze.http.http20.{Http2StageTools, Http2Exception, NodeMsg}

import org.http4s.{Method => HMethod, Headers => HHeaders, _}
import org.http4s.blaze.pipeline.{ Command => Cmd }
import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.util.Http2Writer
import Http2Exception.{ PROTOCOL_ERROR, INTERNAL_ERROR }

import scodec.bits.ByteVector

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Cause.{Terminated, End}
import scalaz.{\/-, -\/}

import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.{Success, Failure}

import org.http4s.util.CaseInsensitiveString._

class Http2NodeStage(streamId: Int,
                      timeout: Duration,
                           ec: ExecutionContext,
                   attributes: AttributeMap,
                      service: HttpService) extends TailStage[NodeMsg.Http2Msg]
{

  import Http2StageTools._
  import NodeMsg.{ DataFrame, HeadersFrame }

  private implicit def _ec = ec   // for all the onComplete calls

  override def name = "Http2NodeStage"

  override protected def stageStartup(): Unit = {
    super.stageStartup()
    readHeaders()
  }

  private def shutdownWithCommand(cmd: Cmd.OutboundCommand): Unit = {
    stageShutdown()
    sendOutboundCommand(cmd)
  }

  private def readHeaders(): Unit = {
    channelRead(timeout = timeout).onComplete  {
      case Success(HeadersFrame(_, endStream, hs)) =>
        checkAndRunRequest(hs, endStream)

      case Success(frame) =>
        val e = PROTOCOL_ERROR(s"Received invalid frame: $frame", streamId, fatal = true)
        shutdownWithCommand(Cmd.Error(e))

      case Failure(Cmd.EOF) => shutdownWithCommand(Cmd.Disconnect)

      case Failure(t) =>
        logger.error(t)("Unknown error in readHeaders")
        val e = INTERNAL_ERROR(s"Unknown error", streamId, fatal = true)
        shutdownWithCommand(Cmd.Error(e))
    }
  }

  /** collect the body: a maxlen < 0 is interpreted as undefined */
  private def getBody(maxlen: Long): EntityBody = {
    var complete = false
    var bytesRead = 0L

    val t = Task.async[ByteVector] { cb =>
      if (complete) cb(-\/(Terminated(End)))
      else channelRead(timeout = timeout).onComplete {
        case Success(DataFrame(last, bytes)) =>
          complete = last
          bytesRead += bytes.remaining()

          // Check length: invalid length is a stream error of type PROTOCOL_ERROR
          // https://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-8.1.2  -> 8.2.1.6
          if (complete && maxlen > 0 && bytesRead != maxlen) {
            val msg = s"Entity too small. Expected $maxlen, received $bytesRead"
            val e = PROTOCOL_ERROR(msg, fatal = false)
            sendOutboundCommand(Cmd.Error(e))
            cb(-\/(InvalidBodyException(msg)))
          }
          else if (maxlen > 0 && bytesRead > maxlen) {
            val msg = s"Entity too large. Exepected $maxlen, received bytesRead"
            val e = PROTOCOL_ERROR(msg, fatal = false)
            sendOutboundCommand((Cmd.Error(e)))
            cb(-\/(InvalidBodyException(msg)))
          }
          else cb(\/-(ByteVector.view(bytes)))

        case Success(HeadersFrame(_, true, ts)) =>
          logger.warn("Discarding trailers: " + ts)
          cb(\/-(ByteVector.empty))

        case Success(other) =>  // This should cover it
          val msg = "Received invalid frame while accumulating body: " + other
          logger.info(msg)
          val e = PROTOCOL_ERROR(msg, fatal = true)
          shutdownWithCommand(Cmd.Error(e))
          cb(-\/(InvalidBodyException(msg)))

        case Failure(Cmd.EOF) =>
          logger.debug("EOF while accumulating body")
          cb(-\/(InvalidBodyException("Received premature EOF.")))
          shutdownWithCommand(Cmd.Disconnect)

        case Failure(t) =>
          logger.error(t)("Error in getBody().")
          val e = INTERNAL_ERROR(streamId, fatal = true)
          cb(-\/(e))
          shutdownWithCommand(Cmd.Error(e))
      }
    }

    Process.repeatEval(t).onHalt(_.asHalt)
  }

  private def checkAndRunRequest(hs: Headers, endStream: Boolean): Unit = {

    val headers = new ListBuffer[Header]
    var method: HMethod = null
    var scheme: String = null
    var path: Uri = null
    var contentLength: Long = -1
    var error: String = ""
    var pseudoDone = false

    hs.foreach {
      case (Method, v)    =>
        if (pseudoDone) error += "Pseudo header in invalid position. "
        else if (method == null) org.http4s.Method.fromString(v) match {
          case \/-(m) => method = m
          case -\/(e) => error = s"$error Invalid method: $e "
        }

        else error += "Multiple ':method' headers defined. "

      case (Scheme, v)    =>
        if (pseudoDone) error += "Pseudo header in invalid position. "
        else if (scheme == null) scheme = v
        else error += "Multiple ':scheme' headers defined. "

      case (Path, v)      =>
        if (pseudoDone) error += "Pseudo header in invalid position. "
        else if (path == null) Uri.requestTarget(v) match {
          case \/-(p) => path = p
          case -\/(e) => error = s"$error Invalid path: $e"
        }
        else error += "Multiple ':path' headers defined. "

      case (Authority, _) => // NOOP; TODO: we should keep the authority header
        if (pseudoDone) error += "Pseudo header in invalid position. "

      case h@(k, _) if k.startsWith(":")   => error += s"Invalid pseudo header: $h. "
      case h@(k, _) if !validHeaderName(k) => error += s"Invalid header key: $k. "

      case hs =>    // Non pseudo headers
        pseudoDone = true
        hs match {
          case h@(Connection, _) => error += s"HTTP/2.0 forbids connection specific headers: $h. "

          case (ContentLength, v) =>
            if (contentLength < 0) try {
              val sz = java.lang.Long.parseLong(v)
              if (sz != 0 && endStream) error += s"Nonzero content length ($sz) for end of stream."
              else if (sz < 0)          error += s"Negative content length: $sz"
              else contentLength = sz
            }
            catch { case t: NumberFormatException => error += s"Invalid content-length: $v. " }

            else error += "Received multiple content-length headers"

          case h@(TE, v) =>
            if (!v.equalsIgnoreCase("trailers")) error += s"HTTP/2.0 forbids TE header values other than 'trailers'. "
          // ignore otherwise

          case (k,v) => headers += Raw(k.ci, v)
      }
    }

    if (method == null || scheme == null || path == null) {
      error += s"Invalid request: missing pseudo headers. Method: $method, Scheme: $scheme, path: $path. "
    }

    if (error.length() > 0) shutdownWithCommand(Cmd.Error(PROTOCOL_ERROR(error, fatal = false)))
    else {
      val body = if (endStream) EmptyBody else getBody(contentLength)
      val hs = HHeaders(headers.result())
      val req = Request(method, path, HttpVersion.`HTTP/2.0`, hs, body, attributes)

      Task.fork(service(req)).runAsync {
        case \/-(resp) => renderResponse(req, resp)
        case -\/(t) =>
          val resp = Response(InternalServerError)
                       .withBody("500 Internal Service Error\n" + t.getMessage)
                       .run

          renderResponse(req, resp)
      }
    }
  }

  private def renderResponse(req: Request, resp: Response): Unit = {
    val hs = new ArrayBuffer[(String, String)](16)
    hs += ((Status, Integer.toString(resp.status.code)))
    resp.headers.foreach{ h => hs += ((h.name.value.toLowerCase(Locale.ROOT), h.value)) }
    
    new Http2Writer(this, hs, ec).writeProcess(resp.body).runAsync {
      case \/-(_)       => shutdownWithCommand(Cmd.Disconnect)
      case -\/(Cmd.EOF) => stageShutdown()
      case -\/(t)       => shutdownWithCommand(Cmd.Error(t))
    }    
  }
}
