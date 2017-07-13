package org.http4s
package dsl
package impl

import fs2._
import org.http4s.headers._

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
  * While it is possible to construct the [[org.http4s.Response]] manually, the EntityResponseGenerators
  * offer shortcut syntax to make intention clear and concise.
  *
  * @example {{{
  * val resp: Task[Response] = Ok("Hello world!")
  * }}}
  */
trait EntityResponseGenerator extends Any with EmptyResponseGenerator {
  def apply[A](body: A)(implicit w: EntityEncoder[A]): Task[Response] =
    apply(body, Headers.empty)(w)

  def apply[A](body: A, headers: Headers)(implicit w: EntityEncoder[A]): Task[Response] = {
    val h = w.headers ++ headers
    w.toEntity(body).flatMap { case Entity(proc, len) =>
      val headers = len.map { l => `Content-Length`.fromLong(l).fold(_ => h, c => h put c) }.getOrElse(h)
      Task.now(Response(status = status, headers = headers, body = proc))
    }
  }
}

trait LocationResponseGenerator extends Any with ResponseGenerator {
  def apply(location: Uri): Task[Response] = Task.now(Response(status).putHeaders(Location(location)))
}

trait WwwAuthenticateResponseGenerator extends Any with ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*): Task[Response] =
    Task.now(Response(status).putHeaders(`WWW-Authenticate`(challenge, challenges: _*)))
}

trait ProxyAuthenticateResponseGenerator extends Any with ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*): Task[Response] =
    Task.now(Response(status).putHeaders(`Proxy-Authenticate`(challenge, challenges: _*)))
}
