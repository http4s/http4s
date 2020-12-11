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

import cats.syntax.all._
import cats.data._
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.effect.implicits._

import org.http4s.Status
import org.http4s.{Request, Response}

object MaxActiveRequests {
  def httpApp[F[_]: Concurrent](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  ): F[Kleisli[F, Request[F], Response[F]] => Kleisli[F, Request[F], Response[F]]] =
    inHttpApp[F, F](maxActive, defaultResp)

  def inHttpApp[G[_]: Sync, F[_]: Concurrent](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  ): G[Kleisli[F, Request[F], Response[F]] => Kleisli[F, Request[F], Response[F]]] =
    Semaphore.in[G, F](maxActive).map { sem => http: Kleisli[F, Request[F], Response[F]] =>
      Kleisli { (a: Request[F]) =>
        sem.tryAcquire.bracketCase { bool =>
          if (bool)
            http.run(a).map(resp => resp.copy(body = resp.body.onFinalizeWeak(sem.release)))
          else defaultResp.pure[F]
        } {
          case (bool, ExitCase.Canceled | ExitCase.Error(_)) =>
            if (bool) sem.release
            else Sync[F].unit
          case (_, ExitCase.Completed) => Sync[F].unit
        }
      }
    }

  def httpRoutes[F[_]: Concurrent](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  ): F[Kleisli[OptionT[F, *], Request[F], Response[F]] => Kleisli[
    OptionT[F, *],
    Request[F],
    Response[F]]] =
    inHttpRoutes[F, F](maxActive, defaultResp)

  def inHttpRoutes[G[_]: Sync, F[_]: Concurrent](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  ): G[Kleisli[OptionT[F, *], Request[F], Response[F]] => Kleisli[
    OptionT[F, *],
    Request[F],
    Response[F]]] =
    Semaphore.in[G, F](maxActive).map {
      sem => http: Kleisli[OptionT[F, *], Request[F], Response[F]] =>
        Kleisli { (a: Request[F]) =>
          Concurrent[OptionT[F, *]].bracketCase(OptionT.liftF(sem.tryAcquire)) { bool =>
            if (bool)
              http
                .run(a)
                .map(resp => resp.copy(body = resp.body.onFinalizeWeak(sem.release)))
                .orElseF(sem.release.as(None))
            else OptionT.pure[F](defaultResp)
          } {
            case (bool, ExitCase.Canceled | ExitCase.Error(_)) =>
              if (bool) OptionT.liftF(sem.release)
              else OptionT.pure[F](())
            case (_, ExitCase.Completed) => OptionT.pure[F](())
          }
        }
    }
}
