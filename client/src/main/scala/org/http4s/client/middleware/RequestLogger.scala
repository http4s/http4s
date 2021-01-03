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
package client
package middleware

import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._
import fs2._
import org.log4s.getLogger
import org.http4s.internal.{Logger => InternalLogger}
import org.typelevel.ci.CIString

/** Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  private def defaultLogAction[F[_]: Sync](s: String): F[Unit] = Sync[F].delay(logger.info(s))

  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    impl(client, logBody) { request =>
      Logger.logMessage[F, Request[F]](request)(
        logHeaders,
        logBody,
        redactHeadersWhen
      )(logAction.getOrElse(defaultLogAction[F]))
    }

  def logBodyText[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    impl(client, logBody = true) { request =>
      InternalLogger.logMessageWithBodyText[F, Request[F]](request)(
        logHeaders,
        logBody,
        redactHeadersWhen
      )(logAction.getOrElse(defaultLogAction[F]))
    }

  def customized[F[_]: Concurrent](
      client: Client[F],
      logBody: Boolean = true,
      logAction: Option[String => F[Unit]] = None
  )(requestToText: Request[F] => F[String]): Client[F] =
    impl(client, logBody) { request =>
      val log = logAction.getOrElse(defaultLogAction[F] _)
      requestToText(request).flatMap(log)
    }

  private def impl[F[_]](client: Client[F], logBody: Boolean)(logMessage: Request[F] => F[Unit])(
      implicit F: Concurrent[F]): Client[F] =
    Client { req =>
      if (!logBody)
        Resource.liftF(logMessage(req)) *> client.run(req)
      else
        Resource.suspend {
          Ref[F].of(Vector.empty[Chunk[Byte]]).map { vec =>
            val newBody = Stream
              .eval(vec.get)
              .flatMap(v => Stream.emits(v).covary[F])
              .flatMap(c => Stream.chunk(c).covary[F])

            val changedRequest = req.withBodyStream(
              req.body
                // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                .observe(_.chunks.flatMap(s => Stream.eval_(vec.update(_ :+ s))))
                .onFinalizeWeak(
                  logMessage(req.withBodyStream(newBody)).attempt
                    .flatMap {
                      case Left(t) => F.delay(logger.error(t)("Error logging request body"))
                      case Right(()) => F.unit
                    }
                )
            )

            client.run(changedRequest)
          }
        }
    }

  val defaultRequestColor: String = Console.BLUE

  def colored[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      color: String = defaultRequestColor,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    customized(client, logBody, logAction) { request =>
      import Console._
      val methodColor =
        if (request.method.isSafe) color
        else YELLOW

      val prelude =
        s"${request.httpVersion} $methodColor${request.method}$RESET$color $BOLD${request.uri}$RESET$color"

      val headers: String =
        InternalLogger.defaultLogHeaders[F, Request[F]](request)(logHeaders, redactHeadersWhen)

      val bodyText: F[String] =
        InternalLogger.defaultLogBody[F, Request[F]](request)(logBody) match {
          case Some(textF) => textF.map(text => s"""body="$text"""")
          case None => Sync[F].pure("")
        }

      def spaced(x: String): String = if (x.isEmpty) x else s" $x"

      bodyText
        .map(body => s"$color$prelude${spaced(headers)}${spaced(body)}$RESET")
    }

}
