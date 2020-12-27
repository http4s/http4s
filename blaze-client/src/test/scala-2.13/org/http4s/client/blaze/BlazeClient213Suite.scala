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
import cats.effect.concurrent.Ref
import cats.syntax.all._
import fs2.Stream
import org.http4s._
import scala.concurrent.duration._
import scala.util.Random

class BlazeClient213Suite extends BlazeClientBase {

  jettyScaffold.test("reset request timeout") { case (jettyServer, _) =>
    val addresses = jettyServer.addresses
    val address = addresses(0)
    val name = address.getHostName
    val port = address.getPort

    Ref[IO]
      .of(0L)
      .flatMap { _ =>
        mkClient(1, requestTimeout = 2.second).use { client =>
          val submit =
            client.status(Request[IO](uri = Uri.fromString(s"http://$name:$port/simple").yolo))
          submit *> munitTimer.sleep(3.seconds) *> submit
        }
      }
      .assertEquals(Status.Ok)
  }

  jettyScaffold.test("Blaze Http1Client should behave and not deadlock") { case (jettyServer, _) =>
    val addresses = jettyServer.addresses
    val hosts = addresses.map { address =>
      val name = address.getHostName
      val port = address.getPort
      Uri.fromString(s"http://$name:$port/simple").yolo
    }

    mkClient(3).use { client =>
      (1 to Runtime.getRuntime.availableProcessors * 5).toList
        .parTraverse { _ =>
          val h = hosts(Random.nextInt(hosts.length))
          client.expect[String](h).map(_.nonEmpty)
        }
        .map(_.forall(identity))
    }.assert
  }

  jettyScaffold.test("behave and not deadlock on failures with parTraverse") {
    case (jettyServer, _) =>
      val addresses = jettyServer.addresses
      mkClient(3).use { client =>
        val failedHosts = addresses.map { address =>
          val name = address.getHostName
          val port = address.getPort
          Uri.fromString(s"http://$name:$port/internal-server-error").yolo
        }

        val successHosts = addresses.map { address =>
          val name = address.getHostName
          val port = address.getPort
          Uri.fromString(s"http://$name:$port/simple").yolo
        }

        val failedRequests =
          (1 to Runtime.getRuntime.availableProcessors * 5).toList.parTraverse { _ =>
            val h = failedHosts(Random.nextInt(failedHosts.length))
            client.expect[String](h)
          }

        val sucessRequests =
          (1 to Runtime.getRuntime.availableProcessors * 5).toList.parTraverse { _ =>
            val h = successHosts(Random.nextInt(successHosts.length))
            client.expect[String](h).map(_.nonEmpty)
          }

        val allRequests = for {
          _ <- failedRequests.handleErrorWith(_ => IO.unit).replicateA(5)
          r <- sucessRequests
        } yield r

        allRequests
          .map(_.forall(identity))
      }.assert
  }

  jettyScaffold.test(
    "Blaze Http1Client should behave and not deadlock on failures with parSequence") {
    case (jettyServer, _) =>
      val addresses = jettyServer.addresses
      mkClient(3).use { client =>
        val failedHosts = addresses.map { address =>
          val name = address.getHostName
          val port = address.getPort
          Uri.fromString(s"http://$name:$port/internal-server-error").yolo
        }

        val successHosts = addresses.map { address =>
          val name = address.getHostName
          val port = address.getPort
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
      }.assert
  }

  jettyScaffold.test("call a second host after reusing connections on a first") {
    case (jettyServer, _) =>
      val addresses = jettyServer.addresses
      // https://github.com/http4s/http4s/pull/2546
      mkClient(maxConnectionsPerRequestKey = Int.MaxValue, maxTotalConnections = 5)
        .use { client =>
          val uris = addresses.take(2).map { address =>
            val name = address.getHostName
            val port = address.getPort
            Uri.fromString(s"http://$name:$port/simple").yolo
          }
          val s = Stream(
            Stream.eval(
              client.expect[String](Request[IO](uri = uris(0)))
            )).repeat.take(10).parJoinUnbounded ++ Stream.eval(
            client.expect[String](Request[IO](uri = uris(1))))
          s.compile.lastOrError
        }
        .assertEquals("simple path")
  }

}
