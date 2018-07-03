package com.example.http4s.ssl

import cats.effect.{ConcurrentEffect, Sync, Timer}
import com.example.http4s.ExampleService
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import org.http4s.server.middleware.HSTS
import org.http4s.server.{SSLContextSupport, ServerBuilder}
import scala.concurrent.ExecutionContext.Implicits.global

abstract class SslClasspathExample[F[_]: ConcurrentEffect] extends StreamApp[F] {

  def loadContextFromClasspath(keystorePassword: String, keyManagerPass: String): F[SSLContext] =
    Sync[F].delay {

      val ksStream = this.getClass.getResourceAsStream("/server.jks")
      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, keystorePassword.toCharArray)
      ksStream.close()

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
          .getOrElse(KeyManagerFactory.getDefaultAlgorithm))

      kmf.init(ks, keyManagerPass.toCharArray)

      val context = SSLContext.getInstance("TLS")
      context.init(kmf.getKeyManagers, null, null)

      context
    }

  def builder: ServerBuilder[F] with SSLContextSupport[F]

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    for {
      context <- Stream.eval(loadContextFromClasspath("password", "secure"))
      timer = Timer.derive[F]
      exitCode <- builder
        .withSSLContext(context)
        .bindHttp(8443, "0.0.0.0")
        .mountService(HSTS(new ExampleService[F].service(timer)), "/http4s")
        .serve
    } yield exitCode

}
