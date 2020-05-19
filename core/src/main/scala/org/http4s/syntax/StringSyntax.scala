/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package syntax

import org.typelevel.ci.CIString

trait StringSyntax {
  @deprecated("Use CIString.apply instead", "1.0.0-M1")
  implicit def http4sStringSyntax(s: String): StringOps =
    new StringOps(s)
}

@deprecated("Use CIString.apply instead", "1.0.0-M1")
final class StringOps(val self: String) extends AnyVal {
  def ci: CIString =
    CIString(self)
}
