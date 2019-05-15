package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.effect.{Concurrent, Timer}
import cats.syntax.applicative._
import scala.concurrent.duration.FiniteDuration

object Timeout {

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response is canceled.
    *
    * @param timeout Finite duration to wait before returning a `500
    * Internal Server Error` response
    * @param service [[HttpRoutes]] to transform
    */
  def apply[F[_], G[_], A](timeout: FiniteDuration, timeoutResponse: F[Response[G]])(
      http: Kleisli[F, A, Response[G]])(
      implicit F: Concurrent[F],
      T: Timer[F]): Kleisli[F, A, Response[G]] =
    http.mapF(Concurrent.timeoutTo(_, timeout, timeoutResponse))

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response is canceled.
    *
    * @param timeout Finite duration to wait before returning a `500
    * Internal Server Error` response
    * @param service [[HttpRoutes]] to transform
    */
  def apply[F[_], G[_], A](timeout: FiniteDuration)(http: Kleisli[F, A, Response[G]])(
      implicit F: Concurrent[F],
      T: Timer[F]
  ): Kleisli[F, A, Response[G]] =
    apply(timeout, Response.timeout[G].pure[F])(http)
}
