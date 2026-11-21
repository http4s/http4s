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
import fs2.io.net.tls.TLSParameters
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.h2.H2Keys
import org.http4s.server.SecureSession
import org.http4s.server.Server
import org.http4s.server.ServerRequestKeys
import scodec.bits.ByteVector

import scala.concurrent.duration._

/** Requests over TLS, on both HTTP/1.1 and HTTP/2, carry ConnectionInfo with
  * secure = true and a SecureSession with the client's certificate chain.
  *
  * The cleartext cases are in [[EmberServerH2ConnectionAttributesSuite]].
  */
class EmberServerTLSConnectionAttributesSuite
    extends Http4sSuite
    with ConnectionAttributesHelpers
    with TLSConnectionAttributesPlatform {

  private type Identity = Option[(String, String)]

  private val serverIdentity: Identity =
    Some((TestCertificates.serverPem, TestCertificates.serverKeyPem))
  private val clientIdentity: Identity =
    Some((TestCertificates.clientPem, TestCertificates.clientKeyPem))
  private val untrustedIdentity: Identity =
    Some((TestCertificates.untrustedClientPem, TestCertificates.untrustedClientKeyPem))

  /** Runs `f` with TLS server and client setups. The server presents the server
    * certificate and the client presents `identity`. Both trust the CA.
    */
  private def withTls[A](serverParams: TLSParameters, identity: Identity)(
      f: (
          EmberServerBuilder[IO] => EmberServerBuilder[IO],
          EmberClientBuilder[IO] => EmberClientBuilder[IO],
      ) => IO[A]
  ): IO[A] =
    (tlsContext(serverIdentity), tlsContext(identity)).tupled.use {
      case (serverContext, clientContext) =>
        f(
          _.withTLS(serverContext, serverParams),
          b => configureClient(b.withTLSContext(clientContext).withoutCheckEndpointAuthentication),
        )
    }

  private def requestsSeen(
      serverParams: TLSParameters,
      identity: Identity,
      http2: Boolean,
      requests: Int = 1,
  ): IO[List[Request[IO]]] =
    withTls(serverParams, identity) { (setupServer, setupClient) =>
      requestsSeenBy(
        setupServer,
        setupClient.andThen(b => if (http2) b.withHttp2 else b),
        setupRequest = r => r,
        requests = requests,
      ).map { case (server, reqs) =>
        reqs.foreach(assertSecure(_, server, http2))
        reqs
      }
    }

  private def assertSecure(req: Request[IO], server: Server, http2: Boolean): Unit = {
    val version = if (http2) HttpVersion.`HTTP/2` else HttpVersion.`HTTP/1.1`
    assertEquals(req.httpVersion, version)
    assertLoopbackConnection(req, server)
    assertEquals(req.isSecure, Some(true))
  }

  private def secureSession(req: Request[IO]): SecureSession =
    req.attributes
      .lookup(ServerRequestKeys.SecureSession)
      .flatten
      .getOrElse(fail(s"SecureSession missing on ${req.httpVersion} request"))

  private def peerCertificates(req: Request[IO]): List[ByteVector] =
    certificates(secureSession(req))

  for ((name, http2) <- List("HTTP/2" -> true, "HTTP/1.1" -> false)) {
    test(s"$name over TLS exposes the session and the client certificate chain") {
      requestsSeen(requireClientCert, clientIdentity, http2).map { reqs =>
        val req = reqs.head
        assertSessionDetails(secureSession(req))
        assertEquals(peerCertificates(req), clientChain)
        if (http2) assertStreamAttributesKept(req)
      }
    }

    test(s"$name over TLS without a client certificate has a SecureSession with no certificates") {
      requestsSeen(optionalClientCert, identity = None, http2).map { reqs =>
        assertEquals(peerCertificates(reqs.head), Nil)
      }
    }

    test(s"$name over TLS never exposes an untrusted client certificate") {
      withTls(optionalClientCert, untrustedIdentity) { (setupServer, setupClient) =>
        Ref[IO].of(List.empty[Request[IO]]).flatMap { seen =>
          val setup = setupClient.andThen(b => if (http2) b.withHttp2 else b)
          (server(recordingApp(seen), setupServer), client(setup)).tupled.use {
            case (server, client) =>
              // Short, as s2n delays reporting a failed handshake by 10-30 seconds
              client.status(Request[IO](Method.GET, uri(server))).timeout(2.seconds).attempt >>
                seen.get
          }
        }
      }.map { reqs =>
        if (acceptsUntrustedOptionalClientCert) {
          // The handshake succeeds, but the certificate was not verified, so it is not exposed
          assertEquals(reqs.size, 1)
          assertEquals(peerCertificates(reqs.head), Nil)
        } else
          assert(
            reqs.isEmpty,
            s"the app saw ${reqs.size} request(s) despite the untrusted certificate",
          )
      }
    }
  }

  test("every stream on one HTTP/2 TLS connection carries the client certificate chain") {
    requestsSeen(requireClientCert, clientIdentity, http2 = true, requests = 10).map { reqs =>
      assertEquals(reqs.size, 10)
      // All requests went over one connection, on distinct streams
      assertEquals(reqs.map(connectionInfo(_).remote).distinct.size, 1)
      assertEquals(reqs.flatMap(_.attributes.lookup(H2Keys.StreamIdentifier)).distinct.size, 10)
      reqs.foreach(req => assertEquals(peerCertificates(req), clientChain))
    }
  }

  test("every request on one HTTP/1.1 keep-alive connection carries the client certificate chain") {
    withTls(requireClientCert, clientIdentity) { (setupServer, setupClient) =>
      Ref[IO].of(List.empty[Request[IO]]).flatMap { seen =>
        (server(recordingApp(seen), setupServer), client(setupClient)).tupled.use {
          case (server, client) =>
            client
              .status(Request[IO](Method.GET, uri(server)))
              .timeout(10.seconds)
              .assertEquals(Status.Ok)
              .replicateA_(5) >> seen.get.tupleLeft(server)
        }
      }
    }.map { case (server, reqs) =>
      assertEquals(reqs.size, 5)
      // Sequential requests reuse the pooled connection
      assertEquals(reqs.map(connectionInfo(_).remote).distinct.size, 1)
      reqs.foreach { req =>
        assertSecure(req, server, http2 = false)
        assertEquals(peerCertificates(req), clientChain)
      }
    }
  }

  test("HTTP/2 push promise requests carry the client certificate chain") {
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
      withTls(requireClientCert, clientIdentity) { (setupServer, setupClient) =>
        val pushClient = setupClient.andThen(
          _.withHttp2.withPushPromiseSupport { case (_, resp) =>
            resp
              .flatMap(_.body.compile.drain)
              .as(Outcome.succeeded[IO, Throwable, Unit](IO.unit))
          }
        )
        (server(app, setupServer), client(pushClient)).tupled.use { case (server, client) =>
          boundServer.complete(server) >>
            client
              .status(Request[IO](Method.GET, uri(server)))
              .timeout(10.seconds)
              .assertEquals(Status.Ok) >>
            pushedReq.get.timeout(10.seconds).map { req =>
              assertLoopbackConnection(req, server)
              assertEquals(req.isSecure, Some(true))
              assertEquals(peerCertificates(req), clientChain)
            }
        }
      }
    }
  }
}
