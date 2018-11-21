package org.http4s
package blazecore

import cats.effect.IO
import fs2.concurrent.Queue
import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.pipeline.Command._
import org.http4s.blaze.util.TickWheelExecutor
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scodec.bits.ByteVector

abstract class TestHead(val name: String) extends HeadStage[ByteBuffer] {
  private var acc = ByteVector.empty
  private val p = Promise[ByteBuffer]

  var closed = false

  @volatile var closeCauses = Vector[Option[Throwable]]()

  def getBytes(): Array[Byte] = acc.toArray

  val result = p.future

  override def writeRequest(data: ByteBuffer): Future[Unit] = synchronized {
    if (closed) Future.failed(EOF)
    else {
      acc ++= ByteVector.view(data)
      util.FutureUnit
    }
  }

  override def stageShutdown(): Unit = synchronized {
    closed = true
    super.stageShutdown()
    p.trySuccess(ByteBuffer.wrap(getBytes()))
    ()
  }

  override def doClosePipeline(cause: Option[Throwable]): Unit = {
    closeCauses :+= cause
    cause.foreach(logger.error(_)(s"$name received unhandled error command"))
    sendInboundCommand(Disconnected)
  }
}

class SeqTestHead(body: Seq[ByteBuffer]) extends TestHead("SeqTestHead") {
  private val bodyIt = body.iterator

  override def readRequest(size: Int): Future[ByteBuffer] = synchronized {
    if (!closed && bodyIt.hasNext) Future.successful(bodyIt.next())
    else {
      stageShutdown()
      sendInboundCommand(Disconnected)
      Future.failed(EOF)
    }
  }
}

final class QueueTestHead(queue: Queue[IO, Option[ByteBuffer]]) extends TestHead("QueueTestHead") {
  private val closedP = Promise[Nothing]

  override def readRequest(size: Int): Future[ByteBuffer] = {
    val p = Promise[ByteBuffer]
    p.tryCompleteWith(queue.dequeue1.flatMap {
      case Some(bb) => IO.pure(bb)
      case None => IO.raiseError(EOF)
    }.unsafeToFuture)
    p.tryCompleteWith(closedP.future)
    p.future
  }

  override def stageShutdown(): Unit = {
    closedP.tryFailure(EOF)
    super.stageShutdown()
  }
}

final class SlowTestHead(body: Seq[ByteBuffer], pause: Duration, scheduler: TickWheelExecutor)
    extends TestHead("Slow TestHead") { self =>

  private val bodyIt = body.iterator
  private var currentRequest: Option[Promise[ByteBuffer]] = None

  private def resolvePending(result: Try[ByteBuffer]): Unit = {
    currentRequest.foreach(_.tryComplete(result))
    currentRequest = None
  }

  private def clear(): Unit = synchronized {
    while (bodyIt.hasNext) bodyIt.next()
    resolvePending(Failure(EOF))
  }

  override def stageShutdown(): Unit = synchronized {
    clear()
    super.stageShutdown()
  }

  override def readRequest(size: Int): Future[ByteBuffer] = self.synchronized {
    currentRequest match {
      case Some(_) =>
        Future.failed(new IllegalStateException("Cannot serve multiple concurrent read requests"))
      case None =>
        val p = Promise[ByteBuffer]
        currentRequest = Some(p)

        scheduler.schedule(new Runnable {
          override def run(): Unit = self.synchronized {
            resolvePending {
              if (!closed && bodyIt.hasNext) Success(bodyIt.next())
              else Failure(EOF)
            }
          }
        }, pause)

        p.future
    }
  }
}
