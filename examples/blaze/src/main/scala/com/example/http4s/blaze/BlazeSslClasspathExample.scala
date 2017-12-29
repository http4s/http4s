package com.example.http4s.blaze

import cats.effect.IO
import com.example.http4s.ssl.SslClasspathExample
import org.http4s.server.blaze.BlazeBuilder

object BlazeSslClasspathExample extends SslClasspathExample[IO] {
  def builder: BlazeBuilder[IO] = BlazeBuilder[IO]
}
