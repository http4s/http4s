package com.example.http4s.ssl

import java.nio.file.Paths

import com.example.http4s.ExampleService
import org.http4s.server.SSLSupport.StoreInfo
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.tomcat.TomcatBuilder
import org.http4s.server.{SSLSupport, ServerBuilder}

class SslExample extends App {
  val keypath = Paths.get("server.jks").toAbsolutePath().toString()
  def go(builder: ServerBuilder with SSLSupport): Unit = builder
    .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
    .mountService(ExampleService.service, "/http4s")
    .bindHttp(4430)
    .run
    .awaitShutdown()
}

object JettySSLExample extends SslExample {
  go(JettyBuilder)
}

object TomcatSSLExample extends SslExample {
  go(TomcatBuilder)
}

object BlazeSSLExample extends SslExample {
  go(BlazeBuilder)
}
