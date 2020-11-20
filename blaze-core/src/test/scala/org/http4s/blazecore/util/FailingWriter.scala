/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package blazecore
package util

import cats.effect._
import fs2._
import org.http4s.blaze.pipeline.Command.EOF
import scala.concurrent.{ExecutionContext, Future}

class FailingWriter(implicit protected val F: Async[IO]) extends EntityBodyWriter[IO] {
  override implicit protected val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    Future.failed(EOF)

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    Future.failed(EOF)
}
