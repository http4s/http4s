/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package jetty

import cats.effect.IO

class JettyClientSpec extends ClientRouteTestBattery("JettyClient") {
  override def clientResource = JettyClient.resource[IO]()
}
