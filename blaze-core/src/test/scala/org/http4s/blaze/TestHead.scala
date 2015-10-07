package org.http4s.blaze

import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.pipeline.Command.{Disconnected, Disconnect, OutboundCommand, EOF}

import java.nio.ByteBuffer

import scala.concurrent.duration.Duration
import scala.concurrent.{ Promise, Future }


abstract class TestHead(val name: String) extends HeadStage[ByteBuffer] {

  private var acc = Vector[Array[Byte]]()
  private val p = Promise[ByteBuffer]

  var closed = false

  def getBytes(): Array[Byte] = acc.toArray.flatten

  def result = p.future

  override def writeRequest(data: ByteBuffer): Future[Unit] = synchronized {
    if (closed) Future.failed(EOF)
    else {
      val cpy = new Array[Byte](data.remaining())
      data.get(cpy)
      acc :+= cpy
      Future.successful(())
    }
  }

  override def stageShutdown(): Unit = synchronized {
    closed = true
    super.stageShutdown()
    p.trySuccess(ByteBuffer.wrap(getBytes()))
  }
}

class SeqTestHead(body: Seq[ByteBuffer]) extends TestHead("SeqTestHead") {
  private val bodyIt = body.iterator

  override def readRequest(size: Int): Future[ByteBuffer] = synchronized {
    if (!closed && bodyIt.hasNext) Future.successful(bodyIt.next())
    else {
      sendInboundCommand(Disconnected)
      Future.failed(EOF)
    }
  }
}

class SlowTestHead(body: Seq[ByteBuffer], pause: Duration) extends TestHead("Slow TestHead") { self =>
  import org.http4s.blaze.util.Execution.scheduler

  // Will serve as our point of synchronization
  private val bodyIt = body.iterator

  private var currentRequest: Promise[ByteBuffer] = null

  private def clear(): Unit = bodyIt.synchronized {
    while(bodyIt.hasNext) bodyIt.next()
    if (currentRequest != null) {
      currentRequest.tryFailure(EOF)
      currentRequest = null
    }
  }

  override def outboundCommand(cmd: OutboundCommand): Unit = {
    cmd match {
      case Disconnect => clear()
      case _          => sys.error(s"TestHead received weird command: $cmd")
    }
    super.outboundCommand(cmd)
  }

  override def readRequest(size: Int): Future[ByteBuffer] = bodyIt.synchronized {
    if (currentRequest != null) {
      Future.failed(new IllegalStateException("Cannot serve multiple concurrent read requests"))
    }
    else if (bodyIt.isEmpty) Future.failed(EOF)
    else {
      currentRequest = Promise[ByteBuffer]
      scheduler.schedule(new Runnable {
        override def run(): Unit = bodyIt.synchronized {
          if (!closed && bodyIt.hasNext) currentRequest.trySuccess(bodyIt.next())
          else currentRequest.tryFailure(EOF)
          currentRequest = null
        }
      }, pause)

      currentRequest.future
    }
  }
}
