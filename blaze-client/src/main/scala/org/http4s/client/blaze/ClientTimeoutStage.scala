package org.http4s.client.blaze

import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.pipeline.Command.{EOF, Disconnect}
import org.http4s.blaze.util.{ Cancellable, TickWheelExecutor }


final private class ClientTimeoutStage(idleTimeout: Duration, requestTimeout: Duration, exec: TickWheelExecutor)
  extends MidStage[ByteBuffer, ByteBuffer] 
{ stage =>

  private implicit val ec = org.http4s.blaze.util.Execution.directec
  private var activeReqTimeout: Cancellable = Cancellable.noopCancel

  override def name: String = s"ClientTimeoutStage: Idle: $idleTimeout, Request: $requestTimeout"

  /////////// Private impl bits //////////////////////////////////////////
  private val killswitch = new Runnable {
    override def run(): Unit = {
      logger.debug(s"Client stage is disconnecting due to timeout.")
      // kill the active request: we've already timed out.
      activeReqTimeout.cancel()

      // check the idle timeout conditions
      timeoutState.getAndSet(new TimeoutException(s"Client timeout.")) match {
        case null => // noop
        case c: Cancellable => c.cancel() // this should be the registration of us
        case _: TimeoutException => // Interesting that we got here.
      }
      sendOutboundCommand(Disconnect)
    }
  }

  // The timeoutState contains a Cancellable, null, or a TimeoutException
  private val timeoutState = new AtomicReference[Any](null)

  // Startup on creation

  /////////// Pass through implementations ////////////////////////////////

  def initialize(): Unit = stageStartup()

  override def readRequest(size: Int): Future[ByteBuffer] =
    checkTimeout(channelRead(size))

  override def writeRequest(data: ByteBuffer): Future[Unit] =
    checkTimeout(channelWrite(data))

  override def writeRequest(data: Seq[ByteBuffer]): Future[Unit] =
    checkTimeout(channelWrite(data))

  /////////// Protected impl bits //////////////////////////////////////////

  override protected def stageShutdown(): Unit = {
    cancelTimeout()
    activeReqTimeout.cancel()
    super.stageShutdown()
  }

  override protected def stageStartup(): Unit = {
    super.stageStartup()
    activeReqTimeout= exec.schedule(killswitch, requestTimeout)
    resetTimeout()
  }

  /////////// Private stuff ////////////////////////////////////////////////

  def checkTimeout[T](f: Future[T]): Future[T] = {
    val p = Promise[T]

    f.onComplete {
      case s@ Success(_) =>
        resetTimeout()
        p.tryComplete(s)

      case eof@ Failure(EOF) => timeoutState.get() match {
        case t: TimeoutException => p.tryFailure(t)
        case c: Cancellable =>
          c.cancel()
          p.tryComplete(eof)

        case null => p.tryComplete(eof)
      }

      case v@ Failure(_) => p.complete(v)
    }

    p.future
  }

  private def setAndCancel(next: Cancellable): Unit = {
    @tailrec
    def go(): Unit = timeoutState.get() match {
      case null =>
        if (!timeoutState.compareAndSet(null, next)) go()

      case c: Cancellable =>
        if (!timeoutState.compareAndSet(c, next)) go()
        else c.cancel()

      case e: TimeoutException =>
        if (next != null) next.cancel()
    }; go()
  }

  private def resetTimeout(): Unit = setAndCancel(exec.schedule(killswitch, idleTimeout))

  private def cancelTimeout(): Unit = setAndCancel(null)
}