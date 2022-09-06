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

package org.http4s.client.middleware

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.effect.kernel.Async
import cats.syntax.all._
import org.http4s.Status.Unauthorized
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.digest.NonceCounter
import org.http4s.headers.Authorization
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.implicits._
import org.http4s.internal.DigestUtil
import org.log4s.getLogger

/** Digest Middleware for digest auth from RFC 7616
  */
object DigestClient {
  private[this] val logger = getLogger

  def apply[F[_]](
      username: String,
      password: String,
  )(client: Client[F])(implicit F: Async[F]): Client[F] =
    apply[F](username, password, "auth")(client)

  def apply[F[_]: Async](
      username: String,
      password: String,
      qop: String,
  )(client: Client[F]): Client[F] = {
    def digestAuthHeader(req: Request[F], challenge: Challenge): F[Authorization] =
      for {
        nonce <- MonadThrow[F].fromOption(
          challenge.params.get("nonce"),
          new IllegalStateException(s"No nonce param in challenge, $challenge"),
        )
        nonceCounter <- NonceCounter.make[F]()
        tuple <- nonceCounter.next(nonce)
        (cnonce, ncLong) = tuple
        realm = challenge.realm
        method = req.method.name
        uri = req.uri
        nc = f"$ncLong%08X"
        response <- DigestUtil.computeResponse(
          method,
          username,
          realm,
          password,
          uri,
          nonce,
          nc,
          cnonce,
          qop,
        )
      } yield {
        val params: NonEmptyList[(String, String)] = NonEmptyList.of(
          "username" -> username,
          "realm" -> realm,
          "nonce" -> nonce,
          "uri" -> uri.toString,
          "qop" -> qop,
          "nc" -> nc,
          "cnonce" -> cnonce,
          "response" -> response,
          "method" -> method,
        )
        Authorization(Credentials.AuthParams(AuthScheme.Digest, params))
      }

    def prepareLoop(req: Request[F]): F[Resource[F, Response[F]]] =
      MonadCancelThrow[F].uncancelable { poll =>
        poll(client.run(req).allocated).attempt.flatMap {
          case Right((resp, dispose))
              if !hasDigestAuth(req.headers) && resp.status == Unauthorized =>
            extractChallenge(resp.headers) match {
              case Some(challenge) =>
                dispose >> digestAuthHeader(req, challenge) >>= (h =>
                  prepareLoop(req.putHeaders(h))
                )
              case None =>
                Resource.make(resp.pure[F])(_ => dispose).pure[F]
            }
          case Right((resp, dispose)) => Resource.make(resp.pure[F])(_ => dispose).pure[F]
          case Left(e) => MonadCancelThrow[F].raiseError(e)
        }
      }

    Client[F](req => Resource.suspend(prepareLoop(req)))
  }

  private def extractChallenge(headers: Headers): Option[Challenge] =
    for {
      challenge <- headers.get[`WWW-Authenticate`].map(_.value)
      res <- `WWW-Authenticate`
        .parse(challenge)
        .fold(
          _ => {
            logger.info(s"Couldn't parse challenge: $challenge")
            none
          },
          v => v.values.head.some,
        )
    } yield res

  private def hasDigestAuth(headers: Headers): Boolean =
    headers
      .get[Authorization]
      .exists(_.credentials.authScheme == AuthScheme.Digest)

}
