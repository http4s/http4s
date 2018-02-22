package com.example.http4s.blaze.demo.server.endpoints

import cats.effect.Sync
import cats.syntax.flatMap._
import org.http4s._
import org.http4s.dsl.Http4sDsl

import scala.util.Random

class TimeoutHttpEndpoint[F[_]](implicit F: Sync[F]) extends Http4sDsl[F] {

  val service: HttpService[F] = HttpService {
    case GET -> Root / ApiVersion / "timeout" =>
      F.delay(Thread.sleep(Random.nextInt(3) * 1000L)).flatMap(_ => Ok())
  }

}
