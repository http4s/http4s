/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package syntax

trait LiteralsSyntax {
  implicit def http4sLiteralsSyntax(sc: StringContext): LiteralsOps =
    new LiteralsOps(sc)
}

class LiteralsOps(val sc: StringContext) extends AnyVal {
  def uri(args: Any*): Uri = macro LiteralSyntaxMacros.uriInterpolator
  def scheme(args: Any*): Uri.Scheme = macro LiteralSyntaxMacros.schemeInterpolator
  def ipv4(args: Any*): Uri.Ipv4Address = macro LiteralSyntaxMacros.ipv4AddressInterpolator
  def ipv6(args: Any*): Uri.Ipv6Address = macro LiteralSyntaxMacros.ipv6AddressInterpolator
  def mediaType(args: Any*): MediaType = macro LiteralSyntaxMacros.mediaTypeInterpolator
  def qValue(args: Any*): QValue = macro LiteralSyntaxMacros.qValueInterpolator
}
