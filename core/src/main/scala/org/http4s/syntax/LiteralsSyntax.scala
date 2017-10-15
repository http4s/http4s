package org.http4s
package syntax

import org.http4s.internal.Macros

trait LiteralsSyntax {
  implicit def http4sLiteralsSyntax(sc: StringContext) = new LiteralsOps(sc)
}

class LiteralsOps(val sc: StringContext) extends AnyVal {
  def authority(): Uri.Authority = macro Macros.authority
  def fragment(): Uri.Fragment = macro Macros.fragment
  def host(): Uri.Host = macro Macros.host
  def port(): Uri.Port = macro Macros.port
  def scheme(): Uri.Scheme = macro Macros.scheme
  def userInfo(): Uri.UserInfo = macro Macros.userInfo
}
