package org.http4s.client.impl

import cats._
import cats.implicits._
import org.http4s._
import org.http4s.headers.`Content-Length`

sealed trait RequestGenerator extends Any {
  def method: Method
}

trait EmptyRequestGenerator[F[_]] extends Any with RequestGenerator {

  /** Make a [[org.http4s.Request]] using this [[Method]] */
  final def apply(uri: Uri)(implicit F: Applicative[F]): F[Request[F]] =
    F.pure(Request(method, uri))
}

trait EntityRequestGenerator[F[_]] extends Any with EmptyRequestGenerator[F] {

  /** Make a [[org.http4s.Request]] using this Method */
  final def apply[A](uri: Uri, body: A)(
      implicit F: Monad[F],
      w: EntityEncoder[F, A]): F[Request[F]] = {
    val h = w.headers
    w.toEntity(body).flatMap {
      case Entity(proc, len) =>
        val headers = len
          .map { l =>
            `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
          }
          .getOrElse(h)
        F.pure(Request(method = method, uri = uri, headers = headers, body = proc))
    }
  }
}
