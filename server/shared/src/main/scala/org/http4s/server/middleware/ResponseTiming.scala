/*
 * Copyright 2014 http4s.org
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
package server
package middleware

import cats.Functor
import cats.data.Kleisli
import cats.effect._
import cats.effect.syntax.clock._
import cats.syntax.all._
import org.typelevel.ci._

import scala.concurrent.duration._

object ResponseTiming {

  /** Simple middleware for adding a custom header with timing information to a response.
    *
    * This middleware captures the time starting from when the request headers are parsed and supplied
    * to the wrapped service and ending when the response is started. Metrics middleware, like this one,
    * work best as the outer layer to ensure work done by other middleware is also included.
    *
    * @param http [[HttpApp]] to transform
    * @param timeUnit the units of measure for this timing
    * @param headerName the name to use for the header containing the timing info
    */
  def apply[F[_]: Functor: Clock](
      http: HttpApp[F],
      timeUnit: TimeUnit = MILLISECONDS,
      headerName: CIString = ci"X-Response-Time",
  ): HttpApp[F] =
    Kleisli { req =>
      http(req).timed.map { case (processingTime, resp) =>
        val header = Header.Raw(headerName, s"${processingTime.toUnit(timeUnit).toLong}")
        resp.putHeaders(header)
      }
    }

  @deprecated("Use `apply` with Functor and Clock type constraints", "0.23.21")
  def apply[F[_]](
      http: HttpApp[F],
      timeUnit: TimeUnit,
      headerName: CIString,
      F: Sync[F],
      clock: Clock[F],
  ): HttpApp[F] = apply(http, timeUnit, headerName)(F, clock)
}
