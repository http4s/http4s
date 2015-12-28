package org.http4s.blaze.util

import scodec.bits.ByteVector

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scalaz.concurrent.Task

import scalaz.stream.Process

object DumpingWriter {
  def dump(p: Process[Task, ByteVector]): ByteVector = {
    val w = new DumpingWriter()
    w.writeProcess(p).run
    w.getVector()
  }
}

class DumpingWriter extends ProcessWriter {
  private val buffers = new ListBuffer[ByteVector]

  def getVector(): ByteVector = buffers.synchronized {
    buffers.foldLeft(ByteVector.empty)(_ ++ _)
  }

  override implicit protected def ec: ExecutionContext = Execution.trampoline

  override protected def writeEnd(chunk: ByteVector): Future[Boolean] = buffers.synchronized {
    buffers += chunk
    Future.successful(false)
  }

  override protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = {
    buffers += chunk
    Future.successful(())
  }
}
