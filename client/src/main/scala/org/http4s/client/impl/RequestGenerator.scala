package org.http4s.client.impl

import org.http4s.EntityEncoder
import org.http4s._
import org.http4s.headers.`Content-Length`
import fs2.Task

sealed trait RequestGenerator extends Any {
  def method: Method
}

trait EmptyRequestGenerator extends Any with RequestGenerator {
  /** Make a [[org.http4s.Request]] using this [[Method]] */
  final def apply(uri: Uri): Task[Request] = Task.now(Request(method, uri))
}

trait EntityRequestGenerator extends Any with EmptyRequestGenerator {
  /** Make a [[org.http4s.Request]] using this Method */
  final def apply[A](uri: Uri, body: A)(implicit w: EntityEncoder[A]): Task[Request] = {
    val h = w.headers
    w.toEntity(body).flatMap { case Entity(proc, len) =>
      val headers = len.map { l => `Content-Length`(l).fold(_ => h, c => h put c) }.getOrElse(h)
      Task.now(Request(method = method, uri = uri, headers = headers, body = proc))
    }
  }
}
