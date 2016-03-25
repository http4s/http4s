package org.http4s
package multipart

import scala.util.Random

import org.http4s._
import org.http4s.MediaType._
import org.http4s.headers.{ `Content-Type` ⇒ ContentType, `Content-Disposition` => ContentDisposition }
import org.http4s.Http4s._
import org.http4s.Uri._
import org.http4s.util._
import scodec.bits.ByteVector
import org.http4s.EntityEncoder._
import scalaz.concurrent.Task
import Entity._
import scalaz.stream.Process

object MultipartEntityEncoder extends EntityEncoder[Multipart] {

  //TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: Multipart): Task[Entity] = {
    val dash             = "--"
    val dashBoundary:  Boundary => String =     boundary =>
                       new StringWriter()                <<
                       dash                              << 
                       boundary.value              result()    
    val delimiter:     Boundary => String =     boundary =>
                       new StringWriter()                <<
                       Boundary.CRLF                     <<
                       dash                              << 
                       boundary.value              result()
    val closeDelimiter:Boundary => String =     boundary =>
                       new StringWriter()                <<
                       delimiter(boundary)               <<
                       dash                        result()          
    val start:         Boundary => ByteVector = boundary =>
                       ByteVectorWriter()                <<
                       dashBoundary(boundary)            <<
                       Boundary.CRLF         toByteVector()
    val end:           Boundary => ByteVector = boundary =>
                       ByteVectorWriter()                <<
                       closeDelimiter(boundary) toByteVector
    val encapsulation: Boundary => String =     boundary =>
                       new StringWriter()                <<
                       Boundary.CRLF                     <<
                       dashBoundary(boundary)            <<
                       Boundary.CRLF               result()    

    val _start         = start(mp.boundary)
    val _end           = end(mp.boundary)
    val _encapsulation = ByteVector(encapsulation(mp.boundary).getBytes)

    def renderPart(prelude: ByteVector, p: Part) =
      Process.emit(prelude ++ (p.headers.foldLeft(ByteVectorWriter()) { (w, h) =>
        w << h << Boundary.CRLF
      } << Boundary.CRLF).toByteVector) ++ p.body

    val parts = mp.parts
    val body = parts.tail.foldLeft(renderPart(_start, parts.head)) { (acc, part) =>
      acc ++ renderPart(_encapsulation, part)
    } ++ Process.emit(_end)

    Task.now(EntityEncoder.Entity(body, None))
  }
}
