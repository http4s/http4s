package org.http4s
package client
package dsl

import cats.Applicative
import org.http4s.Method.{NoBody, PermitsBody}
import org.http4s.client.impl.{EmptyRequestGenerator, EntityRequestGenerator}

trait Http4sClientDsl[F[_]] {
  import Http4sClientDsl._

  implicit def http4sWithBodySyntax(method: Method with PermitsBody): WithBodyOps[F] =
    new WithBodyOps[F](method)

  implicit def http4sNoBodyOps(method: Method with NoBody): NoBodyOps[F] =
    new NoBodyOps[F](method)

  implicit def http4sHeadersDecoder[T](
      implicit F: Applicative[F],
      decoder: EntityDecoder[F, T]): EntityDecoder[F, (Headers, T)] = {
    val s = decoder.consumes.toList
    EntityDecoder.decodeBy(s.head, s.tail: _*)(resp =>
      decoder.decode(resp, strict = true).map(t => (resp.headers, t)))
  }
}

object Http4sClientDsl {

  /** Syntax classes to generate a request directly from a [[Method]] */
  implicit class WithBodyOps[F[_]](val method: Method with PermitsBody)
      extends AnyVal
      with EntityRequestGenerator[F]
  implicit class NoBodyOps[F[_]](val method: Method with NoBody)
      extends AnyVal
      with EmptyRequestGenerator[F]
}
