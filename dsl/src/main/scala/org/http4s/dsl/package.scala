package org.http4s

import org.http4s.Status.{RedirectResponder, EntityResponse, NoEntityResponse}
import org.http4s.Writable.Entity

import java.net.{URL, URI}

import scalaz.concurrent.Task

package object dsl {
  val OPTIONS = Method.Options
  val GET = Method.Get
  val HEAD = Method.Head
  val POST = Method.Post
  val PUT = Method.Put
  val DELETE = Method.Delete
  val TRACE = Method.Trace
  val CONNECT = Method.Connect
  val PATCH = Method.Patch

  def notFound(req: Request): Task[Response] = ResponseBuilder.notFound(req)

  /** Helper for the generation of a [[org.http4s.Response]] which will not contain a body
    *
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = Status.Continue()
    * }}}
    *
    * @see [[org.http4s.Status.EntityResponse]]
    */
  implicit class NoEntityResponseGenerator(status: Status with NoEntityResponse) {
    private[this] val StatusResponder = Response(status)
    def apply(): Task[Response] = Task.now(StatusResponder)
  }

  /** Helper for the generation of a [[org.http4s.Response]] which may contain a body
    *
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = Ok("Hello world!")
    * }}}
    *
    * @see [[NoEntityResponse]]
    */
  implicit class EntityResponseGenerator(status: Status with EntityResponse) {
    def apply(): Task[Response] = Task.now(Response(status))

    def apply[A](body: A)(implicit w: Writable[A]): Task[Response] =
      apply(body, w.headers)(w)

    def apply[A](body: A, headers: Headers)(implicit w: Writable[A]): Task[Response] = {
      var h = headers ++ w.headers
      w.toEntity(body).flatMap { case Entity(proc, len) =>
        len foreach { h +:= Header.`Content-Length`(_) }
        Task.now(Response(status = status, headers = h, body = proc))
      }
    }
  }

  /** Helper for the generation of a [[org.http4s.Response]] which points to another HTTP location
    *
    * The RedirectResponseGenerator aids in adding the appropriate headers for Redirect actions.
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = MovedPermanently("http://foo.com")
    * }}}
    *
    * @see [[NoEntityResponse]]
    */
  implicit class RedirectResponseGenerator(self: Status with RedirectResponder) {
    def apply(uri: String): Response = Response(status = self, headers = Headers(Header.Location(uri)))

    def apply(uri: URI): Response = apply(uri.toString)

    def apply(url: URL): Response = apply(url.toString)
  }
}
