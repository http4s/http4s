/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/scalatra/rl/blob/v0.4.10/core/src/main/scala/rl/UrlCodingUtils.scala
 * Copyright (c) 2011 Mojolly Ltd.
 * See licenses/LICENSE_rl
 */

package org.http4s.util

import org.http4s.internal.CharPredicate

private[http4s] object UrlCodingUtils {
  val GenDelims: CharPredicate =
    CharPredicate.from(":/?#[]@".toSet)

  val SubDelims: CharPredicate =
    CharPredicate.from("!$&'()*+,;=".toSet)
}
