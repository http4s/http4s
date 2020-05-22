/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package jetty

import cats.effect.IO
import cats.implicits._
import org.http4s.dsl.io._

class PrefixedServicesSpec extends Http4sSpec {
  "PrefixedServices" should {
    "be easy to build and combine" in {

      val routeA = HttpRoutes.of[IO] {
        case GET -> Root / "a" => Ok()
      }
      val routeB = HttpRoutes.of[IO] {
        case GET -> Root / "b" => Ok()
      }

      val prefixedA: PrefixedServices[IO] = PrefixedServices(routeA, "/a")
      val prefixedB: PrefixedServices[IO] = PrefixedServices(routeB, "/b")

      val prefixedCombined: PrefixedServices[IO] = prefixedA |+| prefixedB
      prefixedCombined.services.size must_== 2
    }
  }
}
