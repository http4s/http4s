package org.http4s.client

import org.http4s._

import scalaz.concurrent.Task


trait Client {

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  def prepare(req: Request): Task[Response]

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  final def apply(req: Request): Task[Response] = prepare(req)

  /** Prepare a single GET request
    * @param req [[Uri]] of the request
    * @return Task which will generate the Response
    */
  final def prepare(req: Uri): Task[Response] =
    prepare(Request(uri = req))

  /** Prepare a single GET request
    * @param req [[Uri]] of the request
    * @return Task which will generate the Response
    */
  final def apply(req: Uri): Task[Response] = prepare(req)

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
  final def apply(req: Task[Request]): Task[Response] = prepare(req)
}
