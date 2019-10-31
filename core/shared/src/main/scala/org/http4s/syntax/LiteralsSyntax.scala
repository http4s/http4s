package org.http4s
package syntax

import org.http4s.internal.Macros

trait LiteralsSyntax {
  implicit def http4sLiteralsSyntax(sc: StringContext): LiteralsOps =
    new LiteralsOps(sc)
}

class LiteralsOps(val sc: StringContext) extends AnyVal {
  def scheme(): Uri.Scheme = macro Macros.scheme
}
