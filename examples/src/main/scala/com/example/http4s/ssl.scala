package com.example.http4s

import cats.effect.Sync
import cats.implicits._
import java.nio.file.Paths
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import org.http4s.HttpApp
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Host, Location}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo

object ssl {

  val keystorePassword: String = "password"
  val keyManagerPassword: String = "secure"

  val keystorePath: String = Paths.get("../server.jks").toAbsolutePath.toString

  val storeInfo: StoreInfo = StoreInfo(keystorePath, keystorePassword)

  def loadContextFromClasspath[F[_]](keystorePassword: String, keyManagerPass: String)(
      implicit F: Sync[F]): F[SSLContext] =
    F.delay {
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

  def redirectApp[F[_]: Sync](securePort: Int): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpApp[F] { request =>
      request.headers.get(Host) match {
        case Some(Host(host @ _, _)) =>
          val baseUri = request.uri.copy(
            scheme = Scheme.https.some,
            authority = Some(
              Authority(
                userInfo = request.uri.authority.flatMap(_.userInfo),
                host = RegName(host),
                port = securePort.some)))
          MovedPermanently(Location(baseUri.withPath(request.uri.path)))
        case _ =>
          BadRequest()
      }
    }
  }

}
