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

import cats._
import cats.data.Kleisli
import cats.syntax.all._
import org.http4s._
import org.http4s.headers._
import org.typelevel.ci._

object ErrorHandling {
  def apply[F[_], G[_]](
      k: Kleisli[F, Request[G], Response[G]]
  )(implicit F: MonadThrow[F]): Kleisli[F, Request[G], Response[G]] =
    Kleisli { req =>
      val pf: PartialFunction[Throwable, F[Response[G]]] =
        inDefaultServiceErrorHandler[F, G](F)(req)
      k.run(req).handleErrorWith { e =>
        pf.lift(e) match {
          case Some(resp) => resp
          case None => F.raiseError(e)
        }
      }
    }

  def httpRoutes[F[_]: MonadThrow](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  def httpApp[F[_]: MonadThrow](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp)

  object Custom {
    def recoverWith[F[_]: MonadThrow, G[_], A](
        http: Kleisli[F, A, Response[G]]
    )(pf: PartialFunction[Throwable, F[Response[G]]]): Kleisli[F, A, Response[G]] =
      Kleisli { (a: A) =>
        http.run(a).recoverWith(pf)
      }
  }

  object Recover {

    def total[F[_]: MonadThrow, G[_], A](
        http: Kleisli[F, Request[G], Response[G]]
    ): Kleisli[F, Request[G], Response[G]] =
      Kleisli { (a: Request[G]) =>
        http.run(a).handleError(totalRecover(a.httpVersion))
      }

    def messageFailure[F[_]: MonadThrow, G[_], A](
        http: Kleisli[F, Request[G], Response[G]]
    ): Kleisli[F, Request[G], Response[G]] =
      Kleisli { (a: Request[G]) =>
        http.run(a).recover(messageFailureRecover(a.httpVersion))
      }

    def messageFailureRecover[G[_]](
        httpVersion: HttpVersion
    ): PartialFunction[Throwable, Response[G]] = { case m: MessageFailure =>
      m.toHttpResponse[G](httpVersion)
    }

    def totalRecover[G[_]](
        httpVersion: HttpVersion
    ): Throwable => Response[G] = {
      case m: MessageFailure => m.toHttpResponse[G](httpVersion)
      case _ =>
        Response(
          Status.InternalServerError,
          httpVersion,
          Headers(
            Connection(ci"close"),
            `Content-Length`.zero,
          ),
        )
    }

  }
}
