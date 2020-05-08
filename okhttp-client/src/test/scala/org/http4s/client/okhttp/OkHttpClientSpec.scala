/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package okhttp

import cats.effect.IO

class OkHttpClientSpec extends ClientRouteTestBattery("OkHttp") {
  def clientResource =
    OkHttpBuilder.withDefaultClient[IO](testBlocker).map(_.create)
}
