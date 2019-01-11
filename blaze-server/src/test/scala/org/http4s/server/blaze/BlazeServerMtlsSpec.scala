package org.http4s.server.blaze

import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore

import cats.effect.{IO, Resource}
import javax.net.ssl._
import org.http4s.dsl.io._
import org.http4s.server.{SSLClientAuthMode, Server, ServerRequestKeys}
import org.http4s.{Http4sSpec, HttpApp}

import scala.concurrent.duration._
import scala.io.Source

/**
  * Test cases for mTLS support in blaze server
  */
class BlazeServerMtlsSpec extends Http4sSpec {

  {
    val hostnameVerifier: HostnameVerifier = new HostnameVerifier {
      override def verify(s: String, sslSession: SSLSession): Boolean = true
    }

    //For test cases, don't do any host name verification. Certificates are self-signed and not available to all hosts
    HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier)
  }

  def builder: BlazeServerBuilder[IO] =
    BlazeServerBuilder[IO]
      .withResponseHeaderTimeout(1.second)
      .withExecutionContext(testExecutionContext)

  val service: HttpApp[IO] = HttpApp {
    case req @ GET -> Root / "dummy" =>
      val output = req
        .attributes(ServerRequestKeys.SecureSession)
        .map { session =>
          session.sslSessionId shouldNotEqual ""
          session.cipherSuite shouldNotEqual ""
          session.keySize shouldNotEqual 0

          session.X509Certificate.head.getSubjectX500Principal.getName
        }
        .getOrElse("Invalid")

      Ok(output)

    case _ => NotFound()
  }

  val serverR: Resource[IO, Server[IO]] =
    builder
      .bindAny()
      .withSSLContext(sslContext, clientAuth = SSLClientAuthMode.Required)
      .withHttpApp(service)
      .resource

  lazy val sslContext: SSLContext = {
    val ks = KeyStore.getInstance("JKS")
    ks.load(getClass.getResourceAsStream("/keystore.jks"), "password".toCharArray)

    val kmf = KeyManagerFactory.getInstance("SunX509")
    kmf.init(ks, "password".toCharArray)

    val js = KeyStore.getInstance("JKS")
    js.load(getClass.getResourceAsStream("/keystore.jks"), "password".toCharArray)

    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(js)

    val sc = SSLContext.getInstance("TLSv1.2")
    sc.init(kmf.getKeyManagers, tmf.getTrustManagers, null)

    sc
  }

  withResource(serverR) { server =>
    def get(path: String): String = {
      val url = new URL(s"https://localhost:${server.address.getPort}$path")
      val conn = url.openConnection().asInstanceOf[HttpsURLConnection]
      conn.setRequestMethod("GET")
      conn.setSSLSocketFactory(sslContext.getSocketFactory)

      Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines.mkString
    }

    "Server" should {
      "send mTLS request correctly" in {
        get("/dummy") shouldEqual "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US"
      }
    }
  }
}
