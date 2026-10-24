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
import cats.syntax.all._
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.h2.H2Keys
import org.http4s.server.SecureSession
import org.http4s.server.Server
import org.http4s.server.ServerRequestKeys

import java.security.KeyStore
import javax.net.ssl._
import scala.concurrent.duration._

/** HTTP/2 requests over TLS must carry the same connection attributes as HTTP/1.1:
  * ConnectionInfo with secure = true, and a SecureSession with the client certificate.
  *
  * The cleartext cases are in [[EmberServerH2ConnectionAttributesSuite]].
  */
class EmberServerH2TLSConnectionAttributesSuite
    extends Http4sSuite
    with ConnectionAttributesHelpers {

  private val clientCertCn = "CN=Test,OU=Test,O=Test,L=CA,ST=CA,C=US"

  private lazy val keyStore: KeyStore = {
    val ks = KeyStore.getInstance("JKS")
    ks.load(getClass.getResourceAsStream("/keystore.jks"), "password".toCharArray)
    ks
  }

  private lazy val trustManagers: Array[TrustManager] = {
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(keyStore)
    tmf.getTrustManagers
  }

  // Presents the keystore's certificate and trusts it
  private lazy val certTls: TLSContext[IO] = {
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, "password".toCharArray)
    val sc = SSLContext.getInstance("TLS")
    sc.init(kmf.getKeyManagers, trustManagers, null)
    TLSContext.Builder.forAsync[IO].fromSSLContext(sc)
  }

  // Trusts the server but presents no client certificate
  private lazy val noCertTls: TLSContext[IO] = {
    val sc = SSLContext.getInstance("TLS")
    sc.init(null, trustManagers, null)
    TLSContext.Builder.forAsync[IO].fromSSLContext(sc)
  }

  private def tlsServer(
      params: TLSParameters
  ): EmberServerBuilder[IO] => EmberServerBuilder[IO] =
    _.withTLS(certTls, params)

  private def tlsClient(
      tls: TLSContext[IO]
  ): EmberClientBuilder[IO] => EmberClientBuilder[IO] =
    _.withTLSContext(tls).withoutCheckEndpointAuthentication

  private val requireClientCert = tlsServer(TLSParameters(needClientAuth = true))

  private def secureSession(req: Request[IO]): Option[SecureSession] =
    req.attributes.lookup(ServerRequestKeys.SecureSession).flatten

  private def clientCertName(req: Request[IO]): Option[String] =
    secureSession(req)
      .flatMap(_.X509Certificate.headOption)
      .map(_.getSubjectX500Principal.getName)

  test("HTTP/2 over TLS with a client certificate exposes ConnectionInfo and SecureSession") {
    requestsSeenBy(requireClientCert, tlsClient(certTls).andThen(_.withHttp2), identity).map {
      case (server, reqs) =>
        val req = reqs.head
        assertEquals(req.httpVersion, HttpVersion.`HTTP/2`)
        assertLoopbackConnection(req, server)
        assertEquals(req.isSecure, Some(true))
        val session = secureSession(req).getOrElse(fail("SecureSession missing"))
        assertNotEquals(session.sslSessionId, "")
        assertNotEquals(session.cipherSuite, "")
        assertNotEquals(session.keySize, 0)
        assertEquals(clientCertName(req), Some(clientCertCn))
        assertStreamAttributesKept(req)
    }
  }

  test("HTTP/2 over TLS without a client certificate has a SecureSession with no certificates") {
    requestsSeenBy(
      tlsServer(TLSParameters(wantClientAuth = true)),
      tlsClient(noCertTls).andThen(_.withHttp2),
      identity,
    ).map { case (server, reqs) =>
      val req = reqs.head
      assertEquals(req.httpVersion, HttpVersion.`HTTP/2`)
      assertLoopbackConnection(req, server)
      assertEquals(req.isSecure, Some(true))
      val session = secureSession(req).getOrElse(fail("SecureSession missing"))
      assertEquals(session.X509Certificate, Nil)
    }
  }

  test("every stream on one HTTP/2 TLS connection carries the client certificate") {
    requestsSeenBy(
      requireClientCert,
      tlsClient(certTls).andThen(_.withHttp2),
      identity,
      requests = 10,
    ).map { case (server, reqs) =>
      assertEquals(reqs.size, 10)
      // All requests went over one connection, on distinct streams
      assertEquals(reqs.map(connectionInfo(_).remote).distinct.size, 1)
      assertEquals(reqs.flatMap(_.attributes.lookup(H2Keys.StreamIdentifier)).distinct.size, 10)
      reqs.foreach { req =>
        assertLoopbackConnection(req, server)
        assertEquals(req.isSecure, Some(true))
        assertEquals(clientCertName(req), Some(clientCertCn))
      }
    }
  }

  test("HTTP/2 over TLS matches HTTP/1.1 over TLS") {
    (
      requestsSeenBy(requireClientCert, tlsClient(certTls).andThen(_.withHttp2), identity),
      requestsSeenBy(requireClientCert, tlsClient(certTls), identity),
    ).mapN { case ((_, h2), (_, h1)) =>
      assertEquals(h2.head.httpVersion, HttpVersion.`HTTP/2`)
      assertEquals(h1.head.httpVersion, HttpVersion.`HTTP/1.1`)
      assertEquals(h2.head.isSecure, h1.head.isSecure)
      assertEquals(
        secureSession(h2.head).map(_.X509Certificate),
        secureSession(h1.head).map(_.X509Certificate),
      )
    }
  }

  test("HTTP/2 push promise requests carry ConnectionInfo and SecureSession") {
    // The app needs the server's port to build the pushed request's URI, but the
    // server only knows its port once it is bound, so it is handed over afterwards.
    (Deferred[IO, Server], Deferred[IO, Request[IO]]).flatMapN { (boundServer, pushedReq) =>
      val app = HttpApp[IO] { req =>
        req.uri.path.renderString match {
          case "/pushed" => pushedReq.complete(req).as(Response[IO](Status.Ok))
          case _ =>
            boundServer.get.map { server =>
              Response[IO](Status.Ok).withAttribute(
                H2Keys.PushPromises,
                Request[fs2.Pure](Method.GET, uri(server) / "pushed") :: Nil,
              )
            }
        }
      }
      val pushClient = tlsClient(certTls).andThen(
        _.withHttp2.withPushPromiseSupport { case (_, resp) =>
          resp
            .flatMap(_.body.compile.drain)
            .as(Outcome.succeeded[IO, Throwable, Unit](IO.unit))
        }
      )
      (server(app, requireClientCert), client(pushClient)).tupled.use { case (server, client) =>
        boundServer.complete(server) >>
          client
            .status(Request[IO](Method.GET, uri(server)))
            .timeout(10.seconds)
            .assertEquals(Status.Ok) >>
          pushedReq.get.timeout(10.seconds).map { req =>
            assertLoopbackConnection(req, server)
            assertEquals(req.isSecure, Some(true))
            assertEquals(clientCertName(req), Some(clientCertCn))
          }
      }
    }
  }
}
