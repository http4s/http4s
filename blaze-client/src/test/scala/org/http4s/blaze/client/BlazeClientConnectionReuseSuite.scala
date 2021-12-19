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
import cats.implicits._
import fs2.Stream
import org.http4s.Method._
import org.http4s._
import org.http4s.client.scaffold.TestServer

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class BlazeClientConnectionReuseSuite extends BlazeClientBase {
  override def munitTimeout: Duration = new FiniteDuration(50, TimeUnit.SECONDS)

  test("BlazeClient should reuse the connection after a simple successful request".flaky) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient should reuse the connection after a successful request with large response".fail
  ) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "large"))
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient.status should reuse the connection after receiving a response without an entity".flaky
  ) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client.status(Request[IO](GET, servers(0).uri / "no-content"))
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
      } yield ()
    }
  }

  // BlazeClient.status may or may not reuse the connection after receiving a response with an entity.
  // It's up to the implementation.
  // The connection can be reused only if the entity has been fully read from the socket.
  // The current BlazeClient implementation will reuse the connection if it read the entire entity while reading the status line and headers.
  // This behaviour depends on `BlazeClientBuilder.bufferSize`.
  // In particular, responses not bigger than `bufferSize` will lead to reuse of the connection.

  test(
    "BlazeClient.status shouldn't wait for an infinite response entity and shouldn't reuse the connection"
  ) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client
          .status(Request[IO](GET, servers(0).uri / "infinite"))
          .timeout(5.seconds) // we expect it to complete without waiting for the response body
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(2L)
      } yield ()
    }
  }

  test("BlazeClient should reuse connections to different servers separately".flaky) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
        _ <- servers(1).establishedConnections.assertEquals(0L)
        _ <- client.expect[String](Request[IO](GET, servers(1).uri / "simple"))
        _ <- client.expect[String](Request[IO](GET, servers(1).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
        _ <- servers(1).establishedConnections.assertEquals(1L)
      } yield ()
    }
  }

  // // Decoding failures // //

  test("BlazeClient should reuse the connection after response decoding failed".flaky) {
    // This will work regardless of whether we drain the entity or not,
    // because the response is small and it is read in full in first read operation
    val drainThenFail = EntityDecoder.error[IO, String](new Exception())
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client
          .expect[String](Request[IO](GET, servers(0).uri / "simple"))(drainThenFail)
          .attempt
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient should reuse the connection after response decoding failed and the (large) entity was drained".fail
  ) {
    val drainThenFail = EntityDecoder.error[IO, String](new Exception())
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client
          .expect[String](Request[IO](GET, servers(0).uri / "large"))(drainThenFail)
          .attempt
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient shouldn't reuse the connection after response decoding failed and the (large) entity wasn't drained"
  ) {
    val failWithoutDraining = new EntityDecoder[IO, String] {
      override def decode(m: Media[IO], strict: Boolean): DecodeResult[IO, String] =
        DecodeResult[IO, String](IO.raiseError(new Exception()))
      override def consumes: Set[MediaRange] = Set.empty
    }
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client
          .expect[String](Request[IO](GET, servers(0).uri / "large"))(failWithoutDraining)
          .attempt
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(2L)
      } yield ()
    }
  }

  // // Requests with an entity // //

  test("BlazeClient should reuse the connection after a request with an entity".flaky) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client.expect[String](
          Request[IO](POST, servers(0).uri / "process-request-entity").withEntity("entity")
        )
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient shouldn't wait for the request entity transfer to complete if the server closed the connection early. The closed connection shouldn't be reused.".flaky
  ) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client.expect[String](
          Request[IO](POST, servers(0).uri / "respond-and-close-immediately")
            .withBodyStream(Stream(0.toByte).repeat)
        )
        _ <- client.expect[String](Request[IO](GET, servers(0).uri / "simple"))
        _ <- servers(0).establishedConnections.assertEquals(2L)
      } yield ()
    }
  }

  // // Load tests // //

  test(
    "BlazeClient should keep reusing connections even when under heavy load (single client scenario)".fail.flaky
  ) {
    builder().resource.use { client =>
      for {
        servers <- makeServers()
        _ <- client
          .expect[String](Request[IO](GET, servers(0).uri / "simple"))
          .replicateA(200)
          .parReplicateA(20)
        // There's no guarantee we'll actually manage to use 20 connections in parallel. Sharing the client means sharing the lock inside PoolManager as a contention point.
        _ <- servers(0).establishedConnections.map(_ <= 20L).assert
      } yield ()
    }
  }

  test(
    "BlazeClient should keep reusing connections even when under heavy load (multiple clients scenario)".fail.flaky
  ) {
    for {
      servers <- makeServers()
      _ <- builder().resource
        .use { client =>
          client.expect[String](Request[IO](GET, servers(0).uri / "simple")).replicateA(400)
        }
        .parReplicateA(20)
      _ <- servers(0).establishedConnections.assertEquals(20L)
    } yield ()
  }

  private def builder(): BlazeClientBuilder[IO] =
    BlazeClientBuilder[IO](munitExecutionContext).withScheduler(scheduler = tickWheel)

  private def makeServers(): IO[Vector[TestServer[IO]]] = {
    val testServers = server().servers
    testServers
      .traverse(_.resetEstablishedConnections)
      .as(testServers)
  }
}
