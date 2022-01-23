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

package org.http4s.server.middleware

import cats.data._
import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.server._

/** Middlewares for tracking the quantity of concurrent requests.
  *
  * These are generalized middlewares and can be used to implement metrics,
  * logging, max concurrent requests, etc.
  *
  * @note The concurrent request count is decremented on the completion of the
  *       Response body, or in the event of any error, and is guaranteed to
  *       only occur once.
  */
object ConcurrentRequests {

  /** Run a side effect each time the concurrent request count increments and
    * decrements.
    *
    * @note Each side effect is given the current number of concurrent
    *       requests as an argument.
    *
    * @note `onIncrement` should never be < 1 and `onDecrement` should never
    *       be value < 0.
    *
    * @note This is the same as [[route]], but allows for the inner and outer
    *       effect types to differ.
    */
  def route2[F[_]: Sync, G[_]: Async]( // TODO (ce3-ra): Sync + MonadCancel
      onIncrement: Long => G[Unit],
      onDecrement: Long => G[Unit],
  ): F[ContextMiddleware[G, Long]] =
    Ref
      .in[F, G, Long](0L)
      .map(ref =>
        BracketRequestResponse.bracketRequestResponseRoutes[G, Long](
          ref.updateAndGet(_ + 1L).flatTap(onIncrement)
        )(Function.const(ref.updateAndGet(_ - 1L).flatMap(onDecrement)))
      )

  /** Run a side effect each time the concurrent request count increments and
    * decrements.
    *
    * @note Each side effect is given the current number of concurrent
    *       requests as an argument.
    *
    * @note `onIncrement` should never be < 1 and `onDecrement` should never
    *       be value < 0.
    */
  def route[F[_]: Async](
      onIncrement: Long => F[Unit],
      onDecrement: Long => F[Unit],
  ): F[ContextMiddleware[F, Long]] =
    route2[F, F](onIncrement, onDecrement)

  /** As [[route]], but runs the same effect on increment and decrement of the concurrent request count. */
  def onChangeRoute[F[_]: Async](onChange: Long => F[Unit]): F[ContextMiddleware[F, Long]] =
    route[F](onChange, onChange)

  /** Run a side effect each time the concurrent request count increments and
    * decrements.
    *
    * @note Each side effect is given the current number of concurrent
    *       requests as an argument.
    *
    * @note `onIncrement` should never be < 1 and `onDecrement` should never
    *       be value < 0.
    *
    * @note This is the same as [[app]], but allows for the inner and outer
    *       effect types to differ.
    */
  def app2[F[_]: Sync, G[_]: Async](
      onIncrement: Long => G[Unit],
      onDecrement: Long => G[Unit],
  ): F[Kleisli[G, ContextRequest[G, Long], Response[G]] => Kleisli[G, Request[G], Response[G]]] =
    Ref
      .in[F, G, Long](0L)
      .map(ref =>
        BracketRequestResponse.bracketRequestResponseApp[G, Long](
          ref.updateAndGet(_ + 1L).flatTap(onIncrement)
        )(Function.const(ref.updateAndGet(_ - 1L).flatMap(onDecrement)))
      )

  /** Run a side effect each time the concurrent request count increments and
    * decrements.
    *
    * @note Each side effect is given the current number of concurrent
    *       requests as an argument.
    *
    * @note `onIncrement` should never be < 1 and `onDecrement` should never
    *       be value < 0.
    */
  def app[F[_]: Async](
      onIncrement: Long => F[Unit],
      onDecrement: Long => F[Unit],
  ): F[Kleisli[F, ContextRequest[F, Long], Response[F]] => Kleisli[F, Request[F], Response[F]]] =
    app2[F, F](onIncrement, onDecrement)

  /** As [[app]], but runs the same effect on increment and decrement of the concurrent request count. */
  def onChangeApp[F[_]: Async](
      onChange: Long => F[Unit]
  ): F[Kleisli[F, ContextRequest[F, Long], Response[F]] => Kleisli[F, Request[F], Response[F]]] =
    app[F](onChange, onChange)
}
