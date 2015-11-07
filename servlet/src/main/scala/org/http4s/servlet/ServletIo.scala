package org.http4s
package servlet

import java.util.concurrent.atomic.AtomicReference
import javax.servlet.{WriteListener, ReadListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import org.http4s.util.TrampolineExecutionContext
import scodec.bits.ByteVector

import scalaz.stream.Cause.{End, Terminated}
import scalaz.{\/, -\/}
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.stream.io.chunkR
import scalaz.syntax.either._

/**
 * Determines the mode of I/O used for reading request bodies and writing response bodies.
 */
sealed trait ServletIo {
  protected[servlet] def reader(servletRequest: HttpServletRequest): EntityBody

  /** May install a listener on the servlet response. */
  protected[servlet] def initWriter(servletResponse: HttpServletResponse): BodyWriter
}

/**
 * Use standard blocking reads and writes.
 *
 * This is more CPU efficient per request than [[NonBlockingServletIo]], but is likely to
 * require a larger request thread pool for the same load.
 */
case class BlockingServletIo(chunkSize: Int) extends ServletIo {
  override protected[servlet] def reader(servletRequest: HttpServletRequest): EntityBody =
    chunkR(servletRequest.getInputStream).map(_(chunkSize)).eval

  override protected[servlet] def initWriter(servletResponse: HttpServletResponse): BodyWriter = { response: Response =>
    val out = servletResponse.getOutputStream
    val flush = response.isChunked
    response.body.map { chunk =>
      out.write(chunk.toArray)
      if (flush)
        servletResponse.flushBuffer()
    }.run
  }
}

/**
 * Use non-blocking reads and writes.  Available only on containers that support Servlet 3.1.
 *
 * This can support more concurrent connections on a smaller request thread pool than [[BlockingServletIO]],
 * but consumes more CPU per request.  It is also known to cause IllegalStateExceptions in the logs
 * under high load up through  at least Tomcat 8.0.15.  These appear to be harmless, but are
 * operationally annoying.
 */
case class NonBlockingServletIo(chunkSize: Int) extends ServletIo {
  private[this] val LeftEnd = Terminated(End).left

  override protected[servlet] def reader(servletRequest: HttpServletRequest): EntityBody = {
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
      else if (len <= 0) {
        ByteVector.empty
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
    } onHalt (_.asHalt)
  }

  override protected[servlet] def initWriter(servletResponse: HttpServletResponse): BodyWriter = {
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
        Task.fork(Task.async[ByteVector => Unit] { cb =>
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
        }.map(_(chunk)))(TrampolineExecutionContext)
      }.run
    }
    bodyWriter
  }
}
