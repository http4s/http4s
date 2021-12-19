/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server

import cats.effect._
import cats.implicits._
import fs2.io.tls.TLSContext
import fs2.io.tls.TLSParameters
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits._
import org.http4s.server.ServerRequestKeys

import java.io.IOException
import java.security.KeyStore
import javax.net.ssl._

/** Test cases for mTLS support in Ember Server.
  */
class EmberServerMtlsSuite extends Http4sSuite {

  val clientCertCn = "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US"
  val expectedNoAuthResponse = "success"

  def service[F[_]](implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case req @ GET -> Root / "dummy" =>
          val output = req.attributes
            .lookup(ServerRequestKeys.SecureSession)
            .flatten
            .map { session =>
              assertNotEquals(session.sslSessionId, "")
              assertNotEquals(session.cipherSuite, "")
              assertNotEquals(session.keySize, 0)

              session.X509Certificate.head.getSubjectX500Principal.getName
            }
            .getOrElse("Invalid")
          Ok(output)
        case req @ GET -> Root / "noauth" =>
          req.attributes
            .lookup(ServerRequestKeys.SecureSession)
            .flatten
            .foreach { session =>
              assertNotEquals(session.sslSessionId, "")
              assertNotEquals(session.cipherSuite, "")
              assertNotEquals(session.keySize, 0)
              assert(session.X509Certificate.isEmpty)
            }

          Ok("success")
      }
      .orNotFound
  }

  lazy val authTlsClientContext: Resource[IO, TLSContext] =
    Resource.eval(
      TLSContext.fromKeyStoreResource[IO](
        "keystore.jks",
        "password".toCharArray,
        "password".toCharArray,
        testBlocker,
      )
    )

  lazy val noAuthClientContext: SSLContext = {
    val js = KeyStore.getInstance("JKS")
    js.load(getClass.getResourceAsStream("/keystore.jks"), "password".toCharArray)

    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(js)

    val sc = SSLContext.getInstance("TLSv1.2")
    sc.init(null, tmf.getTrustManagers, null)

    sc
  }

  lazy val noAuthTlsClientContext: Resource[IO, TLSContext] =
    Resource.eval(
      TLSContext.fromSSLContext(noAuthClientContext, testBlocker).pure[IO]
    )

  def fixture(tlsParams: TLSParameters, clientTlsContext: Resource[IO, TLSContext]) =
    (server(tlsParams), client(clientTlsContext)).mapN(FunFixture.map2(_, _))

  def client(tlsContextResource: Resource[IO, TLSContext]) =
    ResourceFixture(clientResource(tlsContextResource))

  def clientResource(tlsContextResource: Resource[IO, TLSContext]) =
    for {
      tlsContext <- tlsContextResource
      emberClient <- EmberClientBuilder
        .default[IO]
        .withTLSContext(tlsContext)
        .withoutCheckEndpointAuthentication
        .build
    } yield emberClient

  def server(tlsParams: TLSParameters) =
    ResourceFixture(serverResource(tlsParams))

  def serverResource(tlsParams: TLSParameters) =
    for {
      tlsContext <- authTlsClientContext
      emberServer <- EmberServerBuilder
        .default[IO]
        .withHttpApp(service[IO])
        .withTLS(
          tlsContext,
          tlsParams,
        )
        .build
    } yield emberServer

  fixture(
    TLSParameters(needClientAuth = true, protocols = List("TLSv1.2").some),
    authTlsClientContext,
  ).test("Server should handle mTLS request correctly") { case (server, client) =>
    import org.http4s.dsl.io._
    import org.http4s.client.dsl.io._

    val uri = Uri
      .fromString(s"https://${server.address.getHostName}:${server.address.getPort}/dummy")
      .toOption
      .get
    val request = GET(uri)
    for {
      response <- client.fetchAs[String](request)
    } yield assertEquals(clientCertCn, response)
  }

  fixture(
    TLSParameters(needClientAuth = true, protocols = List("TLSv1.2").some),
    noAuthTlsClientContext,
  ).test("Server should fail for invalid client auth") { case (server, client) =>
    client
      .get(s"https://${server.address.getHostName}:${server.address.getPort}/dummy")(
        _.status.pure[IO]
      )
      .intercept[IOException]
  }

  fixture(
    TLSParameters(wantClientAuth = true, protocols = List("TLSv1.2").some),
    authTlsClientContext,
  ).test("Server should handle mTLS request correctly with optional auth") {
    case (server, client) =>
      import org.http4s.dsl.io._
      import org.http4s.client.dsl.io._

      val uri = Uri
        .fromString(s"https://${server.address.getHostName}:${server.address.getPort}/dummy")
        .toOption
        .get
      val request = GET(uri)
      for {
        response <- client.fetchAs[String](request)
      } yield assertEquals(clientCertCn, response)
  }

  fixture(
    TLSParameters(wantClientAuth = true, protocols = List("TLSv1.2").some),
    noAuthTlsClientContext,
  ).test("Server should handle mTLS request correctly without clientAuth") {
    case (server, client) =>
      import org.http4s.dsl.io._
      import org.http4s.client.dsl.io._

      val uri = Uri
        .fromString(s"https://${server.address.getHostName}:${server.address.getPort}/noauth")
        .toOption
        .get
      val request = GET(uri)
      for {
        response <- client.fetchAs[String](request)
      } yield assertEquals(expectedNoAuthResponse, response)
  }

}
