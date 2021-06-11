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

package org.http4s.blaze
package client

import cats.effect._
import cats.syntax.all._
import fs2.Stream
import java.util.concurrent.TimeoutException
import org.http4s._
import org.http4s.client.{ConnectionFailure, RequestKey}
import org.http4s.syntax.all._
import scala.concurrent.duration._

class BlazeClientSuite extends BlazeClientBase {

  test(
    "Blaze Http1Client should raise error NoConnectionAllowedException if no connections are permitted for key") {
    val sslAddress = secureServer().addresses.head
    val name = sslAddress.getHostName
    val port = sslAddress.getPort
    val u = Uri.fromString(s"https://$name:$port/simple").yolo
    val resp = mkClient(0).use(_.expect[String](u).attempt)
    resp.assertEquals(Left(NoConnectionAllowedException(RequestKey(u.scheme.get, u.authority.get))))
  }

  test("Blaze Http1Client should make simple https requests") {
    val sslAddress = secureServer().addresses.head
    val name = sslAddress.getHostName
    val port = sslAddress.getPort
    val u = Uri.fromString(s"https://$name:$port/simple").yolo
    val resp = mkClient(1).use(_.expect[String](u))
    resp.map(_.length > 0).assertEquals(true)
  }

  test("Blaze Http1Client should reject https requests when no SSLContext is configured") {
    val sslAddress = secureServer().addresses.head
    val name = sslAddress.getHostName
    val port = sslAddress.getPort
    val u = Uri.fromString(s"https://$name:$port/simple").yolo
    val resp = mkClient(1, sslContextOption = None)
      .use(_.expect[String](u))
      .attempt
    resp
      .map {
        case Left(_: ConnectionFailure) => true
        case _ => false
      }
      .assertEquals(true)
  }

  test("Blaze Http1Client should obey response header timeout") {
    val addresses = server().addresses
    val address = addresses(0)
    val name = address.getHostName
    val port = address.getPort
    mkClient(1, responseHeaderTimeout = 100.millis)
      .use { client =>
        val submit = client.expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        submit
      }
      .intercept[TimeoutException]
  }

  test("Blaze Http1Client should unblock waiting connections") {
    val addresses = server().addresses
    val address = addresses(0)
    val name = address.getHostName
    val port = address.getPort
    mkClient(1, responseHeaderTimeout = 20.seconds)
      .use { client =>
        val submit = client.expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        for {
          _ <- submit.start
          r <- submit.attempt
        } yield r
      }
      .map(_.isRight)
      .assertEquals(true)
  }

  test("Blaze Http1Client should drain waiting connections after shutdown") {
    val addresses = server().addresses
    val address = addresses(0)
    val name = address.getHostName
    val port = address.getPort

    val resp = mkClient(1, responseHeaderTimeout = 20.seconds)
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
        IO.sleep(100.millis) *> resp.flatMap(_.joinWithNever)
      }

    resp.assertEquals(true)
  }

  test("Blaze Http1Client should cancel infinite request on completion".ignore) {
    val addresses = server().addresses
    val address = addresses(0)
    val name = address.getHostName
    val port = address.getPort
    Deferred[IO, Unit]
      .flatMap { reqClosed =>
        mkClient(1, requestTimeout = 10.seconds).use { client =>
          val body = Stream(0.toByte).repeat.onFinalizeWeak[IO](reqClosed.complete(()).void)
          val req = Request[IO](
            method = Method.POST,
            uri = Uri.fromString(s"http://$name:$port/").yolo
          ).withBodyStream(body)
          client.status(req) >> reqClosed.get
        }
      }
      .assertEquals(())
  }

  test("Blaze Http1Client should doesn't leak connection on timeout") {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.getHostName
    val port = address.getPort
    val uri = Uri.fromString(s"http://$name:$port/simple").yolo

    mkClient(1)
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
    mkClient(1)
      .use { client =>
        client.status(Request[IO](uri = uri"http://example.invalid/"))
      }
      .interceptMessage[ConnectionFailure](
        "Error connecting to http://example.invalid using address example.invalid:80 (unresolved: true)")
  }
}
