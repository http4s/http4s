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
import org.http4s.internal.{Logger => InternalLogger}
import org.typelevel.ci.CIString
import org.log4s.getLogger

/** Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  private def defaultLogAction[F[_]: Sync](s: String): F[Unit] = Sync[F].delay(logger.info(s))

  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    impl(client, logBody) { response =>
      Logger.logMessage[F, Response[F]](response)(
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
    impl(client, logBody = true) { response =>
      InternalLogger.logMessageWithBodyText[F, Response[F]](response)(
        logHeaders,
        logBody,
        redactHeadersWhen
      )(logAction.getOrElse(defaultLogAction[F]))
    }

  def customized[F[_]: Concurrent](
      client: Client[F],
      logBody: Boolean = true,
      logAction: Option[String => F[Unit]] = None
  )(responseToText: Response[F] => F[String]): Client[F] =
    impl(client, logBody) { response =>
      val log = logAction.getOrElse(defaultLogAction[F] _)
      responseToText(response).flatMap(log)
    }

  private def impl[F[_]](client: Client[F], logBody: Boolean)(logMessage: Response[F] => F[Unit])(
      implicit F: Concurrent[F]): Client[F] =
    Client { req =>
      client.run(req).flatMap { response =>
        if (!logBody)
          Resource.liftF(logMessage(response) *> F.delay(response))
        else
          Resource.suspend {
            Ref[F].of(Vector.empty[Chunk[Byte]]).map { vec =>
              Resource.make(
                F.pure(
                  response.copy(body = response.body
                    // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                    .observe(_.chunks.flatMap(s => Stream.eval_(vec.update(_ :+ s)))))
                )) { _ =>
                val newBody = Stream
                  .eval(vec.get)
                  .flatMap(v => Stream.emits(v).covary[F])
                  .flatMap(c => Stream.chunk(c).covary[F])
                logMessage(response.withBodyStream(newBody)).attempt
                  .flatMap {
                    case Left(t) => F.delay(logger.error(t)("Error logging response body"))
                    case Right(()) => F.unit
                  }
              }
            }
          }
      }
    }

  def defaultResponseColor[F[_]](response: Response[F]): String =
    response.status.responseClass match {
      case Status.Informational | Status.Successful | Status.Redirection => Console.GREEN
      case Status.ClientError => Console.YELLOW
      case Status.ServerError => Console.RED
    }

  def colored[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      color: Response[F] => String = defaultResponseColor _,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    customized(client, logBody, logAction) { response =>
      val prelude = s"${response.httpVersion} ${response.status}"

      val headers: String =
        InternalLogger.defaultLogHeaders[F, Response[F]](response)(logHeaders, redactHeadersWhen)

      val bodyText: F[String] =
        InternalLogger.defaultLogBody[F, Response[F]](response)(logBody) match {
          case Some(textF) => textF.map(text => s"""body="$text"""")
          case None => Sync[F].pure("")
        }

      def spaced(x: String): String = if (x.isEmpty) x else s" $x"

      bodyText
        .map(body => s"${color(response)}$prelude${spaced(headers)}${spaced(body)}${Console.RESET}")
    }

}
