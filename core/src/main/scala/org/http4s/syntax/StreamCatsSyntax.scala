package org.http4s.syntax

import cats._
import cats.implicits._
import fs2.Stream
import fs2.util.Free

trait StreamCatsSyntax {
  implicit def fs2StreamCatsSyntax[F[_], A](stream: Stream[F, A]): StreamCatsOps[F, A] =
    new StreamCatsOps[F, A](stream)
}

final class StreamCatsOps[F[_], A](val self: Stream[F, A]) extends AnyVal {

  def foldMap[B](f: A => B)(implicit M: Monoid[B]): Stream[F, B] =
    self.fold(M.empty)((b, a) => M.combine(b, f(a)))

  def foldMonoid(implicit M: Monoid[A]): Stream[F, A] =
    self.fold(M.empty)(M.combine)

  def foldSemigroup(implicit S: Semigroup[A]): Stream[F, A] =
    self.reduce(S.combine)

  def runFoldMapFree[B](f: A => B)(implicit M: Monoid[B]): Free[F, B] =
    self.runFoldFree(M.empty)((b, a) => M.combine(b, f(a)))

  def runGroupByFoldMapFree[K, B: Monoid](f: A => K)(g: A => B): Free[F, Map[K, B]] =
    runFoldMapFree(a => Map(f(a) -> g(a)))

  def runGroupByFoldMonoidFree[K](f: A => K)(implicit M: Monoid[A]): Free[F, Map[K, A]] =
    runFoldMapFree(a => Map(f(a) -> a))

  def runGroupByFree[K](f: A => K)(implicit M: Monoid[A]): Free[F, Map[K, Vector[A]]] = {
    implicit def vectorMonoid = new Monoid[Vector[A]] {
      def empty = Vector.empty[A]
      def combine(a: Vector[A], b: Vector[A]) = a ++ b
    }

    runGroupByFoldMapFree(f)(a => Vector(a))
  }

  def runFoldMap[B](f: A => B)(implicit F: MonadError[F, Throwable], M: Monoid[B]): F[B] =
    runFoldMapFree(f).run

  def runGroupByFoldMap[K, B: Monoid](f: A => K)(g: A => B)(implicit F: MonadError[F, Throwable]): F[Map[K, B]] =
    runGroupByFoldMapFree(f)(g).run

  def runGroupByFoldMonoid[K](f: A => K)(implicit F: MonadError[F, Throwable], M: Monoid[A]): F[Map[K, A]] =
    runGroupByFoldMonoidFree(f).run

  def runGroupBy[K](f: A => K)(implicit F: MonadError[F, Throwable], M: Monoid[A]): F[Map[K, Vector[A]]] =
    runGroupByFree(f).run

}
