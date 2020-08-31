/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import org.typelevel.ci.CIString

object AuthScheme {
  val Basic = CIString("Basic")
  val Digest = CIString("Digest")
  val Bearer = CIString("Bearer")
}
