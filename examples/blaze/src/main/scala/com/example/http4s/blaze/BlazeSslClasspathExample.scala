package com.example.http4s.blaze

import cats.effect.IO
import com.example.http4s.ssl.SslClasspathExample
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object BlazeSslClasspathExample extends SslClasspathExample[IO] {
  def builder: BlazeBuilder[IO] = BlazeBuilder[IO]
}
