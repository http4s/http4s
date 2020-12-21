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
<<<<<<< HEAD
import cats.effect.Ref
import cats.implicits._
=======
import cats.effect.concurrent.Ref
import cats.syntax.all._
>>>>>>> cats-effect-3
import fs2._
import org.typelevel.ci.CIString
import org.log4s.getLogger

/** Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F])(implicit F: Async[F]): Client[F] =
    impl[F](logHeaders, Left(logBody), redactHeadersWhen, logAction)(client)

  def logBodyText[F[_]](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F])(implicit F: Async[F]): Client[F] =
    impl[F](logHeaders, Right(logBody), redactHeadersWhen, logAction)(client)

  private def impl[F[_]](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      redactHeadersWhen: CIString => Boolean,
      logAction: Option[String => F[Unit]]
  )(client: Client[F])(implicit F: Async[F]): Client[F] = {
    val log = logAction.getOrElse { (s: String) =>
      F.delay(logger.info(s))
    }

    def logMessage(resp: Response[F]): F[Unit] =
      logBodyText match {
        case Left(bool) =>
          Logger.logMessage[F, Response[F]](resp)(logHeaders, bool, redactHeadersWhen)(log(_))
        case Right(f) =>
          org.http4s.internal.Logger
            .logMessageWithBodyText[F, Response[F]](resp)(logHeaders, f, redactHeadersWhen)(log(_))
      }

    val logBody: Boolean = logBodyText match {
      case Left(bool) => bool
      case Right(_) => true
    }

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
                    .observe(_.chunks.flatMap(s => Stream.exec(vec.update(_ :+ s)))))
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
  }

}
