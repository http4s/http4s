package com.example.http4s
package tomcat

import cats.effect.IO
import com.example.http4s.ssl.SslExample
import org.http4s.server.tomcat.TomcatBuilder

object TomcatSslExample extends SslExample[IO] {
  def builder: TomcatBuilder[IO] = TomcatBuilder[IO]
}
