/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.blaze

import cats.effect.{IO, Resource}
import fs2.io.tls.TLSParameters
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.net.ssl._
import org.http4s.dsl.io._
import org.http4s.server.{Server, ServerRequestKeys}
import org.http4s.{Http4sSpec, HttpApp}
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try
import scala.concurrent.ExecutionContext.global
import org.http4s.testing.SilenceOutputStream

/**
  * Test cases for mTLS support in blaze server
  */
class BlazeServerMtlsSpec extends Http4sSpec with SilenceOutputStream {
  {
    val hostnameVerifier: HostnameVerifier = new HostnameVerifier {
      override def verify(s: String, sslSession: SSLSession): Boolean = true
    }

    //For test cases, don't do any host name verification. Certificates are self-signed and not available to all hosts
    HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier)
  }

  def builder: BlazeServerBuilder[IO] =
    BlazeServerBuilder[IO](global)
      .withResponseHeaderTimeout(1.second)

  val service: HttpApp[IO] = HttpApp {
    case req @ GET -> Root / "dummy" =>
      val output = req.attributes
        .lookup(ServerRequestKeys.SecureSession)
        .flatten
        .map { session =>
          session.sslSessionId shouldNotEqual ""
          session.cipherSuite shouldNotEqual ""
          session.keySize shouldNotEqual 0

          session.X509Certificate.head.getSubjectX500Principal.getName
        }
        .getOrElse("Invalid")

      Ok(output)

    case req @ GET -> Root / "noauth" =>
      req.attributes
        .lookup(ServerRequestKeys.SecureSession)
        .flatten
        .foreach { session =>
          session.sslSessionId shouldNotEqual ""
          session.cipherSuite shouldNotEqual ""
          session.keySize shouldNotEqual 0
          session.X509Certificate.size shouldEqual 0
        }

      Ok("success")

    case _ => NotFound()
  }

  def serverR(sslParameters: SSLParameters): Resource[IO, Server] =
    builder
      .bindAny()
      .withSslContextAndParameters(sslContext, sslParameters)
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

  /**
    * Used for no mTLS client. Required to trust self-signed certificate.
    */
  lazy val noAuthClientContext: SSLContext = {
    val js = KeyStore.getInstance("JKS")
    js.load(getClass.getResourceAsStream("/keystore.jks"), "password".toCharArray)

    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(js)

    val sc = SSLContext.getInstance("TLSv1.2")
    sc.init(null, tmf.getTrustManagers, null)

    sc
  }

  /**
    * Test "required" auth mode
    */
  withResource(serverR(TLSParameters(needClientAuth = true).toSSLParameters)) { server =>
    def get(path: String, clientAuth: Boolean = true): String = {
      val url = new URL(s"https://localhost:${server.address.getPort}$path")
      val conn = url.openConnection().asInstanceOf[HttpsURLConnection]
      conn.setRequestMethod("GET")

      if (clientAuth)
        conn.setSSLSocketFactory(sslContext.getSocketFactory)
      else
        conn.setSSLSocketFactory(noAuthClientContext.getSocketFactory)

      Try {
        Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines().mkString
      }.recover {
        case ex: Throwable =>
          ex.getMessage
      }.toOption
        .getOrElse("")
    }

    "Server" should {
      "send mTLS request correctly" in {
        get("/dummy") shouldEqual "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US"
      }

      "fail for invalid client auth" in {
        get("/dummy", clientAuth = false) shouldNotEqual "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US"
      }
    }
  }

  /**
    * Test "requested" auth mode
    */
  withResource(serverR(TLSParameters(wantClientAuth = true).toSSLParameters)) { server =>
    def get(path: String, clientAuth: Boolean = true): String = {
      val url = new URL(s"https://localhost:${server.address.getPort}$path")
      val conn = url.openConnection().asInstanceOf[HttpsURLConnection]
      conn.setRequestMethod("GET")

      if (clientAuth)
        conn.setSSLSocketFactory(sslContext.getSocketFactory)
      else
        conn.setSSLSocketFactory(noAuthClientContext.getSocketFactory)

      Try {
        Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines().mkString
      }.recover {
        case ex: Throwable =>
          ex.getMessage
      }.toOption
        .getOrElse("")
    }

    "Server" should {
      "send mTLS request correctly with optional auth" in {
        get("/dummy") shouldEqual "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US"
      }

      "send mTLS request correctly without clientAuth" in {
        get("/noauth", clientAuth = false) shouldEqual "success"
      }
    }
  }
}
