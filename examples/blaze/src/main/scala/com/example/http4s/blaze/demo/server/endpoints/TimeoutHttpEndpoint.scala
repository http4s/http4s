package com.example.http4s.blaze.demo.server.endpoints

import java.util.concurrent.TimeUnit
import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class TimeoutHttpEndpoint[F[_]](implicit F: Async[F], T: Timer[F]) extends Http4sDsl[F] {

  val service: HttpService[F] = HttpService {
    case GET -> Root / ApiVersion / "timeout" =>
      val randomDuration = FiniteDuration(Random.nextInt(3) * 1000L, TimeUnit.MILLISECONDS)
      T.sleep(randomDuration) *> Ok("delayed response")
  }

}
