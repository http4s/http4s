/*
 * Copyright 2013 http4s.org
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

package org.http4s.internal

import fs2.Chunk
import org.http4s.util.Writer

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import scala.collection.mutable.Buffer

/** [[Writer]] that will result in a `Chunk`
  * @param toChunk initial `Chunk`
  */
private[http4s] class ChunkWriter(
    charset: Charset = StandardCharsets.UTF_8
) extends Writer {
  private[this] val chunks = Buffer[Chunk[Byte]]()

  def toChunk: Chunk[Byte] = Chunk.concatBytes(chunks)

  override def append(s: String): this.type = {
    chunks += Chunk.bytes(s.getBytes(charset))
    this
  }
}
