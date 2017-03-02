package org.http4s
package multipart

import scala.util.Random

import org.http4s.EntityEncoder._
import org.http4s.MediaType._
import org.http4s.headers._
import org.http4s.Uri._
import org.http4s.util._
import fs2._

private[http4s] object MultipartEncoder extends EntityEncoder[Multipart] {

  //TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: Multipart): Task[Entity] = {
    import scala.language.postfixOps
    val dash             = "--"
    val dashBoundary:  Boundary => String =     boundary =>
                       new StringWriter()                <<
                       dash                              <<
                       boundary.value              result
    val delimiter:     Boundary => String =     boundary =>
                       new StringWriter()                <<
                       Boundary.CRLF                     <<
                       dash                              <<
                       boundary.value              result
    val closeDelimiter:Boundary => String =     boundary =>
                       new StringWriter()                <<
                       delimiter(boundary)               <<
                       dash                        result
    val start:         Boundary => Chunk[Byte] = boundary =>
                       ChunkWriter()                     <<
                       dashBoundary(boundary)            <<
                       Boundary.CRLF                toChunk
    val end:           Boundary => Chunk[Byte] = boundary =>
                       ChunkWriter()                     <<
                       closeDelimiter(boundary)     toChunk
    val encapsulation: Boundary => String =     boundary =>
                       new StringWriter()                <<
                       Boundary.CRLF                     <<
                       dashBoundary(boundary)            <<
                       Boundary.CRLF               result

    val _start         = start(mp.boundary)
    val _end           = end(mp.boundary)
    val _encapsulation = Chunk.bytes(encapsulation(mp.boundary).getBytes)

    def renderPart(prelude: Chunk[Byte], p: Part) =
      Stream.chunk(prelude) ++
        Stream.chunk(p.headers.foldLeft(ChunkWriter()) { (w, h) =>
          w << h << Boundary.CRLF
        } << Boundary.CRLF toChunk) ++
        p.body

    val parts = mp.parts
    val body = parts.tail.foldLeft(renderPart(_start, parts.head)) { (acc, part) =>
      acc ++ renderPart(_encapsulation, part)
    } ++ Stream.chunk(_end)

    Task.now(Entity(body, None))
  }
}
