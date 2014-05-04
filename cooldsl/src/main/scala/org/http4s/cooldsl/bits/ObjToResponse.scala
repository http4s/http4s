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
}

object ObjToResponse {
  implicit val taskResponse = new ObjToResponse[Task[Response]] {
    override def apply(o: Task[Response]): Task[Response] = o
  }

  implicit def writableResponse[O](implicit w: Writable[O]) = new ObjToResponse[O] {
    override def apply(o: O): Task[Response] = w.toBody(o).map {
      case (body, Some(i)) => Response(Status.Ok, Headers(Header.`Content-Length`(i)), body)
      case (body, None)    => Response(Status.Ok, Headers.empty, body)
    }
  }
}