package com.example.http4s.ssl

import cats.effect.{Effect, Sync}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import com.example.http4s.ExampleService
import fs2.StreamApp.ExitCode
import fs2.{Scheduler, Stream, StreamApp}
import org.http4s.server.middleware.HSTS
import org.http4s.server.{SSLContextSupport, ServerBuilder}
import java.security.{KeyStore, Security}

abstract class SslClasspathExample[F[_]: Effect] extends StreamApp[F] {

  def loadContextFromClasspath(keystorePassword: String, keyManagerPass: String): F[SSLContext] = Sync[F].delay {

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
      scheduler <- Scheduler(corePoolSize = 2)
      context <- Stream.eval(loadContextFromClasspath("password", "secure"))
      exitCode <- builder
        .withSSLContext(context)
        .bindHttp(8443, "0.0.0.0")
        .mountService(HSTS(new ExampleService[F].service(scheduler)), "/http4s")
        .serve
    } yield exitCode

}
