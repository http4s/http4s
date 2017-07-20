package com.example.http4s
package ssl

import java.nio.file.Paths

import cats.effect._
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.{SSLKeyStoreSupport, ServerBuilder}
import org.http4s.util.StreamApp

trait SslExample extends StreamApp[IO] {
  // TODO: Reference server.jks from something other than one child down.
  val keypath = Paths.get("../server.jks").toAbsolutePath().toString()

  def builder: ServerBuilder[IO] with SSLKeyStoreSupport[IO]

  def stream(args: List[String], requestShutdown: IO[Unit]) =
    builder
      .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
      .mountService(ExampleService.service, "/http4s")
      .bindHttp(8443)
      .serve
}
