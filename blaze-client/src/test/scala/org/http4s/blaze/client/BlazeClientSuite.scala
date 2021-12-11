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

package org.http4s
package blaze
package client

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.syntax.all._
import fs2.Stream
import fs2.io.tcp.SocketGroup
import org.http4s.client.ConnectionFailure
import org.http4s.client.RequestKey
import org.http4s.syntax.all._

import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

class BlazeClientSuite extends BlazeClientBase {

  test(
    "Blaze Http1Client should raise error NoConnectionAllowedException if no connections are permitted for key"
  ) {
    val sslAddress = secureServer().addresses.head
    val name = sslAddress.host
    val port = sslAddress.port
    val u = Uri.fromString(s"https://$name:$port/simple").yolo
    val resp = builder(0).resource.use(_.expect[String](u).attempt)
    resp.assertEquals(Left(NoConnectionAllowedException(RequestKey(u.scheme.get, u.authority.get))))
  }

  test("Blaze Http1Client should make simple https requests") {
    val sslAddress = secureServer().addresses.head
    val name = sslAddress.host
    val port = sslAddress.port
    val u = Uri.fromString(s"https://$name:$port/simple").yolo
    val resp = builder(1).resource.use(_.expect[String](u))
    resp.map(_.length > 0).assert
  }

  test("Blaze Http1Client should reject https requests when no SSLContext is configured") {
    val sslAddress = secureServer().addresses.head
    val name = sslAddress.host
    val port = sslAddress.port
    val u = Uri.fromString(s"https://$name:$port/simple").yolo
    val resp = builder(1, sslContextOption = None).resource
      .use(_.expect[String](u))
      .attempt
    resp.map {
      case Left(_: ConnectionFailure) => true
      case _ => false
    }.assert
  }

  test("Blaze Http1Client should obey response header timeout") {

    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    builder(1, responseHeaderTimeout = 100.millis).resource
      .use { client =>
        val submit = client.expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        submit
      }
      .intercept[TimeoutException]
  }

  test("Blaze Http1Client should unblock waiting connections") {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    builder(1, responseHeaderTimeout = 20.seconds).resource
      .use { client =>
        val submit = client.expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        for {
          _ <- submit.start
          r <- submit.attempt
        } yield r
      }
      .map(_.isRight)
      .assert
  }

  test("Blaze Http1Client should drain waiting connections after shutdown") {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port

    val resp = builder(1, responseHeaderTimeout = 20.seconds).resource
      .use { drainTestClient =>
        drainTestClient
          .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
          .attempt
          .start

        val resp = drainTestClient
          .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
          .attempt
          .map(_.exists(_.nonEmpty))
          .start

        // Wait 100 millis to shut down
        IO.sleep(100.millis) *> resp.flatMap(_.join)
      }

    resp.assert
  }

  test(
    "Blaze Http1Client should stop sending data when the server sends response and closes connection"
  ) {
    // https://datatracker.ietf.org/doc/html/rfc2616#section-8.2.2
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    Deferred[IO, Unit]
      .flatMap { reqClosed =>
        builder(1, requestTimeout = 60.seconds).resource.use { client =>
          val body = Stream(0.toByte).repeat.onFinalizeWeak(reqClosed.complete(()))
          val req = Request[IO](
            method = Method.POST,
            uri = Uri.fromString(s"http://$name:$port/respond-and-close-immediately").yolo,
          ).withBodyStream(body)
          client.status(req) >> reqClosed.get
        }
      }
      .assertEquals(())
  }

  test(
    "Blaze Http1Client should stop sending data when the server sends response without body and closes connection"
  ) {
    // https://datatracker.ietf.org/doc/html/rfc2616#section-8.2.2
    // Receiving a response with and without body exercises different execution path in blaze client.

    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    Deferred[IO, Unit]
      .flatMap { reqClosed =>
        builder(1, requestTimeout = 60.seconds).resource.use { client =>
          val body = Stream(0.toByte).repeat.onFinalizeWeak(reqClosed.complete(()))
          val req = Request[IO](
            method = Method.POST,
            uri = Uri.fromString(s"http://$name:$port/respond-and-close-immediately-no-body").yolo,
          ).withBodyStream(body)
          client.status(req) >> reqClosed.get
        }
      }
      .assertEquals(())
  }

  test(
    "Blaze Http1Client should fail with request timeout if the request body takes too long to send"
  ) {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    builder(1, requestTimeout = 500.millis, responseHeaderTimeout = Duration.Inf).resource
      .use { client =>
        val body = Stream(0.toByte).repeat
        val req = Request[IO](
          method = Method.POST,
          uri = Uri.fromString(s"http://$name:$port/process-request-entity").yolo,
        ).withBodyStream(body)
        client.status(req)
      }
      .attempt
      .map {
        case Left(_: TimeoutException) => true
        case _ => false
      }
      .assert
  }

  test(
    "Blaze Http1Client should fail with response header timeout if the request body takes too long to send"
  ) {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    builder(1, requestTimeout = Duration.Inf, responseHeaderTimeout = 500.millis).resource
      .use { client =>
        val body = Stream(0.toByte).repeat
        val req = Request[IO](
          method = Method.POST,
          uri = Uri.fromString(s"http://$name:$port/process-request-entity").yolo,
        ).withBodyStream(body)
        client.status(req)
      }
      .attempt
      .map {
        case Left(_: TimeoutException) => true
        case _ => false
      }
      .assert
  }

  test("Blaze Http1Client should doesn't leak connection on timeout".flaky) {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    val uri = Uri.fromString(s"http://$name:$port/simple").yolo

    builder(1).resource
      .use { client =>
        val req = Request[IO](uri = uri)
        client
          .run(req)
          .use { _ =>
            IO.never
          }
          .timeout(250.millis)
          .attempt >>
          client.status(req)
      }
      .assertEquals(Status.Ok)
  }

  test("Blaze Http1Client should raise a ConnectionFailure when a host can't be resolved") {
    builder(1).resource
      .use { client =>
        client.status(Request[IO](uri = uri"http://example.invalid/"))
      }
      .attempt
      .map {
        case Left(e: ConnectionFailure) =>
          e.getMessage === "Error connecting to http://example.invalid using address example.invalid:80 (unresolved: true)"
        case _ => false
      }
      .assert
  }

  test("Blaze HTTP/1 client should raise a ResponseException when it receives an unexpected EOF") {
    SocketGroup[IO](testBlocker).use {
      _.serverResource[IO](new InetSocketAddress(0))
        .map { case (addr, sockets) =>
          val uri = Uri.fromString(s"http://[${addr.getHostName}]:${addr.getPort}/eof").yolo
          val req = Request[IO](uri = uri)
          (req, sockets)
        }
        .use { case (req, sockets) =>
          Stream
            .eval(builder(1).resource.use { client =>
              interceptMessageIO[SocketException](
                s"HTTP connection closed: ${RequestKey.fromRequest(req)}"
              )(client.expect[String](req))
            })
            .concurrently(sockets.evalMap(_.use(_.close)))
            .compile
            .drain
        }
    }
  }

  test("Keeps stats".flaky) {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port
    val uri = Uri.fromString(s"http://$name:$port/process-request-entity").yolo
    builder(1, requestTimeout = 2.seconds).resourceWithState.use { case (client, state) =>
      for {
        // We're not thoroughly exercising the pool stats.  We're doing a rudimentary check.
        _ <- state.allocated.assertEquals(Map.empty[RequestKey, Int])
        reading <- Deferred[IO, Unit]
        done <- Deferred[IO, Unit]
        body = Stream.eval(reading.complete(())) *> (Stream.empty: EntityBody[IO]) <* Stream.eval(
          done.get
        )
        req = Request[IO](Method.POST, uri = uri).withEntity(body)
        _ <- client.status(req).start
        _ <- reading.get
        _ <- state.allocated.map(_.get(RequestKey.fromRequest(req))).assertEquals(Some(1))
        _ <- done.complete(())
      } yield ()
    }
  }
}
