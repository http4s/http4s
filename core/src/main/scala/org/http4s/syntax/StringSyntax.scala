/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package syntax

import org.http4s.util.CaseInsensitiveString

trait StringSyntax {
  implicit def http4sStringSyntax(s: String): StringOps =
    new StringOps(s)
}

final class StringOps(val self: String) extends AnyVal {
  def ci: CaseInsensitiveString =
    CaseInsensitiveString(self)
}
