package com.example.http4s.blaze.demo.server.endpoints

import java.util.concurrent.TimeUnit

import cats.effect.Async
import fs2.Scheduler
import org.http4s._
import org.http4s.dsl.Http4sDsl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class TimeoutHttpEndpoint[F[_]](implicit F: Async[F], S: Scheduler) extends Http4sDsl[F] {

  val service: HttpService[F] = HttpService {
    case GET -> Root / ApiVersion / "timeout" =>
      val randomDuration = FiniteDuration(Random.nextInt(3) * 1000L, TimeUnit.MILLISECONDS)
      S.effect.delay(Ok("delayed response"), randomDuration)
  }

}
