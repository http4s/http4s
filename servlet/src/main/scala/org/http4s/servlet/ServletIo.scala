package org.http4s
package servlet

import java.util.concurrent.atomic.AtomicReference
import javax.servlet.{WriteListener, ReadListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import org.http4s.util.{TrampolineExecutionContext, bug}
import scodec.bits.ByteVector

import scala.annotation.tailrec
import scalaz.stream.Cause.{End, Terminated}
import scalaz.{\/, -\/}
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.stream.io.chunkR
import scalaz.syntax.either._

import org.log4s.getLogger

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
 * This can support more concurrent connections on a smaller request thread pool than [[BlockingServletIo]],
 * but consumes more CPU per request.  It is also known to cause IllegalStateExceptions in the logs
 * under high load up through  at least Tomcat 8.0.15.  These appear to be harmless, but are
 * operationally annoying.
 */
case class NonBlockingServletIo(chunkSize: Int) extends ServletIo {
  private[this] val logger = getLogger

  private[this] val LeftEnd = Terminated(End).left
  private[this] val RightUnit = ().right

  override protected[servlet] def reader(servletRequest: HttpServletRequest): EntityBody = {
    sealed trait State
    case object Init extends State
    case object Ready extends State
    case object Complete extends State
    case class Errored(t: Throwable) extends State
    case class Blocked(cb: Callback[ByteVector]) extends State

    val in = servletRequest.getInputStream

    val state = new AtomicReference[State](Init)

    def read(cb: Callback[ByteVector]) = {
      val buff = new Array[Byte](chunkSize)
      val len = in.read(buff)

      if (len == chunkSize) cb(ByteVector.view(buff).right)
      else if (len < 0) {
        state.compareAndSet(Ready, Complete) // will not overwrite an `Errored` state
        cb(LeftEnd)
      }
      else if (len == 0) {
        logger.warn("Encountered a read of length 0")
        cb(ByteVector.empty.right)
      }
      else cb(ByteVector(buff, 0, len).right)
    }

    if (in.isFinished) halt
    else {
      // This Task sets the callback and waits for the first bytes to read
      val registerRead = Task.async[ByteVector] { cb =>
        if (!state.compareAndSet(Init, Blocked(cb))) {
          cb(bug("Shouldn't have gotten here: I should be the first to set a state").left)
        }
        else in.setReadListener(
          new ReadListener {
            override def onDataAvailable(): Unit =
              state.getAndSet(Ready) match {
                case Blocked(cb) => read(cb)
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
        )
      }

      eval(registerRead) ++ repeatEval ( // perform the initial set then transition into normal read mode
        Task.fork {
          Task.async[ByteVector] { cb =>
            @tailrec
            def go(): Unit = state.get match {
              case Ready if in.isReady => read(cb)

              case Ready => // wasn't ready so set the callback and double check that we're still not ready
                val blocked = Blocked(cb)
                if (state.compareAndSet(Ready, blocked)) {
                  if (in.isReady && state.compareAndSet(blocked, Ready)) {
                    read(cb) // data became available while we were setting up the callbacks
                  }
                  else { /* NOOP: our callback is either still needed or has been handled */ }
                }
                else go() // Our state transitioned so try again.

              case Complete => cb(LeftEnd)

              case e@Errored(t) => cb(t.left)

              // This should never happen so throw a huge fit if it does.
              case Blocked(c1) =>
                val t = bug("Two callbacks found in read state")
                cb(t.left)
                c1(t.left)
                logger.error(t)("This should never happen. Please report.")
                throw t

              case Init =>
                cb(bug("Should have left Init state by now").left)
            }
            go()
          }
        }(TrampolineExecutionContext)
      ) onHalt (_.asHalt)
    }
  }

  override protected[servlet] def initWriter(servletResponse: HttpServletResponse): BodyWriter = {
    type Callback[A] = Throwable \/ A => Unit

    sealed trait State
    case object Init extends State
    case object Ready extends State
    case class Errored(t: Throwable) extends State
    case class Blocked(cb: Callback[ByteVector => Unit]) extends State
    case class AwaitingLastWrite(cb: Callback[Unit]) extends State

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
      if (!out.isReady) {
        logger.error(s"writeChunk called while out was not ready, bytes will be lost!")
      } else {
        out.write(chunk.toArray)
        if (autoFlush && out.isReady)
          out.flush()
      }
    }.right

    val listener = new WriteListener {
      override def onWritePossible(): Unit = {
        state.getAndSet(Ready) match {
          case Blocked(cb) => cb(writeChunk)
          case AwaitingLastWrite(cb) => cb(RightUnit)
          case old =>
        }
      }

      override def onError(t: Throwable): Unit =  {
        state.getAndSet(Errored(t)) match {
          case Blocked(cb) => cb(t.left)
          case AwaitingLastWrite(cb) => cb(t.left)
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

    val awaitLastWrite = eval_ {
      Task.fork {
        Task.async[Unit] { cb =>
          state.getAndSet(AwaitingLastWrite(cb)) match {
            case Ready if out.isReady => cb(RightUnit)
            case _ =>
          }
        }
      }(TrampolineExecutionContext)
    }

    { response: Response =>
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
          }
        }.map(_(chunk)))(TrampolineExecutionContext)
      }.append(awaitLastWrite).run
    }
  }
}
