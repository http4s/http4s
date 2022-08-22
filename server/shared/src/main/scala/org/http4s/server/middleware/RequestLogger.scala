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

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.Outcome
import cats.effect.kernel.Sync
import cats.syntax.all._
import cats.~>
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import org.log4s.getLogger
import org.typelevel.ci.CIString

sealed abstract class RequestLoggerBuilder[F[_]]
    extends internal.Logger[F, RequestLoggerBuilder[F]] {
  def apply[G[_]](fk: F ~> G)(
      http: Http[G, F]
  )(implicit G: MonadCancelThrow[G]): Http[G, F]

  def apply[G[_]](
      http: Http[G, F]
  )(implicit lift: Logger.Lift[F, G], G: MonadCancelThrow[G]): Http[G, F] =
    apply(lift.fk)(http)
}

/** Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  private[middleware] final case class Impl[F[_]](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(implicit F: Async[F])
      extends RequestLoggerBuilder[F] {

    override def apply[G[_]](fk: F ~> G)(
        http: Http[G, F]
    )(implicit G: MonadCancelThrow[G]): Http[G, F] =
      impl(logHeaders, logBodyText, fk, redactHeadersWhen, logAction)(http)

    override def withRedactHeadersWhen(f: CIString => Boolean): RequestLoggerBuilder[F] =
      copy(redactHeadersWhen = f)

    override def withLogAction(f: String => F[Unit]): RequestLoggerBuilder[F] =
      copy(logAction = Some(f))
  }

  def builder[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
  ): RequestLoggerBuilder[F] = Impl(logHeaders, Left(logBody))

  def builder[F[_]: Async](
      logHeaders: Boolean,
      renderBodyWith: Stream[F, Byte] => Option[F[String]],
  ): RequestLoggerBuilder[F] = Impl(logHeaders, Right(renderBodyWith))

  @deprecated("Use RequestLogger.builder", "0.23.15")
  def apply[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit
      F: Async[F],
      G: MonadCancelThrow[G],
  ): Http[G, F] =
    impl[G, F](logHeaders, Left(logBody), fk, redactHeadersWhen, logAction)(http)

  private[server] def impl[G[_], F[_]](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean,
      logAction: Option[String => F[Unit]],
  )(http: Http[G, F])(implicit
      F: Async[F],
      G: MonadCancelThrow[G],
  ): Http[G, F] = {
    val log = logAction.fold { (s: String) =>
      Sync[F].delay(logger.info(s))
    }(identity)

    def logMessage(r: Request[F]): F[Unit] =
      logBodyText match {
        case Left(bool) =>
          Logger.logMessage[F, Request[F]](r)(logHeaders, bool, redactHeadersWhen)(log(_))
        case Right(f) =>
          org.http4s.internal.Logger
            .logMessageWithBodyText(r)(logHeaders, f, redactHeadersWhen)(log(_))
      }

    val logBody: Boolean = logBodyText match {
      case Left(bool) => bool
      case Right(_) => true
    }

    Kleisli { req =>
      if (!logBody) {
        def logAct = logMessage(req)
        // This construction will log on Any Error/Cancellation
        // The Completed Case is Unit, as we rely on the semantics of G
        // As None Is Successful, but we oly want to log on Some
        http(req)
          .guaranteeCase {
            case Outcome.Succeeded(_) => G.unit
            case _ => fk(logAct)
          } <* fk(logAct)
      } else
        fk(F.ref(Vector.empty[Chunk[Byte]]))
          .flatMap { vec =>
            val collectChunks: Pipe[F, Byte, Nothing] =
              _.chunks.flatMap(c => Stream.exec(vec.update(_ :+ c)))

            val changedRequest = req.pipeBodyThrough(_.observe(collectChunks))

            val newBody = Stream.eval(vec.get).flatMap(v => Stream.emits(v)).unchunks
            val logRequest: F[Unit] = logMessage(req.withBodyStream(newBody))

            http(changedRequest)
              .guaranteeCase {
                case Outcome.Succeeded(_) => G.unit
                case _ => fk(logRequest)
              }
              .map(_.pipeBodyThrough(_.onFinalizeWeak(logRequest)))
          }
    }
  }

  @deprecated("Use RequestLogger.builder", "0.23.15")
  def httpApp[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  @deprecated("Use RequestLogger.builder", "0.23.15")
  def httpRoutes[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  def httpAppLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    impl[F, F](logHeaders, Right(logBody), FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  @deprecated("Use RequestLogger.builder", "0.23.15")
  def httpRoutesLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    impl[OptionT[F, *], F](
      logHeaders,
      Right(logBody),
      OptionT.liftK[F],
      redactHeadersWhen,
      logAction,
    )(httpRoutes)
}
