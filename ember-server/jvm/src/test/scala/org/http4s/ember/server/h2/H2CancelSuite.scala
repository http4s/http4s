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

package org.http4s.ember.server.h2

import cats.effect._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.h2.H2Keys.Http2PriorKnowledge
import org.http4s.implicits._

import scala.concurrent.duration._

class H2CancelSuite extends Http4sSuite {

  def runScenario(cancelOnPeerReset: Boolean): IO[(Boolean, Boolean)] = {
    val routeDuration: FiniteDuration = 500.milli

    for {
      routeStarted <- Deferred[IO, Unit]
      cancelObserved <- Deferred[IO, Unit]
      routeCompleted <- Deferred[IO, Unit]

      app = HttpRoutes
        .of[IO] { case GET -> Root / "slow" =>
          (routeStarted.complete(()).void *>
            IO.sleep(routeDuration) *>
            routeCompleted.complete(()).void)
            .onCancel(cancelObserved.complete(()).void)
            .as(Response[IO](Status.Ok))
        }
        .orNotFound

      flags <- EmberServerBuilder
        .default[IO]
        .withHost(host"127.0.0.1")
        .withPort(port"0")
        .withHttp2
        .withHttp2CancelOnPeerReset(cancelOnPeerReset)
        .withHttpApp(app)
        .build
        .use { server =>
          EmberClientBuilder
            .default[IO]
            .withHttp2
            .build
            .use { client =>
              val uri = Uri.unsafeFromString(s"http://${server.addressIp4s}/slow")
              val req = Request[IO](Method.GET, uri)
                .withAttribute(Http2PriorKnowledge, ())

              client.run(req).use(_ => IO.never[Unit]).start.flatMap { fiber =>
                for {
                  _ <- routeStarted.get
                  _ <- fiber.cancel
                  _ <-
                    if (cancelOnPeerReset) cancelObserved.get
                    else routeCompleted.get
                  cancel <- cancelObserved.tryGet
                  done <- routeCompleted.tryGet
                } yield (cancel.isDefined, done.isDefined)
              }
            }
        }
    } yield flags
  }

  test("server runs route to completion when http2CancelOnPeerReset is false (default)") {
    runScenario(cancelOnPeerReset = false).map { case (cancel, done) =>
      assertEquals(cancel, false, "route fiber should not have been canceled")
      assertEquals(done, true, "route should have completed normally")
    }
  }

  test("server cancels in-flight route when http2CancelOnPeerReset is true") {
    runScenario(cancelOnPeerReset = true).map { case (cancel, done) =>
      assertEquals(cancel, true, "route fiber should have been canceled")
      assertEquals(done, false, "route should not have completed normally")
    }
  }
}
