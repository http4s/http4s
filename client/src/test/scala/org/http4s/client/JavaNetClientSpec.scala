/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client

import cats.effect.IO

class JavaNetClientSpec extends ClientRouteTestBattery("JavaNetClient") {
  def clientResource = JavaNetClientBuilder[IO](testBlocker).resource
}
