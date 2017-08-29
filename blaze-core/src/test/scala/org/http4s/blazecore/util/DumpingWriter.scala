package org.http4s
package blazecore
package util

import cats._
import cats.implicits._
import fs2._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

import org.http4s.blaze.util.Execution
import org.http4s.util.chunk.ByteChunkMonoid

object DumpingWriter {
  def dump(p: EntityBody): Array[Byte] = {
    val w = new DumpingWriter()
    w.writeEntityBody(p).unsafeRun
    w.toArray
  }
}

class DumpingWriter extends EntityBodyWriter {
  private val buffers = new ListBuffer[Chunk[Byte]]

  def toArray: Array[Byte] = buffers.synchronized {
    Foldable[List].fold(buffers.toList).toBytes.values
  }

  override implicit protected def ec: ExecutionContext = Execution.trampoline

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = buffers.synchronized {
    buffers += chunk
    Future.successful(false)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    buffers += chunk
    FutureUnit
  }
}
