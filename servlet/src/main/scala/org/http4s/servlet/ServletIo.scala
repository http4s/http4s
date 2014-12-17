package org.http4s
package servlet

import java.util.concurrent.ExecutorService
import javax.servlet.{WriteListener, ReadListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import scodec.bits.ByteVector

import scalaz.stream.Cause
import scalaz.stream.Cause.{End, Terminated}
import scalaz.{\/-, \/, -\/}
import scalaz.concurrent.{Actor, Strategy, Task}
import scalaz.stream.Process._
import scalaz.stream.async.{boundedQueue, unboundedQueue}
import scalaz.stream.io.chunkR

trait ServletIo {
  def reader(request: HttpServletRequest): EntityBody

  def writer(response: HttpServletResponse): BodyWriter
}

class BlockingServletIo(chunkSize: Int) extends ServletIo {
  override def reader(servletRequest: HttpServletRequest): EntityBody =
    chunkR(servletRequest.getInputStream).map(_(chunkSize)).eval

  override def writer(servletResponse: HttpServletResponse): BodyWriter = { response: Response =>
    val out = servletResponse.getOutputStream
    val flush = response.isChunked
    response.body.map { chunk =>
      out.write(chunk.toArray)
      if (flush)
        servletResponse.flushBuffer()
    }.run
  }
}

class NonBlockingServletIo(chunkSize: Int, executor: ExecutorService) extends ServletIo {
  override def reader(request: HttpServletRequest): EntityBody = {
    val in = request.getInputStream

    sealed trait Protocol
    sealed trait State
    case object Init extends State
    case object DataAvailable extends Protocol with State
    case class Error(t: Throwable) extends Protocol with State
    case object AllDataRead extends Protocol with State
    case class Callback(cb: Throwable \/ ByteVector => Unit) extends Protocol with State

    var buff = new Array[Byte](chunkSize)
    def read = {
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

    var state: State = Init
    val actor = Actor[Protocol] {
      case DataAvailable =>
        state match {
          case Callback(cb) =>
            cb(\/-(read))
          case _ =>
        }
        state = DataAvailable

      case Error(t) =>
        state match {
          case Callback(cb) =>
            cb(-\/(t))
          case _ =>
        }
        state = Error(t)

      case AllDataRead =>
        state match {
          case Callback(cb) =>
            cb(-\/(Terminated(End)))
          case _ =>
        }
        state = AllDataRead

      case callback @ Callback(cb) =>
        state match {
          case DataAvailable if in.isReady =>
            cb(\/-(read))
          case Error(t) =>
            cb(-\/(t))
          case Callback(cb2) =>
            cb2(\/-(ByteVector.empty))
            state = callback
          case AllDataRead =>
            cb(-\/(Terminated(End)))
          case _ if in.isFinished =>
            cb(-\/(Terminated(End)))
          case _ =>
            state = callback
        }
    }

    val listener = new ReadListener {
      override def onDataAvailable(): Unit = actor ! DataAvailable
      override def onError(t: Throwable): Unit = actor ! Error(t)
      override def onAllDataRead(): Unit = actor ! AllDataRead
    }
    in.setReadListener(listener)

    repeatEval {
      Task.async[ByteVector] { actor ! Callback(_) }
    }
  }

  override def writer(response: HttpServletResponse): BodyWriter = {
    val out = response.getOutputStream

    sealed trait Protocol
    sealed trait State
    case object Init extends State
    case object Ready extends Protocol with State
    case class Error(t: Throwable) extends Protocol with State
    case class Callback(cb: Throwable \/ (ByteVector => Unit) => Unit) extends Protocol with State
    case object SetAutoFlush extends Protocol

    var state: State = Init
    var autoFlush = false

    def writeChunk(chunk: ByteVector): Unit = {
      if (out.isReady) {
        out.write(chunk.toArray)
        if (autoFlush && out.isReady)
          out.flush()
      }
    }

    val actor = Actor[Protocol] {
      case Ready =>
        state match {
          case Callback(cb) =>
            cb(\/-(writeChunk))
          case _ =>
        }
        state = Ready

      case Error(t) =>
        state match {
          case Callback(cb) =>
            cb(-\/(t))
          case _ =>
        }
        state = Error(t)

      case callback @ Callback(cb) =>
        state match {
          case Ready if out.isReady =>
            cb(\/-(writeChunk))
          case Error(t) =>
            cb(-\/(t))
          case Callback(cb2) =>
            cb2(\/-(writeChunk))
            state = callback
          case _ =>
            state = callback
        }

      case SetAutoFlush =>
        autoFlush = true
    }

    val listener = new WriteListener {
      override def onWritePossible(): Unit = actor ! Ready
      override def onError(t: Throwable): Unit = actor ! Error(t)
    }
    out.setWriteListener(listener)

    {
      (response: Response) =>
        if (response.isChunked)
          actor ! SetAutoFlush

        val writers = repeatEval {
          Task.async[ByteVector => Unit] { actor ! Callback(_) }
        }

        response.body.zipWith(writers)((chunk, writer) => writer(chunk)).run
    }
  }
}
