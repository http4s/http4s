/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import org.http4s.syntax.string._

object AuthScheme {
  val Basic = "Basic".ci
  val Digest = "Digest".ci
  val Bearer = "Bearer".ci
}
