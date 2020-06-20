/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package asynchttpclient

import cats.effect.IO

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient") {
  def clientResource = AsyncHttpClient.resource[IO]()
}
