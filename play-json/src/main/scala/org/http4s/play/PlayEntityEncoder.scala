/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.play

import play.api.libs.json.Writes
import org.http4s.EntityEncoder

/**
  * Derive [[EntityEncoder]] if implicit [[Writes]] is in the scope without need to explicitly call `jsonEncoderOf`
  */
trait PlayEntityEncoder {
  implicit def playEntityEncoder[F[_], A: Writes]: EntityEncoder[F, A] =
    jsonEncoderOf[F, A]
}

object PlayEntityEncoder extends PlayEntityEncoder
