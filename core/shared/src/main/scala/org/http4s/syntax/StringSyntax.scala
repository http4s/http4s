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
