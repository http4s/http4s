package org.http4s
package dsl
package impl

import scalaz.concurrent.Task

import org.http4s.Writable.Entity

trait ResponseGenerator extends Any {
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
trait EmptyResponseGenerator extends Any with ResponseGenerator {
  def apply(): Task[Response] = Task.now(Response(status))
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
trait EntityResponseGenerator extends Any with EmptyResponseGenerator {
  def apply[A](body: A)(implicit w: Writable[A]): Task[Response] =
    apply(body, Headers.empty)(w)

  def apply[A](body: A, headers: Headers)(implicit w: Writable[A]): Task[Response] = {
    var h = w.headers ++ headers
    w.toEntity(body).flatMap { case Entity(proc, len) =>
      len foreach { l => h = h put Header.`Content-Length`(l) }
      Task.now(Response(status = status, headers = h, body = proc))
    }
  }
}

trait LocationResponseGenerator extends Any with ResponseGenerator {
  def apply(location: Uri): Task[Response] = Task.now(Response(status).putHeaders(Header.Location(location)))
}

trait WwwAuthenticateResponseGenerator extends Any with ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*): Task[Response] =
    Task.now(Response(status).putHeaders(Header.`WWW-Authenticate`(challenge, challenges: _*)))
}

trait ProxyAuthenticateResponseGenerator extends Any with ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*): Task[Response] =
    Task.now(Response(status).putHeaders(Header.`Proxy-Authenticate`(challenge, challenges: _*)))
}
