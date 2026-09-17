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

import cats.effect.MonadCancelThrow
import org.http4s.headers.Authorization

/** Client middleware for enabling basic authentication.
  *
  * The `Authorization` header is only added to requests that don't already
  * carry one, so a per-request `Authorization` header always takes precedence
  * over the configured credentials.
  */
object BasicAuth {

  /** Sends `credentials` with every request, whatever its destination.
    *
    * @note
    *   Because the credentials are not scoped to an authority, this middleware
    *   must wrap [[FollowRedirect]] rather than the other way around:
    *   `BasicAuth(credentials)(FollowRedirect(3)(client))`. `FollowRedirect`
    *   drops the `Authorization` header when redirected to a different
    *   authority, and an inner `BasicAuth` would put it back, leaking the
    *   credentials to the redirect target. Use [[forAuthority]] or [[when]] to
    *   scope the credentials instead.
    */
  def apply[F[_]: MonadCancelThrow](credentials: BasicCredentials)(client: Client[F]): Client[F] =
    when[F](_ => true, credentials)(client)

  /** Sends `credentials` with every request, or leaves `client` untouched if
    * they are absent.
    *
    * @note
    *   The credentials are not scoped to an authority. See the overload taking
    *   a [[org.http4s.BasicCredentials]] for how this interacts with
    *   [[FollowRedirect]].
    */
  def apply[F[_]: MonadCancelThrow](credentials: Option[BasicCredentials])(
      client: Client[F]
  ): Client[F] =
    credentials.fold(client)(c => when[F](_ => true, c)(client))

  /** Sends `credentials` only with requests addressed to `authority`, so that
    * they are never leaked to another host, notably when combined with
    * [[FollowRedirect]].
    */
  def forAuthority[F[_]: MonadCancelThrow](authority: Uri.Authority, credentials: BasicCredentials)(
      client: Client[F]
  ): Client[F] =
    when[F](_.uri.authority.contains(authority), credentials)(client)

  /** Sends `credentials` only with the requests matching `predicate`. */
  def when[F[_]: MonadCancelThrow](predicate: Request[F] => Boolean, credentials: BasicCredentials)(
      client: Client[F]
  ): Client[F] = {
    val authorization = Authorization(credentials)
    Client { request =>
      val requestWithAuth =
        if (request.headers.get[Authorization].isEmpty && predicate(request))
          request.putHeaders(authorization)
        else
          request
      client.run(requestWithAuth)
    }
  }

}
