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

package org.http4s.server
package middleware

import cats.data.{Kleisli, OptionT}
import cats._
import cats.syntax.all._
import org.http4s._

object ErrorAction {
  def apply[F[_]: ApplicativeError[*[_], Throwable], G[_], B](
      k: Kleisli[F, Request[G], B],
      f: (Request[G], Throwable) => F[Unit]
  ): Kleisli[F, Request[G], B] =
    Kleisli { req =>
      k.run(req).onError { case e => f(req, e) }
    }

  def log[F[_]: ApplicativeError[*[_], Throwable], G[_], B](
      http: Kleisli[F, Request[G], B],
      messageFailureLogAction: (Throwable, => String) => F[Unit],
      serviceErrorLogAction: (Throwable, => String) => F[Unit]
  ): Kleisli[F, Request[G], B] =
    apply(
      http,
      {
        case (req, mf: MessageFailure) =>
          messageFailureLogAction(
            mf,
            s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}"""
          )
        case (req, e) =>
          serviceErrorLogAction(
            e,
            s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}"""
          )
      }
    )

  object httpApp {
    def apply[F[_]: ApplicativeError[*[_], Throwable]](
        httpApp: HttpApp[F],
        f: (Request[F], Throwable) => F[Unit]): HttpApp[F] =
      ErrorAction(httpApp, f)

    def log[F[_]: ApplicativeError[*[_], Throwable], G[_], B](
        httpApp: HttpApp[F],
        messageFailureLogAction: (Throwable, => String) => F[Unit],
        serviceErrorLogAction: (Throwable, => String) => F[Unit]
    ): HttpApp[F] =
      ErrorAction.log(httpApp, messageFailureLogAction, serviceErrorLogAction)
  }

  object httpRoutes {
    def apply[F[_]: MonadError[*[_], Throwable]](
        httpRoutes: HttpRoutes[F],
        f: (Request[F], Throwable) => F[Unit]): HttpRoutes[F] =
      ErrorAction(httpRoutes, liftFToOptionT(f))

    def log[F[_]: MonadError[*[_], Throwable]](
        httpRoutes: HttpRoutes[F],
        messageFailureLogAction: (Throwable, => String) => F[Unit],
        serviceErrorLogAction: (Throwable, => String) => F[Unit]
    ): HttpRoutes[F] =
      ErrorAction.log(
        httpRoutes,
        liftFToOptionT(messageFailureLogAction),
        liftFToOptionT(serviceErrorLogAction)
      )

    private def liftFToOptionT[F[_]: Functor, A, B, C](
        f: Function2[A, B, F[C]]): Function2[A, B, OptionT[F, C]] =
      (a: A, b: B) => OptionT.liftF(f(a, b))
  }
}
