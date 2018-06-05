package com.example.http4s
package blaze

import cats.effect.{IO, Timer}
import com.example.http4s.ssl.SslExampleWithRedirect
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object BlazeSslExampleWithRedirect extends SslExampleWithRedirect[IO] {
  implicit val timer: Timer[IO] = Timer[IO]
  def builder: BlazeBuilder[IO] = BlazeBuilder[IO]
}
