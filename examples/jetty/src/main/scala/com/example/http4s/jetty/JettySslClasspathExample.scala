package com.example.http4s.jetty

import cats.effect.IO
import com.example.http4s.ssl.SslClasspathExample
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.{SSLContextSupport, ServerBuilder}
import scala.concurrent.ExecutionContext.Implicits.global

object JettySslClasspathExample extends SslClasspathExample[IO] {
  override def builder: ServerBuilder[IO] with SSLContextSupport[IO] = JettyBuilder[IO]
}
