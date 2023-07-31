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

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Concurrent
import cats.implicits._
import cats.~>
import org.http4s._

/** Middleware for caching the request body for multiple compilations
  *
  * As the body of the request is the [[fs2.Stream]] of bytes,
  * compiling it several times (e.g. with middlewares) is unsafe.
  * This middleware forbids such behaviour, compiling the body only once.
  * It does so only for the "inner" middlewares:
  *
  * {{{
  * val route = AMiddleware(BodyCache(SomeOtherMiddleware(myRoute)))
  * }}}
  *
  * In this example only `myRoute` & `SomeOtherMiddleware` will receive cached request body,
  * while the `AMiddleware` will get the raw one.
  *
  * As the entire request body will be allocated in memory,
  * there is a possibility of OOM error with a large body.
  * Because of that, using the `EntityLimiter` middleware is strongly advised.
  *
  * @note This middleware has nothing to do with the HTTP caching mechanism
  *       and it does not cache bodies between multiple requests.
  */
object BodyCache {

  def apply[G[_]: Concurrent, F[_]: Concurrent, R](
      old: Kleisli[G, R, Response[F]]
  )(reqGet: R => Request[F], reqSet: R => Request[F] => R)(
      lift: F ~> G
  ): Kleisli[G, R, Response[F]] = Kleisli {
    case req if hasNoBody(reqGet(req)) => old(req)
    case req => lift(compileBody(reqGet(req))).flatMap(reqSet(req).andThen(old.run))
  }

  def httpRoutes[F[_]: Concurrent](routes: HttpRoutes[F]): HttpRoutes[F] =
    apply(routes)(identity, _ => identity)(OptionT.liftK)

  def contextRoutes[T, F[_]: Concurrent](routes: ContextRoutes[T, F]): ContextRoutes[T, F] =
    apply(routes)(_.req, in => cached => in.copy(req = cached))(OptionT.liftK)

  def httpApp[F[_]: Concurrent](app: HttpApp[F]): HttpApp[F] =
    apply(app)(identity, _ => identity)(FunctionK.id)

  private def compileBody[F[_]: Concurrent](req: Request[F]): F[Request[F]] =
    req.toStrict(None)

  def hasNoBody[F[_]](req: Request[F]): Boolean =
    req.contentLength.contains(0L)
}
