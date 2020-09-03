/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.effect._
import cats.syntax.all._
import org.typelevel.ci.CIString
import scala.concurrent.duration._

object ResponseTiming {

  /**
    * Simple middleware for adding a custom header with timing information to a response.
    *
    * This middleware captures the time starting from when the request headers are parsed and supplied
    * to the wrapped service and ending when the response is started. Metrics middleware, like this one,
    * work best as the outer layer to ensure work done by other middleware is also included.
    *
    * @param http [[HttpApp]] to transform
    * @param timeUnit the units of measure for this timing
    * @param headerName the name to use for the header containing the timing info
    */
  def apply[F[_]](
      http: HttpApp[F],
      timeUnit: TimeUnit = MILLISECONDS,
      headerName: CIString = CIString("X-Response-Time"))(implicit
      F: Sync[F],
      clock: Clock[F]): HttpApp[F] =
    Kleisli { req =>
      for {
        before <- clock.monotonic(timeUnit)
        resp <- http(req)
        after <- clock.monotonic(timeUnit)
        header = Header(headerName.toString, s"${after - before}")
      } yield resp.putHeaders(header)
    }
}
