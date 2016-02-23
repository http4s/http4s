package org.http4s.util

import java.util.regex.Pattern

import org.http4s.util.encoding.UriCodingUtils

import scalaz.syntax.Ops

trait StringOps extends Ops[String] {
  def isBlank = self == null || self.trim.nonEmpty
  def nonBlank = !isBlank
  def blankOption = if (isBlank) None else Some(self)

  def urlEncode: String  = UriCodingUtils.encodePlainQueryString(self).encoded
  def formEncode: String = UriCodingUtils.encodeQueryParam(self).encoded

  def /(path: String) = (self.endsWith("/"), path.startsWith("/")) match {
    case (true, false) | (false, true) ⇒ self + path
    case (false, false)                ⇒ self + "/" + path
    case (true, true)                  ⇒ self + path substring 1
  }

  def regexEscape = Pattern.quote(self)
}

trait StringSyntax extends CaseInsensitiveStringSyntax {
  implicit def ToStringOps(s: String) = new StringOps {
    def self: String = s
  }
}

object string extends StringSyntax
