/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package syntax

trait LiteralsSyntax {
  implicit def http4sLiteralsSyntax(sc: StringContext): LiteralsOps =
    new LiteralsOps(sc)
}

class LiteralsOps(val sc: StringContext) extends AnyVal {
  def uri(args: Any*): Uri = macro LiteralSyntaxMacros.uriInterpolator
  def path(args: Any*): Uri.Path = macro LiteralSyntaxMacros.pathInterpolator
  def scheme(args: Any*): Uri.Scheme = macro LiteralSyntaxMacros.schemeInterpolator
  def ipv4(args: Any*): Uri.Ipv4Address = macro LiteralSyntaxMacros.ipv4AddressInterpolator
  def ipv6(args: Any*): Uri.Ipv6Address = macro LiteralSyntaxMacros.ipv6AddressInterpolator
  def mediaType(args: Any*): MediaType = macro LiteralSyntaxMacros.mediaTypeInterpolator
  def qValue(args: Any*): QValue = macro LiteralSyntaxMacros.qValueInterpolator
}
