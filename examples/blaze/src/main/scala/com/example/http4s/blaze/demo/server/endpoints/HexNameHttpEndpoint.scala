package com.example.http4s.blaze.demo.server.endpoints

import cats.Monad
import org.http4s._
import org.http4s.dsl.Http4sDsl

class HexNameHttpEndpoint[F[_]: Monad] extends Http4sDsl[F] {

  object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

  val service: HttpService[F] = HttpService {
    case GET -> Root / ApiVersion / "hex" :? NameQueryParamMatcher(name) =>
      Ok(name.getBytes("UTF-8").map("%02x".format(_)).mkString)
  }

}
