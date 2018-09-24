package org.http4s
package servlet

import cats.effect._
import cats.implicits._
import fs2._
import java.util.concurrent.atomic.AtomicReference
import javax.servlet.{ReadListener, WriteListener}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.http4s.util.bug
import org.http4s.util.execution.trampoline
import org.log4s.getLogger
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

/**
  * Determines the mode of I/O used for reading request bodies and writing response bodies.
  */
sealed abstract class ServletIo[F[_]: Async] {
  protected[servlet] val F = Async[F]

  protected[servlet] def reader(servletRequest: HttpServletRequest): EntityBody[F]

  /** May install a listener on the servlet response. */
  protected[servlet] def initWriter(servletResponse: HttpServletResponse): BodyWriter[F]
}

/**
  * Use standard blocking reads and writes.
  *
  * This is more CPU efficient per request than [[NonBlockingServletIo]], but is likely to
  * require a larger request thread pool for the same load.
  */
final case class BlockingServletIo[F[_]: Effect: ContextShift](
    chunkSize: Int,
    blockingExecutionContext: ExecutionContext)
    extends ServletIo[F] {
  override protected[servlet] def reader(servletRequest: HttpServletRequest): EntityBody[F] =
    io.readInputStream[F](
      F.pure(servletRequest.getInputStream),
      chunkSize,
      blockingExecutionContext)

  override protected[servlet] def initWriter(
      servletResponse: HttpServletResponse): BodyWriter[F] = { response: Response[F] =>
    val out = servletResponse.getOutputStream
    val flush = response.isChunked
    response.body.chunks
      .map { chunk =>
        // Avoids copying for specialized chunks
        val byteChunk = chunk.toBytes
        out.write(byteChunk.values, byteChunk.offset, byteChunk.length)
        if (flush)
          servletResponse.flushBuffer()
      }
      .compile
      .drain
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
final case class NonBlockingServletIo[F[_]: Effect](chunkSize: Int) extends ServletIo[F] {
  private[this] val logger = getLogger

  private[this] def rightSome[A](a: A) = Right(Some(a))
  private[this] val rightNone = Right(None)

  override protected[servlet] def reader(servletRequest: HttpServletRequest): EntityBody[F] =
    Stream.suspend {
      sealed trait State
      case object Init extends State
      case object Ready extends State
      case object Complete extends State
      sealed case class Errored(t: Throwable) extends State
      sealed case class Blocked(cb: Callback[Option[Chunk[Byte]]]) extends State

      val in = servletRequest.getInputStream

      val state = new AtomicReference[State](Init)

      def read(cb: Callback[Option[Chunk[Byte]]]) = {
        val buf = new Array[Byte](chunkSize)
        val len = in.read(buf)

        if (len == chunkSize) cb(rightSome(Chunk.bytes(buf)))
        else if (len < 0) {
          state.compareAndSet(Ready, Complete) // will not overwrite an `Errored` state
          cb(rightNone)
        } else if (len == 0) {
          logger.warn("Encountered a read of length 0")
          cb(rightSome(Chunk.empty))
        } else cb(rightSome(Chunk.bytes(buf, 0, len)))
      }

      if (in.isFinished) Stream.empty
      else {
        // This effect sets the callback and waits for the first bytes to read
        val registerRead =
          // Shift execution to a different EC
          Async.shift(trampoline) *>
            F.async[Option[Chunk[Byte]]] { cb =>
              if (!state.compareAndSet(Init, Blocked(cb))) {
                cb(Left(bug("Shouldn't have gotten here: I should be the first to set a state")))
              } else
                in.setReadListener(
                  new ReadListener {
                    override def onDataAvailable(): Unit =
                      state.getAndSet(Ready) match {
                        case Blocked(cb) => read(cb)
                        case _ => ()
                      }

                    override def onError(t: Throwable): Unit =
                      state.getAndSet(Errored(t)) match {
                        case Blocked(cb) => cb(Left(t))
                        case _ => ()
                      }

                    override def onAllDataRead(): Unit =
                      state.getAndSet(Complete) match {
                        case Blocked(cb) => cb(rightNone)
                        case _ => ()
                      }
                  }
                )
            }

        val readStream = Stream.eval(registerRead) ++ Stream
          .repeatEval( // perform the initial set then transition into normal read mode
            // Shift execution to a different EC
            Async.shift(trampoline) *>
              F.async[Option[Chunk[Byte]]] { cb =>
                @tailrec
                def go(): Unit = state.get match {
                  case Ready if in.isReady => read(cb)

                  case Ready => // wasn't ready so set the callback and double check that we're still not ready
                    val blocked = Blocked(cb)
                    if (state.compareAndSet(Ready, blocked)) {
                      if (in.isReady && state.compareAndSet(blocked, Ready)) {
                        read(cb) // data became available while we were setting up the callbacks
                      } else { /* NOOP: our callback is either still needed or has been handled */ }
                    } else go() // Our state transitioned so try again.

                  case Complete => cb(rightNone)

                  case Errored(t) => cb(Left(t))

                  // This should never happen so throw a huge fit if it does.
                  case Blocked(c1) =>
                    val t = bug("Two callbacks found in read state")
                    cb(Left(t))
                    c1(Left(t))
                    logger.error(t)("This should never happen. Please report.")
                    throw t

                  case Init =>
                    cb(Left(bug("Should have left Init state by now")))
                }
                go()
              })
        readStream.unNoneTerminate.flatMap(Stream.chunk)
      }
    }

  override protected[servlet] def initWriter(
      servletResponse: HttpServletResponse): BodyWriter[F] = {
    sealed trait State
    case object Init extends State
    case object Ready extends State
    sealed case class Errored(t: Throwable) extends State
    sealed case class Blocked(cb: Callback[Chunk[Byte] => Unit]) extends State
    sealed case class AwaitingLastWrite(cb: Callback[Unit]) extends State

    val out = servletResponse.getOutputStream
    /*
     * If onWritePossible isn't called at least once, Tomcat begins to throw
     * NullPointerExceptions from NioEndpoint$SocketProcessor.doRun under
     * load.  The Init state means we block callbacks until the WriteListener
     * fires.
     */
    val state = new AtomicReference[State](Init)
    @volatile var autoFlush = false

    val writeChunk = Right { chunk: Chunk[Byte] =>
      if (!out.isReady) {
        logger.error(s"writeChunk called while out was not ready, bytes will be lost!")
      } else {
        out.write(chunk.toArray)
        if (autoFlush && out.isReady)
          out.flush()
      }
    }

    val listener = new WriteListener {
      override def onWritePossible(): Unit =
        state.getAndSet(Ready) match {
          case Blocked(cb) => cb(writeChunk)
          case AwaitingLastWrite(cb) => cb(Right(()))
          case old @ _ => ()
        }

      override def onError(t: Throwable): Unit =
        state.getAndSet(Errored(t)) match {
          case Blocked(cb) => cb(Left(t))
          case AwaitingLastWrite(cb) => cb(Left(t))
          case _ => ()
        }
    }
    /*
     * This must be set on the container thread in Tomcat, or onWritePossible
     * will not be invoked.  This side effect needs to run between the acquisition
     * of the servletResponse and the calculation of the http4s Response.
     */
    out.setWriteListener(listener)

    val awaitLastWrite = Stream.eval_ {
      // Shift execution to a different EC
      Async.shift(trampoline) *>
        F.async[Unit] { cb =>
          state.getAndSet(AwaitingLastWrite(cb)) match {
            case Ready if out.isReady => cb(Right(()))
            case _ => ()
          }
        }
    }

    { response: Response[F] =>
      if (response.isChunked)
        autoFlush = true
      response.body.chunks
        .evalMap { chunk =>
          // Shift execution to a different EC
          Async.shift(trampoline) *>
            F.async[Chunk[Byte] => Unit] { cb =>
                val blocked = Blocked(cb)
                state.getAndSet(blocked) match {
                  case Ready if out.isReady =>
                    if (state.compareAndSet(blocked, Ready))
                      cb(writeChunk)
                  case e @ Errored(t) =>
                    if (state.compareAndSet(blocked, e))
                      cb(Left(t))
                  case _ =>
                    ()
                }
              }
              .map(_(chunk))
        }
        .append(awaitLastWrite)
        .compile
        .drain
    }
  }
}
