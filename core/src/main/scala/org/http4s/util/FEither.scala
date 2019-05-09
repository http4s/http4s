package org.http4s.util

import cats.{Functor, Monad}

object FEither {

  implicit class FEitherOps[F[_], L, R](val felr: F[Either[L, R]]) extends AnyVal {

    def swap(implicit F: Functor[F]): F[Either[R, L]] = F.map(felr)(_.swap)

    def rightMap[RR](f: R => RR)(implicit F: Functor[F]): F[Either[L, RR]] =
      F.map(felr) {
        case Right(r) => Right(f(r))
        case Left(l) => Left(l)
      }

    def leftMap[LL](f: L => LL)(implicit F: Functor[F]): F[Either[LL, R]] =
      F.map(felr) {
        case Right(r) => Right(r)
        case Left(l) => Left(f(l))
      }

    def rightTransform[RR](f: R => Either[L, RR])(implicit F: Functor[F]): F[Either[L, RR]] =
      F.map(felr) {
        case Right(r) => f(r)
        case Left(l) => Left(l)
      }

    def leftWiden[LL >: L]: F[Either[LL, R]] = felr.asInstanceOf[F[Either[LL, R]]]

    def rightFlatMap[RR](f: R => F[Either[L, RR]])(implicit F: Monad[F]): F[Either[L, RR]] =
      F.flatMap(felr) {
        case Right(r) => f(r)
        case Left(l) => F.pure(Left(l))
      }

    def leftFlatMap[LL](f: L => F[Either[LL, R]])(implicit F: Monad[F]): F[Either[LL, R]] =
      F.flatMap(felr) {
        case Right(r) => F.pure(Right(r))
        case Left(l) => f(l)
      }

    def semiflatMap[RR](f: R => F[RR])(implicit F: Monad[F]): F[Either[L, RR]] =
      F.flatMap(felr) {
        case Right(r) => F.map(f(r))(Right(_))
        case Left(l) => F.pure(Left(l))
      }
  }

}
