package org.http4s
package servlet

import java.util.concurrent.ExecutorService
import javax.servlet.{WriteListener, ReadListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import scodec.bits.ByteVector

import scalaz.stream.Cause.{End, Terminated}
import scalaz.{\/-, \/, -\/}
import scalaz.concurrent.{Strategy, Actor, Task}
import scalaz.stream.Process._
import scalaz.stream.io.chunkR

trait ServletIo {
  def reader(servletRequest: HttpServletRequest): EntityBody

  /** May install a listener on the servlet response. */
  def initWriter(servletResponse: HttpServletResponse): BodyWriter
}

class BlockingServletIo(chunkSize: Int) extends ServletIo {
  override def reader(servletRequest: HttpServletRequest): EntityBody =
    chunkR(servletRequest.getInputStream).map(_(chunkSize)).eval

  override def initWriter(servletResponse: HttpServletResponse): BodyWriter = { response: Response =>
    val out = servletResponse.getOutputStream
    val flush = response.isChunked
    response.body.map { chunk =>
      out.write(chunk.toArray)
      if (flush)
        servletResponse.flushBuffer()
    }.run
  }
}

class NonBlockingServletIo(chunkSize: Int) extends ServletIo {
  override def reader(request: HttpServletRequest): EntityBody = {
    type Callback = Throwable \/ ByteVector => Unit

    sealed trait Protocol
    case object DataAvailable extends Protocol
    case class Error(t: Throwable) extends Protocol
    case object AllDataRead extends Protocol
    case class Submit(cb: Callback) extends Protocol

    sealed trait State
    case class Blocked(cb: Callback) extends State
    case object Ready extends State
    case object Complete extends State
    case class Errored(t: Throwable) extends State

    val in = request.getInputStream

    var buff = new Array[Byte](chunkSize)
    def read() = {
      val len = in.read(buff)
      if (len == chunkSize) {
        val chunk = buff
        buff = new Array[Byte](chunkSize)
        ByteVector(chunk)
      }
      else if (len > 0) {
        val copy = new Array[Byte](len)
        System.arraycopy(buff, 0, copy, 0, len)
        ByteVector.view(copy)
      }
      else
        ByteVector.empty
    }

    var state: State = Ready
    val actor = Actor[Protocol]({
      case Submit(cb) =>
        state match {
          case Ready if in.isReady => cb(\/-(read()))
          case Ready if in.isFinished => cb(-\/(Terminated(End))); state = Complete
          case Ready => state = Blocked(cb)
          case Complete => cb(-\/(Terminated(End)))
          case Errored(t) => cb(-\/(t))
          case Blocked(_) => cb(\/-(ByteVector.empty))
        }

      case DataAvailable =>
        state match {
          case Blocked(cb) => cb(\/-(read()))
          case _ =>
        }
        state = Ready

      case Error(t) =>
        state match {
          case Blocked(cb) => cb(-\/(t))
          case _ =>
        }
        state = Errored(t)

      case AllDataRead =>
        state match {
          case Blocked(cb) => cb(-\/(Terminated(End)))
          case _ =>
        }
        state = Complete
    })(Strategy.Sequential)

    val listener = new ReadListener {
      override def onDataAvailable(): Unit = actor ! DataAvailable
      override def onError(t: Throwable): Unit = actor ! Error(t)
      override def onAllDataRead(): Unit = actor ! AllDataRead
    }
    in.setReadListener(listener)

    repeatEval {
      Task.async[ByteVector] { actor ! Submit(_) }
    }
  }

  override def initWriter(response: HttpServletResponse): BodyWriter = {
    type Callback = Throwable \/ (ByteVector => Unit) => Unit

    sealed trait Protocol
    case object WritePossible extends Protocol
    case class Error(t: Throwable) extends Protocol
    case class Submit(cb: Callback) extends Protocol
    case object SetAutoFlush extends Protocol

    sealed trait State
    case class Blocked(cb: Callback) extends State
    case object Ready extends State
    case class Errored(t: Throwable) extends State

    val out = response.getOutputStream
    var state: State = Ready
    var autoFlush = false

    def writeChunk(chunk: ByteVector): Unit = {
      if (out.isReady) {
        out.write(chunk.toArray)
        if (autoFlush && out.isReady)
          out.flush()
      }
    }

    val actor = Actor[Protocol]({
      case Submit(cb) =>
        state match {
          case Ready if out.isReady => cb(\/-(writeChunk))
          case Ready => state = Blocked(cb)
          case Blocked(_) => cb(\/-(writeChunk))
          case Errored(t) => cb(-\/(t))
        }

      case WritePossible =>
        state match {
          case Blocked(cb) => cb(\/-(writeChunk))
          case _ =>
        }
        state = Ready

      case Error(t) =>
        state match {
          case Blocked(cb) => cb(-\/(t))
          case _ =>
        }
        state = Errored(t)

      case SetAutoFlush =>
        autoFlush = true
    })(Strategy.Sequential)

    val listener = new WriteListener {
      override def onWritePossible(): Unit = actor ! WritePossible
      override def onError(t: Throwable): Unit = actor ! Error(t)
    }
    /*
     * This must be set on the container thread in Tomcat, or onWritePossible
     * will not be invoked.  This side effect needs to run between the acquisition
     * of the servletResponse and the calculation of the http4s Response.
     */
    out.setWriteListener(listener)

    val bodyWriter = { response: Response =>
      if (response.isChunked)
        actor ! SetAutoFlush
      val writers = repeatEval {
        Task.async[ByteVector => Unit] { actor ! Submit(_) }
      }
      response.body.zipWith(writers)((chunk, writer) => writer(chunk)).run
    }
    bodyWriter
  }
}
