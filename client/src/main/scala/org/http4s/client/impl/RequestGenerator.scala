package org.http4s.client.impl

import cats._
import org.http4s._
import org.http4s.headers.`Content-Length`

sealed trait RequestGenerator extends Any {
  def method: Method
}

trait EmptyRequestGenerator[F[_]] extends Any with RequestGenerator {

  /** Make a [[org.http4s.Request]] using this [[Method]] */
  final def apply(uri: Uri, headers: Header*)(implicit F: Applicative[F]): F[Request[F]] =
    F.pure(Request(method, uri, headers = Headers(headers: _*)))
}

trait EntityRequestGenerator[F[_]] extends Any with EmptyRequestGenerator[F] {

  /** Make a [[org.http4s.Request]] using this Method */
  final def apply[A](body: A, uri: Uri, headers: Header*)(
      implicit F: Applicative[F],
      w: EntityEncoder[F, A]): F[Request[F]] = {
    val h = w.headers ++ headers
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Request(method = method, uri = uri, headers = newHeaders, body = entity.body))
  }
}
