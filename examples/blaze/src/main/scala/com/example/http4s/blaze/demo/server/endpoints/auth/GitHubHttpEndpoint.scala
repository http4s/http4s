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
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.example.http4s.blaze.demo.server.endpoints.ApiVersion
import com.example.http4s.blaze.demo.server.service.GitHubService
import org.http4s._
import org.http4s.dsl.Http4sDsl

class GitHubHttpEndpoint[F[_]](gitHubService: GitHubService[F])(implicit F: Sync[F])
    extends Http4sDsl[F] {
  object CodeQuery extends QueryParamDecoderMatcher[String]("code")
  object StateQuery extends QueryParamDecoderMatcher[String]("state")

  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "github" =>
      Ok(gitHubService.authorize)

    // OAuth2 Callback URI
    case GET -> Root / ApiVersion / "login" / "github" :? CodeQuery(code) :? StateQuery(state) =>
      for {
        o <- Ok()
        code <- gitHubService.accessToken(code, state).flatMap(gitHubService.userData)
      } yield o.withEntity(code).putHeaders(Header("Content-Type", "application/json"))
  }
}
