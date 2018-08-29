package com.example.http4s.ssl

import cats.effect._
import com.example.http4s.ExampleService
import fs2.{Stream}
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import org.http4s.server.middleware.HSTS
import org.http4s.server.{ServerBuilder, SSLContextSupport}

abstract class SslClasspathExample[F[_] : Effect](implicit timer: Timer[F], ctx: ContextShift[F]) {

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

  def sslStream =
    for {
      context <- Stream.eval(loadContextFromClasspath("password", "secure"))
      exitCode <- builder
        .withSSLContext(context)
        .bindHttp(8443, "0.0.0.0")
        .mountService(HSTS(new ExampleService[F].service(timer)), "/http4s")
        .serve
    } yield exitCode

}
