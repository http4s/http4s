package org.http4s.blaze.util

import org.http4s.blaze.pipeline.Command.EOF

import fs2._
import scala.concurrent.{ExecutionContext, Future}

class FailingWriter() extends ProcessWriter {

  override implicit protected def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    Future.failed(EOF)

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    Future.failed(EOF)
}
