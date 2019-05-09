package org.http4s

import cats.{Applicative, Functor}
import cats.syntax.functor._

object DecodeResult {

  def success[F[_], A](a: F[A])(implicit F: Functor[F]): DecodeResult[F, A] =
    a.map(Right(_))

  def success[F[_], A](a: A)(implicit F: Applicative[F]): DecodeResult[F, A] =
    F.pure(Right(a))

  def failure[F[_], A](e: F[DecodeFailure])(implicit F: Functor[F]): DecodeResult[F, A] =
    e.map(Left(_))

  def failure[F[_], A](e: DecodeFailure)(implicit F: Applicative[F]): DecodeResult[F, A] =
    F.pure(Left(e))
}
