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

import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all._
import fs2._
import org.http4s.internal.{Logger => InternalLogger}
import org.typelevel.ci.CIString

/** Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = Platform.loggerFactory.getLogger

  private def defaultLogAction[F[_]: Sync](s: String): F[Unit] = logger.info(s).to[F]

  def apply[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(client: Client[F]): Client[F] =
    impl(client, logBody) { request =>
      Logger.logMessage[F, Request[F]](request)(
        logHeaders,
        logBody,
        redactHeadersWhen,
      )(logAction.getOrElse(defaultLogAction[F]))
    }

  def logBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(client: Client[F]): Client[F] =
    impl(client, logBody = true) { request =>
      InternalLogger.logMessageWithBodyText(request)(
        logHeaders,
        logBody,
        redactHeadersWhen,
      )(logAction.getOrElse(defaultLogAction[F]))
    }

  def customized[F[_]: Async](
      client: Client[F],
      logBody: Boolean = true,
      logAction: Option[String => F[Unit]] = None,
  )(requestToText: Request[F] => F[String]): Client[F] =
    impl(client, logBody) { request =>
      val log = logAction.getOrElse(defaultLogAction[F] _)
      requestToText(request).flatMap(log)
    }

  private def impl[F[_]](client: Client[F], logBody: Boolean)(
      logMessage: Request[F] => F[Unit]
  )(implicit F: Async[F]): Client[F] =
    Client { req =>
      if (!logBody)
        Resource.eval(logMessage(req)) *> client.run(req)
      else
        Resource.suspend {
          (F.ref(false), F.ref(Vector.empty[Chunk[Byte]])).mapN { case (hasLogged, vec) =>
            val newBody = Stream.eval(vec.get).flatMap(v => Stream.emits(v)).unchunks

            val logOnceAtEnd: F[Unit] = hasLogged
              .getAndSet(true)
              .ifM(
                F.unit,
                logMessage(req.withBodyStream(newBody)).handleErrorWith { case t =>
                  logger.error(t)("Error logging request body").to[F]
                },
              )

            // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
            val logPipe: Pipe[F, Byte, Byte] =
              _.observe(_.chunks.flatMap(s => Stream.exec(vec.update(_ :+ s))))
                .onFinalizeWeak(logOnceAtEnd)

            val resp = client.run(req.pipeBodyThrough(logPipe))

            // If the request body was not consumed (ex: bodiless GET)
            // the second best we can do is log on the response body finalizer
            // the third best is on the response resource finalizer (ex: if the client failed to pull the body)
            resp
              .map[Response[F]](_.pipeBodyThrough(_.onFinalizeWeak(logOnceAtEnd)))
              .onFinalize(logOnceAtEnd)
          }
        }
    }

  val defaultRequestColor: String = Console.BLUE

  def colored[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      color: String = defaultRequestColor,
      logAction: Option[String => F[Unit]] = None,
  )(client: Client[F])(implicit F: Async[F]): Client[F] =
    customized(client, logBody, logAction) { request =>
      import Console._
      val methodColor =
        if (request.method.isSafe) color
        else YELLOW

      val prelude =
        s"${request.httpVersion} $methodColor${request.method}$RESET$color $BOLD${request.uri}$RESET$color"

      val headers: String =
        InternalLogger.defaultLogHeaders(request)(logHeaders, redactHeadersWhen)

      val bodyText: F[String] =
        InternalLogger.defaultLogBody(request)(logBody) match {
          case Some(textF) => textF.map(text => s"""body="$text"""")
          case None => Sync[F].pure("")
        }

      def spaced(x: String): String = if (x.isEmpty) x else s" $x"

      bodyText
        .map(body => s"$color$prelude${spaced(headers)}${spaced(body)}$RESET")
    }

}
