package org.http4s
package multipart

import java.nio.charset.StandardCharsets

import fs2._
import org.http4s.internal.ChunkWriter

private[http4s] class MultipartEncoder[F[_]] extends EntityEncoder[F, Multipart[F]] {

  //TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: Multipart[F]): Entity[F] =
    Entity(renderParts(mp.boundary)(mp.parts), None)

  val dash: String = "--"

  val dashBoundary: Boundary => String =
    boundary => s"$dash${boundary.value}"

  val delimiter: Boundary => String =
    boundary => s"${Boundary.CRLF}$dash${boundary.value}"

  val closeDelimiter: Boundary => String =
    boundary => s"${delimiter(boundary)}$dash"

  val start: Boundary => Chunk[Byte] = boundary =>
    new ChunkWriter()
      .append(dashBoundary(boundary))
      .append(Boundary.CRLF)
      .toChunk

  val end: Boundary => Chunk[Byte] = boundary =>
    new ChunkWriter()
      .append(closeDelimiter(boundary))
      .toChunk

  /**
    * encapsulation := delimiter CRLF body-part
    */
  val encapsulationWithoutBody: Boundary => String = boundary =>
    s"${Boundary.CRLF}${dashBoundary(boundary)}${Boundary.CRLF}"

  val renderHeaders: Headers => Chunk[Byte] = headers =>
    headers
      .foldLeft(new ChunkWriter()) { (chunkWriter, header) =>
        chunkWriter
          .append(header)
          .append(Boundary.CRLF)
      }
      .toChunk

  def renderPart(prelude: Chunk[Byte])(part: Part[F]): Stream[F, Byte] =
    Stream.chunk(prelude) ++
      Stream.chunk(renderHeaders(part.headers)) ++
      Stream.chunk(Chunk.bytes(Boundary.CRLF.getBytes(StandardCharsets.UTF_8))) ++
      part.body

  def renderParts(boundary: Boundary)(parts: Vector[Part[F]]): Stream[F, Byte] =
    if (parts.isEmpty) {
      Stream.empty.covary[F]
    } else {
      parts.tail
        .foldLeft(renderPart(start(boundary))(parts.head)) { (acc, part) =>
          acc ++
            renderPart(
              Chunk.bytes(encapsulationWithoutBody(boundary).getBytes(StandardCharsets.UTF_8)))(
              part)
        } ++ Stream.chunk(end(boundary))
    }

}
