package com.example.http4s.blaze.demo.server.endpoints

import cats.effect.{Async, Timer}
import cats.implicits._
import java.util.concurrent.TimeUnit
import org.http4s._
import org.http4s.dsl.Http4sDsl
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class TimeoutHttpEndpoint[F[_]](implicit F: Async[F], timer: Timer[F]) extends Http4sDsl[F] {

  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "timeout" =>
      val randomDuration = FiniteDuration(Random.nextInt(3) * 1000L, TimeUnit.MILLISECONDS)
      timer.sleep(randomDuration) *> Ok("delayed response")
  }

}
