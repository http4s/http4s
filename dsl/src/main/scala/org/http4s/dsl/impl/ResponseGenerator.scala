package org.http4s
package dsl
package impl

import scalaz.concurrent.Task

import org.http4s.Writable.Entity

trait ResponseGenerator {
  def status: Status
}

/**
 *  Helper for the generation of a [[org.http4s.Response]] which will not contain a body
 *
 * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
 * offer shortcut syntax to make intention clear and concise.
 *
 * @example {{{
 * val resp: Task[Response] = Status.Continue()
 * }}}
 */
trait EmptyResponseGenerator extends ResponseGenerator {
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
trait EntityResponseGenerator extends EmptyResponseGenerator {
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

trait LocationResponseGenerator extends ResponseGenerator {
  def apply(location: Uri): Task[Response] = Task.now(Response(status).putHeaders(Header.Location(location)))
}

trait WwwAuthenticateResponseGenerator extends ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*): Task[Response] =
    Task.now(Response(status).putHeaders(Header.`WWW-Authenticate`(challenge, challenges: _*)))
}

trait ProxyAuthenticateResponseGenerator extends ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*): Task[Response] =
    Task.now(Response(status).putHeaders(Header.`Proxy-Authenticate`(challenge, challenges: _*)))
}
