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
    "evaluate to the defaultConfiguration given an identity function as the configuration function" in {
      val defaultConfig = AsyncHttpClient.defaultConfig
      AsyncHttpClient.configure(identity) shouldEqual defaultConfig
    }
  }

  // TODO: more tests once I get some feedback

}
