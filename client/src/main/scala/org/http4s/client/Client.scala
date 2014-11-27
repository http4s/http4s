package org.http4s.client

import org.http4s._

import scala.util.control.NoStackTrace
import scalaz.concurrent.Task


trait Client {

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  def prepare(req: Request): Task[Response]

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  /** Prepare a single GET request
    * @param req [[Uri]] of the request
    * @return Task which will generate the Response
    */
  final def prepare(req: Uri): Task[Response] =
    prepare(Request(uri = req))

  /** Prepare a single GET request
    * @param req `String` uri of the request
    * @return Task which will generate the Response
    */
  final def prepare(req: String): Task[Response] =
    Uri.fromString(req)
       .fold(f => Task.fail(new org.http4s.ParseException(f)),prepare)

  /** Prepare a single request
    * @param req `Task[Request]` containing the headers, URI, etc
    * @return Task which will generate the Response
    */
  final def prepare(req: Task[Request]): Task[Response] =
    req.flatMap(prepare)
}

object Client {
  case class Result[T](status: Status, headers: Headers, body: T)
  
  case class BadResponse(status: Status, msg: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Bad Response, $status: '$msg'"
  }

  def decode[A](resp: Task[Response])(onResponse: Response => EntityDecoder[A]): Task[Result[A]] =
    resp.flatMap { resp =>
      onResponse(resp)
        .apply(resp)
        .map(Result(resp.status, resp.headers, _))
    }
}
