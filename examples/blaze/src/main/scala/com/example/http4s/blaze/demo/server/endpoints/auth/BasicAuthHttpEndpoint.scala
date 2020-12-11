/*
 * Copyright 2013 http4s.org
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

package com.example.http4s.blaze.demo.server.endpoints.auth

import cats.effect.Sync
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.authentication.BasicAuth

// Use this header --> Authorization: Basic Z3ZvbHBlOjEyMzQ1Ng==
class BasicAuthHttpEndpoint[F[_]](implicit F: Sync[F], R: AuthRepository[F, BasicCredentials])
    extends Http4sDsl[F] {
  private val authedRoutes: AuthedRoutes[BasicCredentials, F] = AuthedRoutes.of {
    case GET -> Root as user =>
      Ok(s"Access Granted: ${user.username}")
  }

  private val authMiddleware: AuthMiddleware[F, BasicCredentials] =
    BasicAuth[F, BasicCredentials]("Protected Realm", R.find)

  val service: HttpRoutes[F] = authMiddleware(authedRoutes)
}
