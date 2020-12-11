/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package blazecore
package util

import cats.effect.{Effect, IO}
import fs2._
import org.http4s.blaze.util.Execution
import scala.collection.mutable.Buffer
import scala.concurrent.{ExecutionContext, Future}

object DumpingWriter {
  def dump(p: EntityBody[IO]): IO[Array[Byte]] = {
    val w = new DumpingWriter()
    for (_ <- w.writeEntityBody(p)) yield (w.toArray)
  }
}

class DumpingWriter(implicit protected val F: Effect[IO]) extends EntityBodyWriter[IO] {
  override implicit protected def ec: ExecutionContext = Execution.trampoline

  private val buffer = Buffer[Chunk[Byte]]()

  def toArray: Array[Byte] =
    buffer.synchronized {
      Chunk.concatBytes(buffer.toSeq).toArray
    }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    buffer.synchronized {
      buffer += chunk
      Future.successful(false)
    }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    buffer.synchronized {
      buffer += chunk
      FutureUnit
    }
}
