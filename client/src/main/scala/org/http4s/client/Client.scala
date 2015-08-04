package org.http4s.client

import org.http4s._
import org.http4s.headers.Accept

import scalaz.concurrent.Task

trait Client {

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  def prepare(req: Request): Task[Response]

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  final def apply(req: Request): Task[Response] = prepare(req)

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  final def prepAs[T](req: Request)(implicit d: EntityDecoder[T]): Task[T] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(m.head, m.tail:_*))
    } else req

    prepare(r).flatMap { resp =>
      d.decode(resp).fold(e => throw ParseException(e), identity)
    }
  }

  /////////////////////////////////////////////////////////////////////////

  /** Prepare a single GET request
    * @param uri [[Uri]] of the request
    * @return Task which will generate the Response
    */
  final def prepare(uri: Uri): Task[Response] =
    prepare(Request(uri = uri))

  /** Prepare a single GET request
    * @param uri [[Uri]] of the request
    * @return Task which will generate the Response
    */
  final def apply(uri: Uri): Task[Response] = prepare(uri)

  /** Prepare a single GET request
    * @param uri [[Uri]] of the request
    * @return Task which will generate the Response
    */
  final def prepAs[T](uri: Uri)(implicit d: EntityDecoder[T]): Task[T] =
    prepAs(Request(uri = uri))(d)

  /////////////////////////////////////////////////////////////////////////

  /** Prepare a single request
    * @param req `Task[Request]` containing the headers, URI, etc
    * @return Task which will generate the Response
    */
  final def prepare(req: Task[Request]): Task[Response] =
    req.flatMap(prepare)

  /** Prepare a single request
    * @param req `Task[Request]` containing the headers, URI, etc
    * @return Task which will generate the Response
    */
  final def apply(req: Task[Request]): Task[Response] =
    prepare(req)

  /** Prepare a single request
    * @param req `Task[Request]` containing the headers, URI, etc
    * @return Task which will generate the Response
    */
  final def prepAs[T](req: Task[Request])(implicit d: EntityDecoder[T]): Task[T] =
    req.flatMap(prepAs(_)(d))
}
