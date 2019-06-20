package org.http4s
package syntax

trait LiteralsSyntax {
  implicit def http4sLiteralsSyntax(sc: StringContext): LiteralsOps =
    new LiteralsOps(sc)
}

class LiteralsOps(val sc: StringContext) extends AnyVal {
  def uri(args: Any*): Uri = macro LiteralSyntaxMacros.uriInterpolator
  def scheme(args: Any*): Uri.Scheme = macro LiteralSyntaxMacros.schemeInterpolator
  def mediaType(args: Any*): MediaType = macro LiteralSyntaxMacros.mediaTypeInterpolator
  def qValue(args: Any*): QValue = macro LiteralSyntaxMacros.qValueInterpolator
}
