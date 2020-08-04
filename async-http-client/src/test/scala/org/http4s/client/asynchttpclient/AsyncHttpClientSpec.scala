/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package asynchttpclient

import cats.effect.IO

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient") with Http4sSpec {

  def clientResource = AsyncHttpClient.resource[IO]()

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
      val customConfig = AsyncHttpClient.configure(_.setMaxConnectionsPerHost(customMaxConnectionsPerHost))

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
  }
}
