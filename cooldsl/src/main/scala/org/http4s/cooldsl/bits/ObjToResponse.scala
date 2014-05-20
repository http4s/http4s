package org.http4s.cooldsl.bits

import scalaz.concurrent.Task
import org.http4s._
import scala.Some
import org.http4s.Response

/**
 * Created by Bryce Anderson on 5/4/14.
 */

trait ObjToResponse[O] {
  def apply(o: O): Task[Response]
  def mediaTypes: Seq[MediaType]
  def manifest: Option[Manifest[O]]
}

object ObjToResponse {
  implicit val taskResponse = new ObjToResponse[Task[Response]] {
    override def mediaTypes = Nil
    override def apply(o: Task[Response]): Task[Response] = o

    override def manifest: Option[Manifest[Task[Response]]] = None
  }

  implicit def writableResponse[O](implicit w: Writable[O], m: Manifest[O]) = new ObjToResponse[O] {

    override def manifest: Some[Manifest[O]] = Some(m)

    override def mediaTypes: Seq[MediaType] = w.contentType.mediaType::Nil

    override def apply(o: O): Task[Response] = w.toBody(o).map {
      case (body, Some(i)) => Response(Status.Ok, Headers(Header.`Content-Length`(i)), body)
      case (body, None)    => Response(Status.Ok, Headers.empty, body)
    }
  }
}