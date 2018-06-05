package com.example.http4s
package ssl

import cats.effect._
import fs2.StreamApp.ExitCode
import fs2._
import java.nio.file.Paths
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.middleware.HSTS
import org.http4s.server.{SSLKeyStoreSupport, ServerBuilder}
import scala.concurrent.ExecutionContext.Implicits.global

abstract class SslExample[F[_]: ConcurrentEffect] extends StreamApp[F] {
  // TODO: Reference server.jks from something other than one child down.
  val keypath: String = Paths.get("../server.jks").toAbsolutePath.toString

  def builder: ServerBuilder[F] with SSLKeyStoreSupport[F]

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] = {
    implicit val timer = Timer.derive[F]
    builder
      .withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "secure")
      .mountService(HSTS(new ExampleService[F].service), "/http4s")
      .bindHttp(8443)
      .serve
  }
}
