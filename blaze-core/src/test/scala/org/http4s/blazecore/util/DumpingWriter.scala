package org.http4s
package blazecore
package util

import cats.effect.{Effect, IO}
import fs2._
import org.http4s.blaze.util.Execution
import scala.collection.mutable.Buffer
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

  private val buffer = Buffer[Chunk[Byte]]()

  def toArray: Array[Byte] = buffer.synchronized {
    Chunk.concatBytes(buffer.toSeq).toArray
  }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = buffer.synchronized {
    buffer += chunk
    Future.successful(false)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    buffer.synchronized {
      buffer += chunk
      FutureUnit
    }
}
