package com.example.http4s.blaze.demo.server.endpoints

import cats.effect.Sync
import org.http4s._
import org.http4s.dsl.Http4sDsl

class HexNameHttpEndpoint[F[_]: Sync] extends Http4sDsl[F] {

  object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "hex" :? NameQueryParamMatcher(name) =>
      Ok(name.getBytes("UTF-8").map("%02x".format(_)).mkString)
  }

}
