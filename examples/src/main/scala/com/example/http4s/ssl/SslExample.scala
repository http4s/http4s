package com.example.http4s
package ssl

import java.nio.file.Paths

import fs2._
import org.http4s.server._, SSLSupport._
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.{ SSLKeyStoreSupport, Server, ServerApp, ServerBuilder }

trait SslExample extends ServerApp {
  // TODO: Reference server.jks from something other than one child down.
  val keypath = Paths.get("../server.jks").toAbsolutePath().toString()

  def builder: ServerBuilder with SSLKeyStoreSupport

  def server(args: List[String]): Task[Server] = builder
    .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
    .mountService(ExampleService.service, "/http4s")
    .bindHttp(8443)
    .start
}

