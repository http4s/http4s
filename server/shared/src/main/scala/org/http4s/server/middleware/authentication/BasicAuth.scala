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
package authentication

import cats.Applicative
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.all._
import org.http4s.headers.Authorization

/** Provides Basic Authentication from RFC 2617.
  */
object BasicAuth {

  private val authParams = Map("charset" -> "UTF-8")

  /** Validates a plaintext password (presumably by comparing it to a
    * hashed value).  A Some value indicates success; None indicates
    * the password failed to validate.
    */
  type BasicAuthenticator[F[_], A] = BasicCredentials => F[Option[A]]

  /** Construct authentication middleware that can validate the client-provided
    * plaintext password against something else (like a stored, hashed password).
    * @param realm The realm used for authentication purposes.
    * @param validate Function that validates a plaintext password
    * @return
    */
  def apply[F[_]: Sync, A](
      realm: String,
      validate: BasicAuthenticator[F, A],
  ): AuthMiddleware[F, A] =
    challenged(challenge(realm, validate))

  def challenge[F[_]: Applicative, A](
      realm: String,
      validate: BasicAuthenticator[F, A],
  ): Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]] =
    Kleisli { req =>
      validatePassword(validate, req).map {
        case Some(authInfo) =>
          Right(AuthedRequest(authInfo, req))
        case None =>
          Left(Challenge("Basic", realm, authParams))
      }
    }

  private def validatePassword[F[_], A](validate: BasicAuthenticator[F, A], req: Request[F])(
      implicit F: Applicative[F]
  ): F[Option[A]] =
    req.headers.get[Authorization] match {
      case Some(Authorization(BasicCredentials(username, password))) =>
        validate(BasicCredentials(username, password))
      case _ =>
        F.pure(None)
    }
}
