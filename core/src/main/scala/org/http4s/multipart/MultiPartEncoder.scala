package org.http4s
package multipart

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
    lazy val headers = fd.contentType.foldLeft(ByteVectorWriter() <<
                                               contentDisposition(fd.name.value) <<
                                               B.CRLF)((w, ct) => w << ct <<
                                               B.CRLF) << B.CRLF

    Task { Entity(fd.content.body.map { body => headers.toByteVector() ++ body }) }
  }
}

object partEncoder extends EntityEncoder[Part] {

  def headers: Headers = Headers.empty

  def toEntity(p: Part): Task[Entity] = p match {
    case fd: FormData => formDataEncoder.toEntity(fd)
  }

}

object MultiPartEntityEncoder extends EntityEncoder[MultiPart] {

  //TODO: Refactor encoders to create headers dependent on value.
  def headers: Headers = Headers.empty

  def toEntity(mp: MultiPart): Task[Entity] = {

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
