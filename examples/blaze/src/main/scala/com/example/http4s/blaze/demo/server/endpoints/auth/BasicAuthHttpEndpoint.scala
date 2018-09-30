package com.example.http4s.blaze.demo.server.endpoints.auth

import cats.effect.Sync
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.authentication.BasicAuth

// Use this header --> Authorization: Basic Z3ZvbHBlOjEyMzQ1Ng==
class BasicAuthHttpEndpoint[F[_]](implicit F: Sync[F], R: AuthRepository[F, BasicCredentials])
    extends Http4sDsl[F] {

  private val authedService: AuthedService[BasicCredentials, F] = AuthedService {
    case GET -> Root as user =>
      Ok(s"Access Granted: ${user.username}")
  }

  private val authMiddleware: AuthMiddleware[F, BasicCredentials] =
    BasicAuth[F, BasicCredentials]("Protected Realm", R.find)

  val service: HttpRoutes[F] = authMiddleware(authedService)

}
