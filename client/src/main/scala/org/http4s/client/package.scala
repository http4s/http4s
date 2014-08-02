package org.http4s

import org.http4s.Header.`Content-Length`

import scalaz.concurrent.Task

package object client {

  import Client.Result

  /** Some syntactic sugar for making requests */
  implicit class ClientSyntax(request: Task[Request]) {

    /** Generate a Task which, when executed, will perform the request using the provided client */
    def build(implicit client: Client): Task[Response] = client.prepare(request)

    /** Generate a Task which, when executed, will perform the request and if the response
      * is of type `status`, decodes it.
      */
    def on[T](status: Status)(decoder: EntityDecoder[T])(implicit client: Client): Task[Result[T]] =
      client.decode(request){ case s: Status if s == status => decoder }

    /** Generate a Task which, when executed, will perform the request and attempt to decode it */
    def decode[T](pf: PartialFunction[Status, EntityDecoder[T]])(implicit client: Client): Task[Result[T]] =
      client.decode(request)(pf)
  }
}
