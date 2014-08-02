package org.http4s

import org.http4s.Header.`Content-Length`
import org.http4s.client.Client.Result

import scalaz.concurrent.Task

package object client {

  import Client.Result

  /** Some syntactic sugar for making requests */
  implicit class ClientSyntax(request: Task[Request]) {

    def build(implicit client: Client): Task[Response] = client.prepare(request)

    def withbody[A](body: A)(implicit w: Writable[A]): Task[Request] = request.flatMap { req =>
      w.toEntity(body).map { e =>
        val headers = e.length match {
          case Some(i) => req.headers ++ w.headers :+ `Content-Length`(i)
          case None    => req.headers ++ w.headers
        }
        req.copy(headers = headers, body = e.body)
      }
    }

    def on[T](status: Status)(decoder: EntityDecoder[T])(implicit client: Client): Task[Result[T]] =
      client.request(request){ case s: Status if s == status => decoder }

    def decode[T](pf: PartialFunction[Status, EntityDecoder[T]])(implicit client: Client): Task[Result[T]] =
      client.request(request)(pf)
  }
}
