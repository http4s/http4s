package org.http4s
package blazecore
package util

import org.http4s.blaze.pipeline.Command.EOF
import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}



class FailingWriter() extends ProcessWriter {

  override implicit protected def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override protected def writeEnd(chunk: ByteVector): Future[Boolean] = Future.failed(EOF)

  override protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = Future.failed(EOF)
}
