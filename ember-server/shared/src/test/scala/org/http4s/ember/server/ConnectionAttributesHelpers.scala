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
import com.comcast.ip4s._
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.h2.H2Keys
import org.http4s.server.Server

import scala.concurrent.duration._

/** Helpers for checking which connection attributes reach the app.
  *
  * Requests are recorded and checked after the exchange, rather than inside the
  * app, so a failed assertion reports its own message instead of a 500.
  */
trait ConnectionAttributesHelpers { self: Http4sSuite =>

  /** Records every request it sees for testing. */
  protected def recordingApp(seen: Ref[IO, List[Request[IO]]]): HttpApp[IO] =
    HttpApp[IO](req => seen.update(req :: _).as(Response[IO](Status.Ok)))

  /** An HTTP/2-enabled server on loopback, customised by `setupServer`. */
  protected def server(
      app: HttpApp[IO],
      setupServer: EmberServerBuilder[IO] => EmberServerBuilder[IO],
  ): Resource[IO, Server] =
    setupServer(
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttp2
        .withHttpApp(app)
    ).build

  protected def client(
      setupClient: EmberClientBuilder[IO] => EmberClientBuilder[IO]
  ): Resource[IO, Client[IO]] =
    setupClient(EmberClientBuilder.default[IO]).build

  protected def uri(server: Server): Uri = {
    val scheme = if (server.isSecure) "https" else "http"
    Uri.unsafeFromString(s"$scheme://127.0.0.1:${server.addressIp4s.port.value}/")
  }

  /** Sends `requests` concurrent GETs and returns the server and what the app saw. */
  protected def requestsSeenBy(
      setupServer: EmberServerBuilder[IO] => EmberServerBuilder[IO],
      setupClient: EmberClientBuilder[IO] => EmberClientBuilder[IO],
      setupRequest: Request[IO] => Request[IO],
      requests: Int = 1,
  ): IO[(Server, List[Request[IO]])] =
    Ref[IO].of(List.empty[Request[IO]]).flatMap { seen =>
      (server(recordingApp(seen), setupServer), client(setupClient)).tupled.use {
        case (server, client) =>
          val req = setupRequest(Request[IO](Method.GET, uri(server)))
          List
            .fill(requests)(client.status(req).timeout(10.seconds).assertEquals(Status.Ok))
            .parSequence_ >> seen.get.tupleLeft(server)
      }
    }

  protected def connectionInfo(req: Request[IO]): Request.Connection =
    req.attributes
      .lookup(Request.Keys.ConnectionInfo)
      .getOrElse(fail(s"ConnectionInfo missing on ${req.httpVersion} request"))

  protected def assertLoopbackConnection(req: Request[IO], server: Server): Unit = {
    val info = connectionInfo(req)
    assertEquals(info.local, server.addressIp4s)
    assertEquals(info.remote.host, ipv4"127.0.0.1": IpAddress)
    assertEquals(req.remote.map(_.host), Some(ipv4"127.0.0.1": IpAddress))
  }

  /** Connection attributes must not replace the per-stream ones. */
  protected def assertStreamAttributesKept(req: Request[IO]): Unit = {
    assert(req.attributes.contains(H2Keys.StreamIdentifier), "StreamIdentifier was dropped")
    assert(
      req.attributes.contains(Message.Keys.TrailerHeaders[IO]),
      "TrailerHeaders was dropped",
    )
  }
}
