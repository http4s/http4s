package org.http4s.server.middleware

import cats._
import cats.data.Kleisli
import cats.implicits._
import cats.effect._

import org.http4s._
import org.http4s.headers.{Date => HDate}
import scala.concurrent.duration.MILLISECONDS

import java.time.Instant

/**
  * Date Middleware, adds the Date Header to All Responses generated
  * by the service.
 **/
object Date {
  def apply[G[_]: Monad: Clock, F[_], A](
      k: Kleisli[G, A, Response[F]]): Kleisli[G, A, Response[F]] =
    Kleisli { a =>
      for {
        resp <- k(a)
        header <- resp.headers
          .get(HDate)
          .fold(
            Clock[G]
              .realTime(MILLISECONDS)
              .map(Instant.ofEpochMilli(_))
              .map(nowInstant => HDate(HttpDate.unsafeFromInstant(nowInstant)))
            // Starting on January 1,n 10000, this will throw an exception.
            // The author intends to leave this problem for future generations.
          )(_.pure[G])

      } yield resp.putHeaders(header)
    }
}
