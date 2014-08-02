package org.http4s.client

import com.typesafe.scalalogging.Logging
import org.http4s.client.Client.BadResponse
import org.http4s._

import scala.util.control.NoStackTrace
import scalaz.concurrent.Task


trait Client { self: Logging =>

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  def prepare(req: Request): Task[Response]

  /** Prepare a single request
    * @param req `Task[Request]` containing the headers, URI, etc
    * @return Task which will generate the Response
    */
  final def prepare(req: Task[Request]): Task[Response] = req.flatMap(prepare(_))

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  final def request[A](req: Task[Request])(onResponse: PartialFunction[Status, EntityDecoder[A]]): Task[A] =
    req.flatMap(req => request(req)(onResponse))

  final def request[A](req: Request)(onResponse: PartialFunction[Status, EntityDecoder[A]]): Task[A] =
    prepare(req).flatMap { resp =>
      onResponse
        .andThen(_.apply(resp))
        .applyOrElse(resp.status, {status: Status => Task.fail(BadResponse(status, "Invalid status")) })
    }
}

object Client {
  
  case class BadResponse(status: Status, msg: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Bad Response, $status: '$msg'"
  }
}
