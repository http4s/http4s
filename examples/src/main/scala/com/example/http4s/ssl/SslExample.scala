package com.example.http4s
package ssl

import java.nio.file.Paths

import fs2._
import org.http4s.server._, SSLSupport._

trait SslExample extends ServerApp {
  // TODO: Reference server.jks from something other than one child down.
  val keypath = Paths.get("../server.jks").toAbsolutePath().toString()

  def builder: ServerBuilder with SSLSupport

  def server(args: List[String]): Task[Server] = builder
    .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
    .mountService(ExampleService.service, "/http4s")
    .bindHttp(8443)
    .start
}

