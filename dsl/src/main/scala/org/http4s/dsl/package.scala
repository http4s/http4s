package org.http4s

import scalaz.concurrent.Task

import org.http4s.Status.{HeaderRequired, NoConstraints, EntityProhibited}
import org.http4s.Writable.Entity

package object dsl extends Http4s with Http4sConstants {

  implicit final class MethodSyntax(val self: Method) extends AnyVal {
    /** Make a [[org.http4s.Request]] using this Method */
    def apply(uri: Uri): Task[Request] = Task.now(Request(self, uri))

  }

  def notFound(req: Request): Task[Response] = ResponseBuilder.notFound(req)

  /** Helper for the generation of a [[org.http4s.Response]] which will not contain a body
    *
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = Status.Continue()
    * }}}
    */
  implicit class EntityProhibitedResponseGenerator(status: Status with EntityProhibited) {
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
    */
  implicit class UnconstrainedResponseGenerator(status: Status with NoConstraints) {
    def apply(): Task[Response] = Task.now(Response(status))

    def apply[A](body: A)(implicit w: Writable[A]): Task[Response] =
      apply(body, w.headers)(w)

    def apply[A](body: A, headers: Headers)(implicit w: Writable[A]): Task[Response] = {
      var h = headers ++ w.headers
      w.toEntity(body).flatMap { case Entity(proc, len) =>
        len foreach { l => h = h put Header.`Content-Length`(l) }
        Task.now(Response(status = status, headers = h, body = proc))
      }
    }
  }

  /** Helper for the generation of a [[org.http4s.Response]] which have a required header.
    *
    * The RequiredHeaderResponseGenerator aids in adding the appropriate headers for certain statuses.
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = MovedPermanently(uri("http://foo.com"))
    * }}}
    */
  implicit class RequiredHeaderResponseGenerator[H](self: Status with HeaderRequired[H]) {
    def apply[A](requiredHeaderValue: H): Task[Response] = apply(requiredHeaderValue, Headers.empty)

    def apply[A](requiredHeaderValue: H, headers: Headers): Task[Response] = {
      var h = headers.put(self.mkRequiredHeader(requiredHeaderValue))
      Task.now(Response(status = self, headers = h))
    }

    def apply[A](requiredHeaderValue: H, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] = {
      var h = headers.put(self.mkRequiredHeader(requiredHeaderValue)) ++ w.headers
      w.toEntity(body).flatMap { case Entity(proc, len) =>
        len foreach { l => h = h put Header.`Content-Length`(l) }
        Task.now(Response(status = self, headers = h, body = proc))
      }
    }
  }
}
