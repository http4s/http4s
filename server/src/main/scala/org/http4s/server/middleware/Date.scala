package org.http4s.server.middleware

import cats._
import cats.data.Kleisli
import cats.implicits._
import cats.effect._

import org.http4s._
import org.http4s.headers.{Date => HDate}
import scala.concurrent.duration.MILLISECONDS

import java.time.Instant

object Date {
  def apply[G[_]: FlatMap: Clock, F[_], A](
      k: Kleisli[G, A, Response[F]]): Kleisli[G, A, Response[F]] =
    Kleisli { a =>
      for {
        resp <- k(a)
        nowInstant <- Clock[G]
          .realTime(MILLISECONDS)
          .map(Instant.ofEpochMilli(_))
      } yield {
        // Starting on January 1,n 10000, this will throw an exception.
        // The author intends to leave this problem for future generations.
        val date = resp.headers
          .get(HDate)
          .getOrElse(HDate(HttpDate.unsafeFromInstant(nowInstant)))

        resp.putHeaders(date)
      }
    }
}
