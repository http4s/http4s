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


object formDataEncoder extends EntityEncoder[FormData] {

  private val contentDisposition:String => ContentDisposition =
                    name => ContentDisposition("form-data", Map("name" -> name))

  def headers: Headers = Headers.empty

  def toEntity(fd: FormData): Task[Entity] = {
    lazy val headers = fd.contentType.foldLeft(ByteVectorWriter()                <<
                                               contentDisposition(fd.name.value) <<
                                               Boundary.CRLF)((w, ct) => w << ct        <<
                                               Boundary.CRLF) << Boundary.CRLF

    Task { Entity(fd.content.body.map { body => headers.toByteVector() ++ body }) }
  }
}

object partEncoder extends EntityEncoder[Part] {

  def headers: Headers = Headers.empty

  def toEntity(p: Part): Task[Entity] = p match {
    case fd: FormData => formDataEncoder.toEntity(fd)
  }

}

object MultipartEntityEncoder extends EntityEncoder[Multipart] {

  //TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: Multipart): Task[Entity] = {
    
  val dash             = "--"
  def transportPadding = {
    val rand = new Random()
    val lws = " \t"
    
    def nextChar = lws(rand.nextInt(2))
    def stream: Stream[Char] = Stream continually (nextChar)
    // The BNF is zero or more
    (stream take rand.nextInt(10)).mkString
  } 
  
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
                     delimiter(boundary)               <<
                     transportPadding                  <<
                     Boundary.CRLF         toByteVector()
                     
  val end:           Boundary => ByteVector = boundary =>
                     ByteVectorWriter()                <<
                     closeDelimiter(boundary)          <<
                     transportPadding                  <<
                     Boundary.CRLF         toByteVector()

  val encapsulation: Boundary => String =     boundary =>
                     new StringWriter()                <<
                     delimiter(boundary)               <<
                     transportPadding                  <<
                     Boundary.CRLF               result()    

    val _start         = Entity(Process.emit(start(mp.boundary)))
    val _end           = Entity(Process.emit(end(mp.boundary)))
    val _encapsulation = Entity(Process.emit(ByteVector(encapsulation(mp.boundary).getBytes)))
    
    val appendPart: (Task[Entity], Part) => Task[Entity] = { (a, p) =>
      for {
        acc <- a
        ff  <- partEncoder.toEntity(p)
      } yield entityInstance.append(acc, entityInstance.append(_encapsulation, ff))
    }

    val parts = mp.parts match {
      case x :: xs => xs.foldLeft(partEncoder.toEntity(x))(appendPart)
    }

    parts.
      map(entityInstance.append(_start, _)).
      map(entityInstance.append(_, _end))
  }


}
