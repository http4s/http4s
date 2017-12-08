package org.http4s
package multipart

import cats.effect.Sync
import fs2._
import org.http4s.util._

private[http4s] class MultipartEncoder[F[_]: Sync] extends EntityEncoder[F, Multipart[F]] {

  //TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: Multipart[F]): F[Entity[F]] =
    Sync[F].delay(Entity(renderParts(mp.boundary)(mp.parts), None))

  val dash: String = "--"

  val dashBoundary: Boundary => String =
    boundary => s"$dash${boundary.value}"

  val delimiter: Boundary => String =
    boundary => s"${Boundary.CRLF}$dash${boundary.value}"

  val closeDelimiter: Boundary => String =
    boundary => s"${delimiter(boundary)}$dash"

  val start: Boundary => Segment[Byte, Unit] = boundary =>
    ChunkWriter()
      .append(dashBoundary(boundary))
      .append(Boundary.CRLF)
      .toByteSegment

  val end: Boundary => Segment[Byte, Unit] = boundary =>
    ChunkWriter()
      .append(closeDelimiter(boundary))
      .toByteSegment

  /**
    * encapsulation := delimiter CRLF body-part
    */
  val encapsulationWithoutBody: Boundary => String = boundary =>
    s"${Boundary.CRLF}${dashBoundary(boundary)}${Boundary.CRLF}"

  val renderHeaders: Headers => Segment[Byte, Unit] = headers =>
    headers
      .foldLeft(ChunkWriter()) { (chunkWriter, header) =>
        chunkWriter
          .append(header)
          .append(Boundary.CRLF)
      }
      .toByteSegment

  def renderPart(prelude: Segment[Byte, Unit])(part: Part[F]): Stream[F, Byte] =
    Stream.segment(prelude) ++
      Stream.segment(renderHeaders(part.headers)) ++
      Stream.chunk(Chunk.bytes(Boundary.CRLF.getBytes)) ++
      part.body

  def renderParts(boundary: Boundary)(parts: Vector[Part[F]]): Stream[F, Byte] =
    parts.tail
      .foldLeft(renderPart(start(boundary))(parts.head)) { (acc, part) =>
        acc ++
          renderPart(Segment.array(encapsulationWithoutBody(boundary).getBytes))(part)
      } ++ Stream.segment(end(boundary))

}
