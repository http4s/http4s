/*
 * Copyright 2016 http4s.org
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
package client
package asynchttpclient

import cats.effect.{IO, Resource}
import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.HostStats

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient") with Http4sSuite {

  def clientResource: Resource[IO, Client[IO]] = AsyncHttpClient.resource[IO]()

  test(
    "AsyncHttpClient configure should evaluate to the defaultConfiguration given the identity function as the configuration function") {
    val defaultConfig = AsyncHttpClient.defaultConfig
    val customConfig = AsyncHttpClient.configure(identity)

    assertEquals(defaultConfig.getMaxConnectionsPerHost, customConfig.getMaxConnectionsPerHost)
    assertEquals(defaultConfig.getMaxConnections, customConfig.getMaxConnections)
    assertEquals(defaultConfig.getRequestTimeout, customConfig.getRequestTimeout)
  }

  test("AsyncHttpClient configure should be able to configure max connections per host") {
    val customMaxConnectionsPerHost = 30
    val customConfig =
      AsyncHttpClient.configure(_.setMaxConnectionsPerHost(customMaxConnectionsPerHost))

    assertEquals(customConfig.getMaxConnectionsPerHost, customMaxConnectionsPerHost)
  }

  test("AsyncHttpClient configure should be able to configure max connections") {
    val customMaxConnections = 40
    val customConfig = AsyncHttpClient.configure(_.setMaxConnections(customMaxConnections))

    assertEquals(customConfig.getMaxConnections, customMaxConnections)
  }

  test("AsyncHttpClient configure should be able to configure request timeout") {
    val customRequestTimeout = defaults.RequestTimeout.toMillis.toInt + 2
    val customConfig = AsyncHttpClient.configure(_.setRequestTimeout(customRequestTimeout))

    assertEquals(customConfig.getRequestTimeout, customRequestTimeout)
  }

  test("AsyncHttpClient configure should be able to configure more than one parameter at once") {
    val customMaxConnectionsPerHost = 25
    val customMaxConnections = 2
    val customRequestTimeout = defaults.RequestTimeout.toMillis.toInt + 2
    val customConfig = AsyncHttpClient.configure { builder =>
      builder
        .setMaxConnectionsPerHost(customMaxConnectionsPerHost)
        .setMaxConnections(customMaxConnections)
        .setRequestTimeout(customRequestTimeout)
    }

    assertEquals(customConfig.getMaxConnectionsPerHost, customMaxConnectionsPerHost)
    assertEquals(customConfig.getMaxConnections, customMaxConnections)
    assertEquals(customConfig.getRequestTimeout, customRequestTimeout)
  }

  test("AsyncHttpClientStats should correctly get the stats from the underlying ClientStats") {

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
        f: AsyncHttpClientStats[IO] => IO[Stats]): IO[Stats] =
      stats.map(f).use(identity)

    extractStats(clientStats, _.getTotalIdleConnectionCount).assertEquals(0L) *>
      extractStats(clientStats, _.getTotalConnectionCount).assertEquals(0L) *>
      extractStats(clientStats, _.getStatsPerHost).assertEquals(Map.empty[String, HostStats])
  }

  class ClientWithStats(client: Client[IO], private val stats: AsyncHttpClientStats[IO])
      extends DefaultClient[IO] {
    def getStats: AsyncHttpClientStats[IO] = stats
    override def run(req: Request[IO]): Resource[IO, Response[IO]] = client.run(req)
  }
}
