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

package org.http4s.client
package blaze

import cats.effect._
import cats.syntax.all._
import fs2.Stream
import org.http4s._
import org.http4s.blaze.client.BlazeClientBase

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.Random

class BlazeClient213Suite extends BlazeClientBase {
  override def munitTimeout: Duration = new FiniteDuration(50, TimeUnit.SECONDS)

  test("reset request timeout".flaky) {
    val addresses = server().addresses
    val address = addresses.head
    val name = address.host
    val port = address.port

    Ref[IO]
      .of(0L)
      .flatMap { _ =>
        builder(1, requestTimeout = 2.second).resource.use { client =>
          val submit =
            client.status(Request[IO](uri = Uri.fromString(s"http://$name:$port/simple").yolo))
          submit *> IO.sleep(3.seconds) *> submit
        }
      }
      .assertEquals(Status.Ok)
  }

  test("Blaze Http1Client should behave and not deadlock") {
    val addresses = server().addresses
    val hosts = addresses.map { address =>
      val name = address.host
      val port = address.port
      Uri.fromString(s"http://$name:$port/simple").yolo
    }

    builder(3).resource
      .use { client =>
        (1 to Runtime.getRuntime.availableProcessors * 5).toList
          .parTraverse { _ =>
            val h = hosts(Random.nextInt(hosts.length))
            client.expect[String](h).map(_.nonEmpty)
          }
          .map(_.forall(identity))
      }
      .assertEquals(true)
  }

  test("behave and not deadlock on failures with parTraverse") {
    val addresses = server().addresses
    builder(3).resource
      .use { client =>
        val failedHosts = addresses.map { address =>
          val name = address.host
          val port = address.port
          Uri.fromString(s"http://$name:$port/internal-server-error").yolo
        }

        val successHosts = addresses.map { address =>
          val name = address.host
          val port = address.port
          Uri.fromString(s"http://$name:$port/simple").yolo
        }

        val failedRequests =
          (1 to Runtime.getRuntime.availableProcessors * 5).toList.parTraverse { _ =>
            val h = failedHosts(Random.nextInt(failedHosts.length))
            client.expect[String](h)
          }

        val successfulRequests =
          (1 to Runtime.getRuntime.availableProcessors * 5).toList.parTraverse { _ =>
            val h = successHosts(Random.nextInt(successHosts.length))
            client.expect[String](h).map(_.nonEmpty)
          }

        val allRequests = for {
          _ <- failedRequests.handleErrorWith(_ => IO.unit).replicateA(5)
          r <- successfulRequests
        } yield r

        allRequests
          .map(_.forall(identity))
      }
      .assertEquals(true)
  }

  test("Blaze Http1Client should behave and not deadlock on failures with parSequence".flaky) {
    val addresses = server().addresses
    builder(3).resource
      .use { client =>
        val failedHosts = addresses.map { address =>
          val name = address.host
          val port = address.port
          Uri.fromString(s"http://$name:$port/internal-server-error").yolo
        }

        val successHosts = addresses.map { address =>
          val name = address.host
          val port = address.port
          Uri.fromString(s"http://$name:$port/simple").yolo
        }

        val failedRequests = (1 to Runtime.getRuntime.availableProcessors * 5).toList.map { _ =>
          val h = failedHosts(Random.nextInt(failedHosts.length))
          client.expect[String](h)
        }.parSequence

        val sucessRequests = (1 to Runtime.getRuntime.availableProcessors * 5).toList.map { _ =>
          val h = successHosts(Random.nextInt(successHosts.length))
          client.expect[String](h).map(_.nonEmpty)
        }.parSequence

        val allRequests = for {
          _ <- failedRequests.handleErrorWith(_ => IO.unit).replicateA(5)
          r <- sucessRequests
        } yield r

        allRequests
          .map(_.forall(identity))
      }
      .assertEquals(true)
  }

  test("call a second host after reusing connections on a first") {
    val addresses = server().addresses
    // https://github.com/http4s/http4s/pull/2546
    builder(maxConnectionsPerRequestKey = Int.MaxValue, maxTotalConnections = 5).resource
      .use { client =>
        val uris = addresses.take(2).map { address =>
          val name = address.host
          val port = address.port
          Uri.fromString(s"http://$name:$port/simple").yolo
        }
        val s = Stream(
          Stream.eval(
            client.expect[String](Request[IO](uri = uris(0)))
          )
        ).repeat.take(10).parJoinUnbounded ++ Stream.eval(
          client.expect[String](Request[IO](uri = uris(1)))
        )
        s.compile.lastOrError
      }
      .assertEquals("simple path")
  }
}
