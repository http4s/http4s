package org.http4s
package syntax

import cats._
import cats.data.EitherT
import cats.implicits._

trait EffectMessageSyntax[F[_], M <: Message[F]] extends Any with MessageOps[F] {
  type Self = F[M#Self]

  def self: F[M]

  def transformHeaders(f: Headers => Headers)(implicit F: Functor[F]): Self =
    self.map(_.transformHeaders(f))

  def withBody[T](b: T)(implicit F: Monad[F], w: EntityEncoder[F, T]): Self =
    self.flatMap(_.withBody(b).widen[M#Self])

  override def withAttribute[A](key: AttributeKey[A], value: A)(implicit F: Functor[F]): Self =
    self.map(_.withAttribute(key, value))

  override def attemptAs[T](
      implicit F: FlatMap[F],
      decoder: EntityDecoder[F, T]): DecodeResult[F, T] =
    EitherT(self.flatMap { msg =>
      decoder.decode(msg, false).value
    })
}
