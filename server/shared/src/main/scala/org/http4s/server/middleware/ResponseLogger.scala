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
import cats.effect.kernel.Async
import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.Outcome
import cats.effect.kernel.Sync
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.~>
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import org.log4s.getLogger
import org.typelevel.ci.CIString

sealed abstract class ResponseLogger[F[_]] extends internal.Logger[F, ResponseLogger[F]]

/** Simple middleware for logging responses as they are processed
  */
object ResponseLogger {

  private[middleware] final case class Impl[F[_]](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(implicit F: Async[F])
      extends ResponseLogger[F] {
    override def apply[G[_], A](fk: F ~> G)(
        http: Kleisli[G, A, Response[F]]
    )(implicit G: MonadCancelThrow[G]): Kleisli[G, A, Response[F]] = {

      val fallback: String => F[Unit] = s => Sync[F].delay(logger.info(s))
      val log = logAction.fold(fallback)(identity)

      def logMessage(resp: Response[F]): F[Unit] =
        logBodyText match {
          case Left(bool) =>
            Logger.logMessage[F, Response[F]](resp)(logHeaders, bool, redactHeadersWhen)(log(_))
          case Right(f) =>
            org.http4s.internal.Logger
              .logMessageWithBodyText(resp)(logHeaders, f, redactHeadersWhen)(log(_))
        }

      val logBody: Boolean = logBodyText match {
        case Left(bool) => bool
        case Right(_) => true
      }

      def logResponse(response: Response[F]): F[Response[F]] =
        if (!logBody)
          logMessage(response)
            .as(response)
        else {
          response.entity match {
            case Entity.Default(_, _) =>
              F.ref(Vector.empty[Chunk[Byte]]).map { vec =>
                val newBody = Stream.eval(vec.get).flatMap(v => Stream.emits(v)).unchunks
                // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                val logPipe: Pipe[F, Byte, Byte] =
                  _.observe(_.chunks.flatMap(c => Stream.exec(vec.update(_ :+ c))))
                    .onFinalizeWeak(logMessage(response.withBodyStream(newBody)))

                response.pipeBodyThrough(logPipe)
              }

            case Entity.Strict(_) | Entity.Empty =>
              logMessage(response).as(response)
          }
        }

      Kleisli[G, A, Response[F]] { req =>
        http(req)
          .flatMap((response: Response[F]) => fk(logResponse(response)))
          .guaranteeCase {
            case Outcome.Errored(t) => fk(log(s"service raised an error: ${t.getClass}"))
            case Outcome.Canceled() => fk(log(s"service canceled response for request"))
            case Outcome.Succeeded(_) => G.unit
          }
      }
    }

    override def withRedactHeadersWhen(f: CIString => Boolean): ResponseLogger[F] =
      copy(redactHeadersWhen = f)

    override def withLogAction(f: String => F[Unit]): ResponseLogger[F] = copy(logAction = Some(f))
  }

  def apply[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
  ): ResponseLogger[F] = Impl(logHeaders, Left(logBody))

  def apply[F[_]: Async](
      logHeaders: Boolean,
      logBodyWith: Stream[F, Byte] => Option[F[String]],
  ): ResponseLogger[F] = Impl(logHeaders, Right(logBodyWith))

  private[this] val logger = getLogger

  @deprecated("Use the DSL beginning with ResponseLogger(logHeaders, logBody)", "1.0.0-M???")
  def httpApp[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: Kleisli[F, A, Response[F]]): Kleisli[F, A, Response[F]] =
    apply(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpApp)

  @deprecated("Use the DSL beginning with ResponseLogger(logHeaders, logBody)", "1.0.0-M???")
  def httpAppLogBodyText[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: Kleisli[F, A, Response[F]]): Kleisli[F, A, Response[F]] =
    apply(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpApp)

  @deprecated("Use the DSL beginning with ResponseLogger(logHeaders, logBody)", "1.0.0-M???")
  def httpRoutes[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: Kleisli[OptionT[F, *], A, Response[F]]): Kleisli[OptionT[F, *], A, Response[F]] =
    apply(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpRoutes)

  @deprecated("Use the DSL beginning with ResponseLogger(logHeaders, logBody)", "1.0.0-M???")
  def httpRoutesLogBodyText[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: Kleisli[OptionT[F, *], A, Response[F]]): Kleisli[OptionT[F, *], A, Response[F]] =
    apply(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpRoutes)
}
