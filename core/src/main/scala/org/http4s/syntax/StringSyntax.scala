package org.http4s
package syntax

import com.rossabaker.ci.CIString

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
