package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
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
      @deprecatedName('service) http: Kleisli[F, A, Response[G]])(
      implicit F: Concurrent[F],
      T: Timer[F]): Kleisli[F, A, Response[G]] =
    http
      .mapF(respF => F.race(respF, T.sleep(timeout) *> timeoutResponse))
      .map(_.merge)

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response is canceled.
    *
    * @param timeout Finite duration to wait before returning a `500
    * Internal Server Error` response
    * @param service [[HttpRoutes]] to transform
    */
  def apply[F[_], G[_], A](timeout: FiniteDuration)(
      @deprecatedName('service) http: Kleisli[F, A, Response[G]])(
      implicit F: Concurrent[F],
      T: Timer[F]
  ): Kleisli[F, A, Response[G]] =
    apply(timeout, Response.timeout[G].pure[F])(http)
}
