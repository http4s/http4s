package org.http4s
package syntax

import cats._
import cats.implicits._

trait TaskRequestSyntax {
  implicit def http4sTaskRequestSyntax[F[+_]](req: F[Request[F]]): TaskRequestOps[F] =
    new TaskRequestOps[F](req)
}

final class TaskRequestOps[F[+_]](val self: F[Request[F]])
    extends AnyVal
    with TaskMessageOps[F, Request[F]]
    with RequestOps[F] {
  def decodeWith[A](decoder: EntityDecoder[F, A], strict: Boolean)(f: A => F[Response[F]])(implicit F: Monad[F]): F[Response[F]] =
    self.flatMap(_.decodeWith(decoder, strict)(f))

  def withPathInfo(pi: String)(implicit F: Functor[F]): F[Request[F]] =
    self.map(_.withPathInfo(pi))
}
