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
import org.http4s.headers.Authorization

/** Client middleware for enabling basic authentication
  */
object BasicAuth {

  def apply[F[_]: Async](credentials: Option[BasicCredentials])(client: Client[F]): Client[F] = {
    val authorization = credentials.map(Authorization(_))
    Client { request =>
      val requestWithAuth = authorization.fold(request)(auth => request.putHeaders(auth))
      client.run(requestWithAuth)
    }
  }

  def apply[F[_]: Async](credentials: BasicCredentials)(client: Client[F]): Client[F] =
    apply(Some(credentials))(client)

}
