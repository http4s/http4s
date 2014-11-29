package org.http4s.client

import org.http4s.{Request, Response, EntityDecoder, Uri}

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
    Uri.fromString(req).fold(f => Task.fail(new org.http4s.ParseException(f)), prepare)

  /** Prepare a single request
    * @param req `Task[Request]` containing the headers, URI, etc
    * @return Task which will generate the Response
    */
  final def prepare(req: Task[Request]): Task[Response] =
    req.flatMap(prepare)
}

object Client {
  def toResult[A](resp: Task[Response], onResponse: Response => Task[Result[A]]): Task[Result[A]]=
    for {
      r <- resp
      res <-onResponse(r)
    } yield res

  def withDecoder[A](resp: Task[Response], onResponse: Response => EntityDecoder[A]): Task[Result[A]] =
    toResult(resp,  resp => onResponse(resp)
                              .apply(resp)
                              .map(Result(resp.status, resp.headers, _))
    )
}
