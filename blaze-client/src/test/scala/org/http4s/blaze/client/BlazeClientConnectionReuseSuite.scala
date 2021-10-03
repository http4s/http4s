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

import cats.implicits._
import cats.effect._
import fs2.Stream
import org.http4s.Method.{GET, POST}
import org.http4s._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class BlazeClientConnectionReuseSuite extends BlazeClientBase {
  override def munitTimeout: Duration = new FiniteDuration(50, TimeUnit.SECONDS)

  test("BlazeClient should the reuse connection after a simple successful request".flaky) {
    val servers = makeServers()
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient should reuse the connection after a successful request with large response".fail) {
    val servers = makeServers()
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.expect[String](Request[IO](GET, servers(0) / "large"))
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient.status shouldn't wait for the response entity, but it may reuse the connection if the response entity was fully read nonetheless".flaky) {
    val servers = makeServers()
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.status(Request[IO](GET, servers(0) / "simple"))
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient.status shouldn't wait for the response entity and shouldn't reuse the connection if the response entity wasn't fully read") {
    val servers = makeServers()
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client
          .status(Request[IO](GET, servers(0) / "huge"))
          .timeout(5.seconds) // we expect it to complete without waiting for the response body
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(2L)
      } yield ()
    }
  }

  test("BlazeClient should reuse connection to different servers separately".flaky) {
    val servers = makeServers()
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(1L)
        _ <- client.expect[String](Request[IO](GET, servers(1) / "simple"))
        _ <- client.expect[String](Request[IO](GET, servers(1) / "simple"))
        _ <- state.totalAllocations.assertEquals(2L)
      } yield ()
    }
  }

  //// Decoding failures ////

  test("BlazeClient should reuse the connection after response decoding failed".flaky) {
    // This will work regardless of whether we drain the entity or not,
    // because the response is small and it is read in full in first read operation
    val servers = makeServers()
    val drainThenFail = EntityDecoder.error[IO, String](new Exception())
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))(drainThenFail).attempt
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient should reuse the connection after response decoding failed and the (large) entity was drained".fail) {
    val servers = makeServers()
    val drainThenFail = EntityDecoder.error[IO, String](new Exception())
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.expect[String](Request[IO](GET, servers(0) / "large"))(drainThenFail).attempt
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient shouldn't reuse the connection after response decoding failed and the (large) entity wasn't drained") {
    val servers = makeServers()
    val failWithoutDraining = new EntityDecoder[IO, String] {
      override def decode(m: Media[IO], strict: Boolean): DecodeResult[IO, String] =
        DecodeResult[IO, String](IO.raiseError(new Exception()))
      override def consumes: Set[MediaRange] = Set.empty
    }
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client
          .expect[String](Request[IO](GET, servers(0) / "large"))(failWithoutDraining)
          .attempt
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(2L)
      } yield ()
    }
  }

  //// Requests with an entity ////

  test("BlazeClient should reuse the connection after a request with an entity".flaky) {
    val servers = makeServers()
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.expect[String](
          Request[IO](POST, servers(0) / "process-request-entity").withEntity("entity"))
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(1L)
      } yield ()
    }
  }

  test(
    "BlazeClient shouldn't wait for the request entity transfer to complete if the server closed the connection early. The closed connection shouldn't be reused.") {
    val servers = makeServers()
    builder().resourceWithState.use { case (client, state) =>
      for {
        _ <- client.expect[String](
          Request[IO](POST, servers(0) / "respond-and-close-immediately")
            .withBodyStream(Stream(0.toByte).repeat))
        _ <- client.expect[String](Request[IO](GET, servers(0) / "simple"))
        _ <- state.totalAllocations.assertEquals(2L)
      } yield ()
    }
  }

  //// Load tests ////

  test("BlazeClient should keep reusing connections even when under heavy load".fail) {
    val servers = makeServers()
    builder().resourceWithState
      .use { case (client, state) =>
        for {
          _ <- client.expect[String](Request[IO](GET, servers(0) / "simple")).replicateA(400)
          _ <- state.totalAllocations.assertEquals(1L)
        } yield ()
      }
      .parReplicateA(20)
  }

  private def builder(): BlazeClientBuilder[IO] =
    BlazeClientBuilder[IO](munitExecutionContext).withScheduler(scheduler = tickWheel)

  private def makeServers(): Vector[Uri] = jettyServer().addresses.map { address =>
    val name = address.getHostName
    val port = address.getPort
    Uri.fromString(s"http://$name:$port").yolo
  }

  private implicit class ParReplicateASyntax[A](ioa: IO[A]) {
    def parReplicateA(n: Int): IO[List[A]] = List.fill(n)(ioa).parSequence
  }
}
