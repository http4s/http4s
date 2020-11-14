/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package asynchttpclient

import cats.effect.{IO, Resource}
import org.asynchttpclient.DefaultAsyncHttpClient

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient") with Http4sSpec {

  def clientResource: Resource[IO, Client[IO]] = AsyncHttpClient.resource[IO]()

  "AsyncHttpClient configure" should {
    "evaluate to the defaultConfiguration given the identity function as the configuration function" in {
      val defaultConfig = AsyncHttpClient.defaultConfig
      val customConfig = AsyncHttpClient.configure(identity)

      defaultConfig.getMaxConnectionsPerHost shouldEqual customConfig.getMaxConnectionsPerHost
      defaultConfig.getMaxConnections shouldEqual customConfig.getMaxConnections
      defaultConfig.getRequestTimeout shouldEqual customConfig.getRequestTimeout
    }

    "be able to configure max connections per host" in {
      val customMaxConnectionsPerHost = 30
      val customConfig =
        AsyncHttpClient.configure(_.setMaxConnectionsPerHost(customMaxConnectionsPerHost))

      customConfig.getMaxConnectionsPerHost shouldEqual customMaxConnectionsPerHost
    }

    "be able to configure max connections" in {
      val customMaxConnections = 40
      val customConfig = AsyncHttpClient.configure(_.setMaxConnections(customMaxConnections))

      customConfig.getMaxConnections shouldEqual customMaxConnections
    }

    "be able to configure request timeout" in {
      val customRequestTimeout = defaults.RequestTimeout.toMillis.toInt + 2
      val customConfig = AsyncHttpClient.configure(_.setRequestTimeout(customRequestTimeout))

      customConfig.getRequestTimeout shouldEqual customRequestTimeout
    }

    "be able to configure more than one parameter at once" in {
      val customMaxConnectionsPerHost = 25
      val customMaxConnections = 2
      val customRequestTimeout = defaults.RequestTimeout.toMillis.toInt + 2
      val customConfig = AsyncHttpClient.configure { builder =>
        builder
          .setMaxConnectionsPerHost(customMaxConnectionsPerHost)
          .setMaxConnections(customMaxConnections)
          .setRequestTimeout(customRequestTimeout)
      }

      customConfig.getMaxConnectionsPerHost shouldEqual customMaxConnectionsPerHost
      customConfig.getMaxConnections shouldEqual customMaxConnections
      customConfig.getRequestTimeout shouldEqual customRequestTimeout
    }
  }

  "AsyncHttpClientStats" should {
    "correctly get the stats from the underlying ClientStats" in {

      val clientWithStats: Resource[IO, Client[IO]] = Resource(
        IO.delay(new DefaultAsyncHttpClient(AsyncHttpClient.defaultConfig))
          .map(c =>
            (
              new ClientWithStats(
                AsyncHttpClient.apply(c),
                new AsyncHttpClientStats[IO](c.getClientStats)),
              IO.delay(c.close()))))

      val clientStats: Resource[IO, AsyncHttpClientStats[IO]] = clientWithStats.map {
        case client: ClientWithStats => client.getStats
      }

      def extractStats[Stats](
          stats: Resource[IO, AsyncHttpClientStats[IO]],
          f: AsyncHttpClientStats[IO] => IO[Stats]): Stats =
        stats.map(f).use(x => x).unsafeRunSync()

      extractStats(clientStats, _.getTotalIdleConnectionCount) shouldEqual 0
      extractStats(clientStats, _.getTotalConnectionCount) shouldEqual 0
      extractStats(clientStats, _.getTotalIdleConnectionCount) shouldEqual 0
      extractStats(clientStats, _.getStatsPerHost) shouldEqual Map.empty
    }
  }
  class ClientWithStats(client: Client[IO], private val stats: AsyncHttpClientStats[IO])
      extends DefaultClient[IO] {
    def getStats: AsyncHttpClientStats[IO] = stats
    override def run(req: Request[IO]): Resource[IO, Response[IO]] = client.run(req)
  }
}
