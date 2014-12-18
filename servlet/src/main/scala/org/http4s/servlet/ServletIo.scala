package org.http4s
package servlet

import java.util.concurrent.atomic.AtomicReference
import javax.servlet.{WriteListener, ReadListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import scodec.bits.ByteVector

import scalaz.stream.Cause.{End, Terminated}
import scalaz.{\/, -\/}
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.stream.io.chunkR
import scalaz.syntax.either._

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
  private[this] val LeftEnd = Terminated(End).left

  override def reader(servletRequest: HttpServletRequest): EntityBody = {
    type Callback = Throwable \/ ByteVector => Unit

    sealed trait State
    case object Ready extends State
    case object Complete extends State
    case class Errored(t: Throwable) extends State
    case class Blocked(cb: Callback) extends State

    val in = servletRequest.getInputStream

    def read = {
      val buff = new Array[Byte](chunkSize)
      val len = in.read(buff)
      if (len == chunkSize) {
        ByteVector.view(buff)
      }
      else {
        ByteVector.viewI(buff(_), len)
      }
    }.right

    val state = new AtomicReference[State](Ready)

    val listener = new ReadListener {
      override def onDataAvailable(): Unit =
        state.getAndSet(Ready) match {
          case Blocked(cb) => cb(read)
          case _ =>
        }

      override def onError(t: Throwable): Unit =
        state.getAndSet(Errored(t)) match {
          case Blocked(cb) => cb(t.left)
          case _ =>
        }

      override def onAllDataRead(): Unit =
        state.getAndSet(Complete) match {
          case Blocked(cb) => cb(LeftEnd)
          case _ =>
        }
    }
    in.setReadListener(listener)

    if (in.isFinished)
      halt
    else repeatEval {
      Task.async[ByteVector] { cb =>
        val blocked = Blocked(cb)
        state.getAndSet(blocked) match {
          case Ready if in.isReady =>
            if (state.compareAndSet(blocked, Ready))
              cb(read)
          case Complete =>
            if (state.compareAndSet(blocked, Complete))
              cb(LeftEnd)
          case e @ Errored(t) =>
            if (state.compareAndSet(blocked, e))
              cb(t.left)
          case _ =>
            state.set(Blocked(cb))
        }
      }
    }
  }

  override def initWriter(servletResponse: HttpServletResponse): BodyWriter = {
    type Callback = Throwable \/ (ByteVector => Unit) => Unit

    sealed trait State
    case object Init extends State
    case object Ready extends State
    case class Errored(t: Throwable) extends State
    case class Blocked(cb: Callback) extends State

    val out = servletResponse.getOutputStream
    /*
     * If onWritePossible isn't called at least once, Tomcat begins to throw
     * NullPointerExceptions from NioEndpoint$SocketProcessor.doRun under
     * load.  The Init state means we block callbacks until the WriteListener
     * fires.
     */
    val state = new AtomicReference[State](Init)
    @volatile var autoFlush = false

    val writeChunk = { chunk: ByteVector =>
      if (out.isReady) {
        out.write(chunk.toArray)
        if (autoFlush && out.isReady)
          out.flush()
      }
    }.right

    val listener = new WriteListener {
      override def onWritePossible(): Unit = {
        state.getAndSet(Ready) match {
          case Blocked(cb) => cb(writeChunk)
          case old =>
        }
      }

      override def onError(t: Throwable): Unit =  {
        state.getAndSet(Errored(t)) match {
          case Blocked(cb) => cb(-\/(t))
          case _ =>
        }
      }
    }
    /*
     * This must be set on the container thread in Tomcat, or onWritePossible
     * will not be invoked.  This side effect needs to run between the acquisition
     * of the servletResponse and the calculation of the http4s Response.
     */
    out.setWriteListener(listener)

    val bodyWriter = { response: Response =>
      if (response.isChunked)
        autoFlush = true
      response.body.evalMap { chunk =>
        Task.async[ByteVector => Unit] { cb =>
          val blocked = Blocked(cb)
          state.getAndSet(blocked) match {
            case Ready if out.isReady =>
              if (state.compareAndSet(blocked, Ready))
                cb(writeChunk)
            case e @ Errored(t) =>
              if (state.compareAndSet(blocked, e))
                cb(-\/(t))
            case _ =>
              state.set(Blocked(cb))
          }
        }.map(_(chunk))
      }.run
    }
    bodyWriter
  }
}
