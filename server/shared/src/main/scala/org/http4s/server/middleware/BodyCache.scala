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

import cats.data.{Kleisli, OptionT}
import cats.effect.Concurrent
import org.http4s.{ContextRequest, ContextRoutes, HttpApp, HttpRoutes, Request}
import fs2.Stream
import cats.implicits._

/** Middleware for caching the request body for multiple compilations
  *
  * As the body of the request is the [[Stream]] of bytes,
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
  * @note This middleware has nothing to do with the HTTP caching mechanism
  *       and it does not cache bodies between multiple requests.
  */
object BodyCache {

  def httpRoutes[F[_]: Concurrent](routes: HttpRoutes[F]): HttpRoutes[F] =
    Kleisli {
      case req if hasNoBody(req) => routes(req)
      case req => OptionT.liftF(compileBody(req)).flatMap(routes.apply)

    }

  def contextRoutes[T, F[_]: Concurrent](routes: ContextRoutes[T, F]): ContextRoutes[T, F] =
    Kleisli {
      case cr @ ContextRequest(_, req) if req.contentLength.contains(0L) => routes(cr)
      case cr @ ContextRequest(_, req) =>
        OptionT.liftF(compileBody(req)).flatMap(cachedReq => routes(cr.copy(req = cachedReq)))
    }

  def httpApp[F[_]: Concurrent](app: HttpApp[F]): HttpApp[F] =
    Kleisli {
      case req if req.contentLength.contains(0L) => app(req)
      case req => compileBody(req).flatMap(app.apply)
    }

  private def compileBody[F[_]: Concurrent](req: Request[F]): F[Request[F]] = for {
    body <- req.body.compile.toList
    cachedReq = req.withBodyStream(Stream.emits(body))
  } yield cachedReq

  private def hasNoBody[F[_]](req: Request[F]) =
    req.contentLength.contains(0L)
}
