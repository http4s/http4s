package org.http4s.client.blaze

import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.pipeline.Command.{EOF, InboundCommand}
import org.http4s.blaze.util.{Cancelable, TickWheelExecutor}
import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

final private[blaze] class ClientTimeoutStage(
    responseHeaderTimeout: Duration,
    idleTimeout: Duration,
    requestTimeout: Duration,
    exec: TickWheelExecutor)
    extends MidStage[ByteBuffer, ByteBuffer] { stage =>

  import ClientTimeoutStage._

  private implicit val ec = org.http4s.blaze.util.Execution.directec

  // The timeout between request body completion and response header
  // completion.
  private val activeResponseHeaderTimeout = new AtomicReference[Cancelable](null)

  // The total timeout for the request. Lasts the lifetime of the stage.
  private val activeReqTimeout = new AtomicReference[Cancelable](null)

  // The timeoutState contains a Cancelable, null, or a TimeoutException
  // It will also act as the point of synchronization
  private val timeoutState = new AtomicReference[AnyRef](null)

  override def name: String =
    s"ClientTimeoutStage: Response Header: $responseHeaderTimeout, Idle: $idleTimeout, Request: $requestTimeout"

  /////////// Private impl bits //////////////////////////////////////////
  private def killswitch(name: String, timeout: Duration) = new Runnable {
    override def run(): Unit = {
      logger.debug(s"Client stage is disconnecting due to $name timeout after $timeout.")

      // check the idle timeout conditions
      timeoutState.getAndSet(new TimeoutException(s"Client $name timeout after $timeout.")) match {
        case null => // noop
        case c: Cancelable => c.cancel() // this should be the registration of us
        case _: TimeoutException => // Interesting that we got here.
      }

      cancelResponseHeaderTimeout()

      // Cancel the active request timeout if it exists
      activeReqTimeout.getAndSet(Closed) match {
        case null =>
          /* We beat the startup. Maybe timeout is 0? */
          closePipeline(None)

        case Closed => /* Already closed, no need to disconnect */

        case timeout =>
          timeout.cancel()
          closePipeline(None)
      }
    }
  }

  private val responseHeaderTimeoutKillswitch = killswitch("response header", responseHeaderTimeout)
  private val idleTimeoutKillswitch = killswitch("idle", idleTimeout)
  private val requestTimeoutKillswitch = killswitch("request", requestTimeout)

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
    activeReqTimeout.getAndSet(Closed) match {
      case null => logger.error("Shouldn't get here.")
      case timeout => timeout.cancel()
    }
    super.stageShutdown()
  }

  override protected def stageStartup(): Unit = {
    super.stageStartup()
    val timeout = exec.schedule(requestTimeoutKillswitch, ec, requestTimeout)
    if (!activeReqTimeout.compareAndSet(null, timeout)) {
      activeReqTimeout.get() match {
        case Closed => // NOOP: the timeout already triggered
        case _ => logger.error("Shouldn't get here.")
      }
    } else resetTimeout()

    sendInboundCommand(new EventListener {
      def onResponseHeaderComplete(): Unit = cancelResponseHeaderTimeout()
      def onRequestSendComplete(): Unit = activateResponseHeaderTimeout()
    })
  }

  /////////// Private stuff ////////////////////////////////////////////////

  def checkTimeout[T](f: Future[T]): Future[T] = {
    val p = Promise[T]

    f.onComplete {
      case s @ Success(_) =>
        resetTimeout()
        p.tryComplete(s)

      case eof @ Failure(EOF) =>
        timeoutState.get() match {
          case t: TimeoutException => p.tryFailure(t)
          case c: Cancelable =>
            c.cancel()
            p.tryComplete(eof)

          case null => p.tryComplete(eof)
        }

      case v @ Failure(_) => p.complete(v)
    }

    p.future
  }

  private def setAndCancel(next: Cancelable): Unit = {
    @tailrec
    def go(): Unit = timeoutState.get() match {
      case null =>
        if (!timeoutState.compareAndSet(null, next)) go()

      case c: Cancelable =>
        if (!timeoutState.compareAndSet(c, next)) go()
        else c.cancel()

      case _: TimeoutException =>
        if (next != null) next.cancel()
    }; go()
  }

  private def resetTimeout(): Unit =
    setAndCancel(exec.schedule(idleTimeoutKillswitch, idleTimeout))

  private def cancelTimeout(): Unit = setAndCancel(null)

  private def activateResponseHeaderTimeout(): Unit = {
    val timeout = exec.schedule(responseHeaderTimeoutKillswitch, ec, responseHeaderTimeout)
    if (!activeResponseHeaderTimeout.compareAndSet(null, timeout))
      timeout.cancel()
  }

  private def cancelResponseHeaderTimeout(): Unit =
    activeResponseHeaderTimeout.getAndSet(Closed) match {
      case null => // no-op
      case timeout => timeout.cancel()
    }
}

private[blaze] object ClientTimeoutStage {
  trait EventListener extends InboundCommand {
    def onRequestSendComplete(): Unit
    def onResponseHeaderComplete(): Unit
  }

  // Make sure we have our own _stable_ copy for synchronization purposes
  private val Closed = new Cancelable {
    def cancel() = ()
    override def toString = "Closed"
  }
}
