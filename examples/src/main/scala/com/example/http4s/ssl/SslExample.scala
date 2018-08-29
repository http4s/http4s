package com.example.http4s
package ssl

import cats.effect._
import fs2._
import java.nio.file.Paths
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.middleware.HSTS
import org.http4s.server.{SSLKeyStoreSupport, ServerBuilder}

abstract class SslExample[F[_]](implicit T: Timer[F], F: ConcurrentEffect[F], cs: ContextShift[F]) {
  // TODO: Reference server.jks from something other than one child down.
  val keypath: String = Paths.get("../server.jks").toAbsolutePath.toString

  def builder: ServerBuilder[F] with SSLKeyStoreSupport[F]

  def stream: Stream[F, ExitCode] = {
    builder
      .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
      .mountService(HSTS(new ExampleService[F].service), "/http4s")
      .bindHttp(8443)
      .serve
  }
}
