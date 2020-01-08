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
  def apply[G[_]: MonadError[*[_], Throwable]: Clock, F[_], A](
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
              .flatMap(nowInstant => HttpDate.fromInstant(nowInstant).liftTo[G])
              .map(nowHttpDate => HDate(nowHttpDate))
          )(_.pure[G])

      } yield resp.putHeaders(header)
    }
}
