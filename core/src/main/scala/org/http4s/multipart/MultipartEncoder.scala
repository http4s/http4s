package org.http4s
package multipart


import org.http4s.EntityEncoder._
import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.Uri._
import org.http4s.util._
import fs2._

private[http4s] object MultipartEncoder extends EntityEncoder[Multipart] {
  import scala.language.postfixOps

  //TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: Multipart): Task[Entity] = Task.delay(Entity(renderParts(mp.boundary)(mp.parts), None))

  val dash : String = "--"

  val dashBoundary: Boundary => String =
    boundary => s"$dash${boundary.value}"

  val delimiter: Boundary => String =
    boundary => s"${Boundary.CRLF}$dash${boundary.value}"

  val closeDelimiter: Boundary => String =
    boundary => s"${delimiter(boundary)}$dash"

  val start: Boundary => Chunk[Byte] = boundary =>
    ChunkWriter()
      .append(dashBoundary(boundary))
      .append(Boundary.CRLF)
      .toChunk

  val end: Boundary => Chunk[Byte] = boundary =>
    ChunkWriter()
    .append(closeDelimiter(boundary))
    .toChunk

  /**
    * encapsulation := delimiter CRLF body-part
    */
  val encapsulationWithoutBody : Boundary => String = boundary =>
    s"${Boundary.CRLF}${dashBoundary(boundary)}${Boundary.CRLF}"

  val renderHeaders: Headers => Chunk[Byte] = headers =>
    headers.foldLeft(ChunkWriter()){(chunkWriter,header) =>
      chunkWriter
        .append(header)
        .append(Boundary.CRLF)
    }.toChunk

  def renderPart(prelude: Chunk[Byte])(part: Part): Stream[Task, Byte] =
      Stream.chunk(prelude) ++
        Stream.chunk(renderHeaders(part.headers)) ++
        Stream.chunk(Chunk.bytes(Boundary.CRLF.getBytes)) ++
        Stream.eval(part.body).map(_.toSeq).flatMap(Stream.emits)


  def renderParts(boundary: Boundary)(parts: Vector[Part]): Stream[Task, Byte] =
    parts
      .tail
      .foldLeft(renderPart(start(boundary))(parts.head)){(acc, part) =>
        acc ++
          renderPart(
            Chunk.bytes(encapsulationWithoutBody(boundary).getBytes)
          )(part)
      } ++
      Stream.chunk(end(boundary))


}
