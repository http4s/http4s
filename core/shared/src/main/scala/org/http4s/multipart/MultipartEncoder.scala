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

package org.http4s
package multipart

import fs2._
import org.http4s.internal.ChunkWriter

import java.nio.charset.StandardCharsets

private[http4s] class MultipartEncoder[F[_]] extends EntityEncoder[F, Multipart[F]] {
  // TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: Multipart[F]): Entity[F] =
    Entity(renderParts(mp.boundary)(mp.parts), None)

  val dash: String = "--"

  val dashBoundary: Boundary => String =
    boundary => s"$dash${boundary.value}"

  val delimiter: Boundary => String =
    boundary => s"${Boundary.CRLF}$dash${boundary.value}"

  // The close-delimiter does not require a trailing CRLF, but adding
  // one makes it more robust with real implementations.  The wasted
  // two bytes go into the "epilogue", which the recipient is to
  // ignore.
  val closeDelimiter: Boundary => String =
    boundary => s"${delimiter(boundary)}$dash${Boundary.CRLF}"

  val start: Boundary => Chunk[Byte] = boundary =>
    new ChunkWriter()
      .append(dashBoundary(boundary))
      .append(Boundary.CRLF)
      .toChunk

  val end: Boundary => Chunk[Byte] = boundary =>
    new ChunkWriter()
      .append(closeDelimiter(boundary))
      .toChunk

  /** encapsulation := delimiter CRLF body-part
    */
  val encapsulationWithoutBody: Boundary => String = boundary =>
    s"${Boundary.CRLF}${dashBoundary(boundary)}${Boundary.CRLF}"

  val renderHeaders: Headers => Chunk[Byte] = headers =>
    headers.headers
      .foldLeft(new ChunkWriter()) { (chunkWriter, header) =>
        chunkWriter
          .append(header)
          .append(Boundary.CRLF)
      }
      .toChunk

  def renderPart(prelude: Chunk[Byte])(part: Part[F]): Stream[F, Byte] =
    Stream.chunk(prelude) ++
      Stream.chunk(renderHeaders(part.headers)) ++
      Stream.chunk(Chunk.array(Boundary.CRLF.getBytes(StandardCharsets.UTF_8))) ++
      part.body

  def renderParts(boundary: Boundary)(parts: Vector[Part[F]]): Stream[F, Byte] =
    if (parts.isEmpty)
      Stream.empty
    else
      parts.tail
        .foldLeft(renderPart(start(boundary))(parts.head)) { (acc, part) =>
          acc ++
            renderPart(
              Chunk.array(encapsulationWithoutBody(boundary).getBytes(StandardCharsets.UTF_8))
            )(part)
        } ++ Stream.chunk(end(boundary))
}
