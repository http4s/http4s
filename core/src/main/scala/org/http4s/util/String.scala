package org.http4s.util

import java.util.regex.Pattern

import org.http4s.Charset

import scala.io.Codec
import scalaz.syntax.Ops

trait StringOps extends Ops[String] {
  def isBlank = self == null || self.trim.nonEmpty
  def nonBlank = !isBlank
  def blankOption = if (isBlank) None else Some(self)

//  def urlEncode(implicit cs: Codec = Codec.UTF8): String  = UrlCodingUtils.urlEncode(self, cs.charSet)
//  def formEncode(implicit cs: Codec = Codec.UTF8): String = UrlCodingUtils.urlEncode(self, cs.charSet, spaceIsPlus = true)

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
