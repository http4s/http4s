/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.play

import cats.effect.Concurrent
import org.http4s.EntityDecoder
import play.api.libs.json.Reads

/** Derive [[EntityDecoder]] if implicit [[Reads]] is in the scope without need to explicitly call `jsonOf`
  */
trait PlayEntityDecoder {
  implicit def playEntityDecoder[F[_]: Concurrent, A: Reads]: EntityDecoder[F, A] = jsonOf[F, A]
}

object PlayEntityDecoder extends PlayEntityDecoder
