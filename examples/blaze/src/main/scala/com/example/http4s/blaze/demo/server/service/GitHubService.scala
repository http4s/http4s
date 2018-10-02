package com.example.http4s.blaze.demo.server.service

import cats.effect.Sync
import cats.syntax.functor._
import com.example.http4s.blaze.demo.server.endpoints.ApiVersion
import fs2.Stream
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Header, Request, Uri}

// See: https://developer.github.com/apps/building-oauth-apps/authorization-options-for-oauth-apps/#web-application-flow
class GitHubService[F[_]: Sync](client: Client[F]) extends Http4sClientDsl[F] {

  // NEVER make this data public! This is just a demo!
  private val ClientId = "959ea01cd3065cad274a"
  private val ClientSecret = "53901db46451977e6331432faa2616ba24bc2550"

  private val RedirectUri = s"http://localhost:8080/$ApiVersion/login/github"

  case class AccessTokenResponse(access_token: String)

  val authorize: Stream[F, Byte] = {
    val uri = Uri
      .uri("https://github.com")
      .withPath("/login/oauth/authorize")
      .withQueryParam("client_id", ClientId)
      .withQueryParam("redirect_uri", RedirectUri)
      .withQueryParam("scopes", "public_repo")
      .withQueryParam("state", "test_api")

    client.stream(Request[F](uri = uri)).flatMap(_.body)
  }

  def accessToken(code: String, state: String): F[String] = {
    val uri = Uri
      .uri("https://github.com")
      .withPath("/login/oauth/access_token")
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
    val request = Request[F](uri = Uri.uri("https://api.github.com/user"))
      .putHeaders(Header("Authorization", s"token $accessToken"))

    client.expect[String](request)
  }

}
