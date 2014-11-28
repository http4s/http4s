package org.http4s.client.blaze

import java.nio.ByteBuffer

import org.http4s._
import org.http4s.blaze.http.http_parser.Http1ClientParser
import org.http4s.blaze.pipeline.Command

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.{-\/, \/-}

abstract class Http1ClientReceiver extends Http1ClientParser with BlazeClientStage { self: Http1ClientStage =>

  private val _headers = new ListBuffer[Header]
  private var _status: Status = null
  private var _httpVersion: HttpVersion = null
  @volatile private var closed = false

  override def isClosed(): Boolean = closed

  override def shutdown(): Unit = {
    closed = true
    sendOutboundCommand(Command.Disconnect)
  }

  override protected def submitResponseLine(code: Int, reason: String,
                                            scheme: String,
                                            majorversion: Int, minorversion: Int): Unit = {
    _status = Status.fromIntAndReason(code, reason).valueOr(e => throw new ParseException(e))
    _httpVersion = {
      if (majorversion == 1 && minorversion == 1)  HttpVersion.`HTTP/1.1`
      else if (majorversion == 1 && minorversion == 0)  HttpVersion.`HTTP/1.0`
      else HttpVersion.fromVersion(majorversion, minorversion).getOrElse(HttpVersion.`HTTP/1.0`)
    }
  }

  protected def collectMessage(body: EntityBody): Response = {
    val status   = if (_status == null) Status.InternalServerError else _status
    val headers  = if (_headers.isEmpty) Headers.empty else Headers(_headers.result())
    val httpVersion = if (_httpVersion == null) HttpVersion.`HTTP/1.0` else _httpVersion // TODO Questionable default
    Response(status, httpVersion, headers, body)
  }

  override protected def headerComplete(name: String, value: String): Boolean = {
    _headers += Header(name, value)
    false
  }

  protected def receiveResponse(cb: Callback, close: Boolean): Unit =
    readAndParse(cb, close, "Initial Read")

  // this method will get some data, and try to continue parsing using the implicit ec
  private def readAndParse(cb: Callback,  closeOnFinish: Boolean, phase: String) {
    channelRead().onComplete {
      case Success(buff) => requestLoop(buff, closeOnFinish, cb)
      case Failure(t)    =>
        fatalError(t, s"Error during phase: $phase")
        cb(-\/(t))
    }
  }

  private def requestLoop(buffer: ByteBuffer, closeOnFinish: Boolean, cb: Callback): Unit = try {
    if (!responseLineComplete() && !parseResponseLine(buffer)) {
      readAndParse(cb, closeOnFinish, "Response Line Parsing")
      return
    }

    if (!headersComplete() && !parseHeaders(buffer)) {
      readAndParse(cb, closeOnFinish, "Header Parsing")
      return
    }

    val body = collectBodyFromParser(buffer).onComplete(Process.eval_(Task {
      if (closeOnFinish) {
        closed = true
        stageShutdown()
        sendOutboundCommand(Command.Disconnect)
      }
      else reset()
    }))

    // TODO: we need to detect if the other side has signaled the connection will close.
    cb(\/-(collectMessage(body)))
  } catch {
    case t: Throwable =>
      logger.error(t)("Error during client request decode loop")
      cb(-\/(t))
  }
}
