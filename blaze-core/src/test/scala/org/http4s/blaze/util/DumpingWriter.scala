package org.http4s
package blaze
package util

import cats._
import fs2._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

import org.http4s.batteries._
import org.http4s.util.chunk.ByteChunkMonoid

object DumpingWriter {
  def dump(p: EntityBody): Chunk[Byte] = {
    val w = new DumpingWriter()
    w.writeProcess(p).unsafeRun
    w.getVector
  }
}

class DumpingWriter extends ProcessWriter {
  private val buffers = new ListBuffer[Chunk[Byte]]

  def getVector(): Chunk[Byte] = buffers.synchronized {
    Foldable[List].fold(buffers.toList)
  }

  override implicit protected def ec: ExecutionContext = Execution.trampoline

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = buffers.synchronized {
    buffers += chunk
    Future.successful(false)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    buffers += chunk
    Future.successful(())
  }
}
