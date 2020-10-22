/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package scala.annotation

/** Shims Scala 2.13.2's `@nowarn` so it compiles on Scala 2.12.
  * Silencer has rudimentary support.
  */
class nowarn(val value: String = "") extends StaticAnnotation
