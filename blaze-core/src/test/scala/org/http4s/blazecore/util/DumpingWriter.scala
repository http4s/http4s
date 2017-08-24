package org.http4s
package blazecore
package util

import cats._
import cats.effect.{Effect, IO}
import cats.implicits._
import fs2._
import org.http4s.blaze.util.Execution
import org.http4s.util.chunk.ByteChunkMonoid

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object DumpingWriter {
  def dump(p: EntityBody[IO]): Array[Byte] = {
    val w = new DumpingWriter()
    w.writeEntityBody(p).unsafeRunSync()
    w.toArray
  }
}

class DumpingWriter(implicit protected val F: Effect[IO]) extends EntityBodyWriter[IO] {
  override implicit protected def ec: ExecutionContext = Execution.trampoline

  private val buffers = new ListBuffer[Chunk[Byte]]

  def toArray: Array[Byte] = buffers.synchronized {
    Foldable[List].fold(buffers.toList).toBytes.values
  }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = buffers.synchronized {
    buffers += chunk
    Future.successful(false)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    buffers += chunk
    Future.successful(())
  }
}
