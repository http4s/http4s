package org.http4s.client

import com.typesafe.scalalogging.Logging
import org.http4s.client.Client.{Result, BadResponse}
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

  /** Generate a Task which, when executed, will perform the request and decode the result */
  final def decode[A](req: Task[Request])(onResponse: Response => EntityDecoder[A]): Task[Result[A]] =
    req.flatMap(req => decode(req)(onResponse))

  /** Generate a Task which, when executed, will perform the request and decode the result */
  final def decode[A](req: Request)(onResponse: Response => EntityDecoder[A]): Task[Result[A]] =
    prepare(req).flatMap { resp =>
      onResponse(resp)
        .apply(resp)
        .map(Result(resp.status, resp.headers, _))
    }
}

object Client {
  case class Result[T](status: Status, headers: Headers, body: T)
  
  case class BadResponse(status: Status, msg: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Bad Response, $status: '$msg'"
  }
}
