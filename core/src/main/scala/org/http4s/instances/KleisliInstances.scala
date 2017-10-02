package org.http4s
package instances

import cats._
import cats.data._
import cats.implicits._

trait KleisliInstances {

  implicit def http4sKleisliSemigroupK[F[_]: SemigroupK, A]: SemigroupK[Kleisli[F, A, ?]] =
    new SemigroupK[Kleisli[F, A, ?]] {
      def combineK[B](x: Kleisli[F, A, B], y: Kleisli[F, A, B]) =
        Kleisli(req => x(req) <+> y(req))
    }

}
