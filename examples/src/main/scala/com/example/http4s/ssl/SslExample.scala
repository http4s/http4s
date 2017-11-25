package com.example.http4s
package ssl

import cats.effect._
import fs2.{Scheduler, Stream}
import java.nio.file.Paths
import org.http4s.server.{SSLKeyStoreSupport, ServerBuilder}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.middleware.HSTS
import org.http4s.util.StreamApp
import org.http4s.util.ExitCode

abstract class SslExample[F[_]: Effect] extends StreamApp[F] {
  // TODO: Reference server.jks from something other than one child down.
  val keypath: String = Paths.get("../server.jks").toAbsolutePath.toString

  def builder: ServerBuilder[F] with SSLKeyStoreSupport[F]

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    Scheduler(corePoolSize = 2).flatMap { implicit scheduler =>
      builder
        .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
        .mountService(HSTS(new ExampleService[F].service), "/http4s")
        .bindHttp(8443)
        .serve
    }
}
