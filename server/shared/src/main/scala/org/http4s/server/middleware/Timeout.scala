/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.kernel.Temporal
import cats.syntax.applicative._

import scala.concurrent.duration.FiniteDuration

object Timeout {

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response is canceled.
    *
    * @note if the service runs uncancelable effects while responding
    * (e.g. if service uses [[cats.effect.kernel.MonadCancel#uncancelable MonadCancel#uncancelable]] under the hood) and has exceeded
    * the timeout, then the expected behavior is:
    * <ul>
    *   <li> uncancelable effects will be completed naturally (regardless of how long it takes), </li>
    *   <li> after that, the timeout response will be returned. </li>
    * </ul>
    * To get more insights on effect cancelation, dig into the [[cats.effect.kernel.MonadCancel MonadCancel]] documentation.
    *
    * @param timeout Finite duration to wait before returning the provided response
    */
  def apply[F[_], G[_], A](timeout: FiniteDuration, timeoutResponse: F[Response[G]])(
      http: Kleisli[F, A, Response[G]]
  )(implicit F: Temporal[F]): Kleisli[F, A, Response[G]] =
    http.mapF(F.timeoutTo(_, timeout, timeoutResponse))

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response is canceled.
    *
    * @note if the service runs uncancelable effects while responding
    * (e.g. if service uses [[cats.effect.kernel.MonadCancel#uncancelable MonadCancel#uncancelable]] under the hood) and has exceeded
    * the timeout, then the expected behavior is:
    * <ul>
    *   <li> uncancelable effects will be completed naturally (regardless of how long it takes), </li>
    *   <li> after that, the timeout response will be returned. </li>
    * </ul>
    * To get more insights on effect cancelation, dig into the [[cats.effect.kernel.MonadCancel MonadCancel]] documentation.
    *
    * @param timeout Finite duration to wait before returning
    * a `503 Service Unavailable` response
    */
  def apply[F[_], G[_], A](timeout: FiniteDuration)(http: Kleisli[F, A, Response[G]])(implicit
      F: Temporal[F]
  ): Kleisli[F, A, Response[G]] =
    apply(timeout, Response.timeout[G].pure[F])(http)

  /** This is the same as [[apply[F[_],G[_],A](timeout:scala\.concurrent\.duration\.FiniteDuration)*]], but for HttpRoutes */
  def httpRoutes[F[_]](timeout: FiniteDuration)(httpRoutes: HttpRoutes[F])(implicit
      F: Temporal[F]
  ): HttpRoutes[F] =
    apply(timeout)(httpRoutes)

  /** This is the same as [[apply[F[_],G[_],A](timeout:scala\.concurrent\.duration\.FiniteDuration,timeoutResponse*]], but for HttpRoutes */
  def httpRoutes[F[_]](timeout: FiniteDuration, timeoutResponse: F[Response[F]])(
      httpRoutes: HttpRoutes[F]
  )(implicit F: Temporal[F]): HttpRoutes[F] =
    apply(timeout, OptionT.liftF(timeoutResponse))(httpRoutes)

  /** This is the same as [[apply[F[_],G[_],A](timeout:scala\.concurrent\.duration\.FiniteDuration)*]], but for HttpApp */
  def httpApp[F[_]](timeout: FiniteDuration)(httpApp: HttpApp[F])(implicit
      F: Temporal[F]
  ): HttpApp[F] =
    apply(timeout)(httpApp)

  /** This is the same as [[apply[F[_],G[_],A](timeout:scala\.concurrent\.duration\.FiniteDuration,timeoutResponse*]], but for HttpApp */
  def httpApp[F[_]](timeout: FiniteDuration, timeoutResponse: F[Response[F]])(httpApp: HttpApp[F])(
      implicit F: Temporal[F]
  ): HttpApp[F] =
    apply(timeout, timeoutResponse)(httpApp)
}
