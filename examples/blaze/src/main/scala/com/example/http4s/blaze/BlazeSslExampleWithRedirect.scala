package com.example.http4s
package blaze

import cats.effect.IO
import com.example.http4s.ssl.SslExampleWithRedirect
import org.http4s.server.blaze.BlazeBuilder

object BlazeSslExampleWithRedirect extends SslExampleWithRedirect[IO] {
  def builder: BlazeBuilder[IO] = BlazeBuilder[IO]
}
