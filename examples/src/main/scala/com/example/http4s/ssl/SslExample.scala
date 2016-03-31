package com.example.http4s.ssl

import java.nio.file.Paths

import com.example.http4s.ExampleService
import org.http4s.server.SSLSupport.StoreInfo
import org.http4s.server.{ SSLSupport, Server, ServerApp, ServerBuilder }
import scalaz.concurrent.Task

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

