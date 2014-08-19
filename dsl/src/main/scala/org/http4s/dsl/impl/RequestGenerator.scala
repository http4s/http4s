package org.http4s.dsl.impl

import scalaz.concurrent.Task

import org.http4s.Writable.Entity
import org.http4s._

trait RequestGenerator extends Any {
  def method: Method
}

trait EmptyRequestGenerator extends Any with RequestGenerator {
  /** Make a [[org.http4s.Request]] using this Method */
  def apply(uri: Uri): Task[Request] = Task.now(Request(method, uri))
}

trait EntityRequestGenerator extends Any with EmptyRequestGenerator {
  /** Make a [[org.http4s.Request]] using this Method */
  def apply[A](uri: Uri, body: A)(implicit w: Writable[A]): Task[Request] = {
    Task.now(Request(method, uri))
    var h = w.headers
    w.toEntity(body).flatMap { case Entity(proc, len) =>
      len foreach { l => h = h put Header.`Content-Length`(l) }
      Task.now(Request(method = method, uri = uri, headers = h, body = proc))
    }
  }
}
