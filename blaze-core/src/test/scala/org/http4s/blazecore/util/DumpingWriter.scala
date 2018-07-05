package org.http4s
package blazecore
package util

import cats.effect.{Effect, IO}
import fs2._
import org.http4s.blaze.util.Execution
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

  private var buffer = Segment.empty[Byte]

  def toArray: Array[Byte] = buffer.synchronized {
    buffer.force.toArray
  }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = buffer.synchronized {
    buffer = buffer ++ Segment.chunk(chunk)
    Future.successful(false)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    buffer.synchronized {
      buffer = buffer ++ Segment.chunk(chunk)
      FutureUnit
    }
}
