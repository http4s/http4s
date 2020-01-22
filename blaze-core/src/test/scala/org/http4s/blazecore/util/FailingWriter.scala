package org.http4s
package blazecore
package util

import cats.effect._
import fs2._
import org.http4s.blaze.pipeline.Command.EOF
import scala.concurrent.{ExecutionContext, Future}

class FailingWriter(implicit protected val F: Effect[IO]) extends EntityBodyWriter[IO] {
  override implicit protected val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    Future.failed(EOF)

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    Future.failed(EOF)
}
