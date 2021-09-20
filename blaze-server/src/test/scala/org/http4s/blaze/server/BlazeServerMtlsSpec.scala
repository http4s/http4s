/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.blaze.server

import cats.effect.{IO, Resource}
import fs2.io.net.tls.TLSParameters
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.net.ssl._
import org.http4s.dsl.io._
import org.http4s.server.{Server, ServerRequestKeys}
import org.http4s.testing.ErrorReporting
import org.http4s.{Http4sSuite, HttpApp}
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try

/** Test cases for mTLS support in blaze server
  */
class BlazeServerMtlsSpec extends Http4sSuite {
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
          assert(session.sslSessionId != "")
          assert(session.cipherSuite != "")
          assert(session.keySize != 0)

          session.X509Certificate.head.getSubjectX500Principal.getName
        }
        .getOrElse("Invalid")

      Ok(output)

    case req @ GET -> Root / "noauth" =>
      req.attributes
        .lookup(ServerRequestKeys.SecureSession)
        .flatten
        .foreach { session =>
          assert(session.sslSessionId != "")
          assert(session.cipherSuite != "")
          assert(session.keySize != 0)
          assert(session.X509Certificate.isEmpty)
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

  /** Used for no mTLS client. Required to trust self-signed certificate.
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

  def get(server: Server, path: String, clientAuth: Boolean = true): String =
    ErrorReporting.silenceOutputStreams {
      val url = new URL(s"https://${server.address}$path")
      val conn = url.openConnection().asInstanceOf[HttpsURLConnection]
      conn.setRequestMethod("GET")

      if (clientAuth)
        conn.setSSLSocketFactory(sslContext.getSocketFactory)
      else
        conn.setSSLSocketFactory(noAuthClientContext.getSocketFactory)

      Try {
        Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines().mkString
      }.recover { case ex: Throwable =>
        ex.getMessage
      }.toOption
        .getOrElse("")
    }

  def blazeServer(sslParameters: SSLParameters) =
    ResourceFixture(serverR(sslParameters))

  /** Test "required" auth mode
    */
  blazeServer(TLSParameters(needClientAuth = true).toSSLParameters)
    .test("Server should send mTLS request correctly") { server =>
      assertEquals(get(server, "/dummy", true), "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US")
    }
  blazeServer(TLSParameters(needClientAuth = true).toSSLParameters)
    .test("Server should fail for invalid client auth") { server =>
      assertNotEquals(get(server, "/dummy", false), "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US")
    }

  /** Test "requested" auth mode
    */
  blazeServer(TLSParameters(wantClientAuth = true).toSSLParameters)
    .test("Server should send mTLS request correctly with optional auth") { server =>
      assertEquals(get(server, "/dummy", true), "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US")
    }

  blazeServer(TLSParameters(wantClientAuth = true).toSSLParameters)
    .test("Server should send mTLS request correctly without clientAuth") { server =>
      assertEquals(get(server, "/noauth", false), "success")
    }

}
