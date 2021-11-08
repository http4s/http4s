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

package com.example.http4s.blaze.demo.server.service

import cats.effect.Sync
import cats.syntax.functor._
import com.example.http4s.blaze.demo.server.endpoints.ApiVersion
import fs2.Stream
import io.circe.generic.auto._
import org.http4s.Request
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.syntax.literals._

// See: https://developer.github.com/apps/building-oauth-apps/authorization-options-for-oauth-apps/#web-application-flow
class GitHubService[F[_]: Sync](client: Client[F]) extends Http4sClientDsl[F] {
  // NEVER make this data public! This is just a demo!
  private val ClientId = "959ea01cd3065cad274a"
  private val ClientSecret = "53901db46451977e6331432faa2616ba24bc2550"

  private val RedirectUri = s"http://localhost:8080/$ApiVersion/login/github"

  private case class AccessTokenResponse(access_token: String)

  val authorize: Stream[F, Byte] = {
    val uri = uri"https://github.com"
      .withPath(path"/login/oauth/authorize")
      .withQueryParam("client_id", ClientId)
      .withQueryParam("redirect_uri", RedirectUri)
      .withQueryParam("scopes", "public_repo")
      .withQueryParam("state", "test_api")

    client.stream(Request[F](uri = uri)).flatMap(_.body)
  }

  def accessToken(code: String, state: String): F[String] = {
    val uri = uri"https://github.com"
      .withPath(path"/login/oauth/access_token")
      .withQueryParam("client_id", ClientId)
      .withQueryParam("client_secret", ClientSecret)
      .withQueryParam("code", code)
      .withQueryParam("redirect_uri", RedirectUri)
      .withQueryParam("state", state)

    client
      .expect[AccessTokenResponse](Request[F](uri = uri))(jsonOf[F, AccessTokenResponse])
      .map(_.access_token)
  }

  def userData(accessToken: String): F[String] = {
    val request = Request[F](uri = uri"https://api.github.com/user")
      .putHeaders("Authorization" -> s"token $accessToken")

    client.expect[String](request)
  }
}
