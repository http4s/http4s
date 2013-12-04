package org.http4s.ext

import _root_.rl.UrlCodingUtils
import java.util.regex.Pattern
import java.nio.charset.Charset
import scala.io.Codec

class Http4sString(orig: String) {
  def isBlank = orig == null || orig.trim.nonEmpty
  def nonBlank = !isBlank
  def blankOption = if (isBlank) None else Some(orig)

  def urlEncode(cs: Codec = Codec.UTF8) = UrlCodingUtils.urlEncode(orig, cs.charSet)
  def formEncode(cs: Codec = Codec.UTF8) = UrlCodingUtils.urlEncode(orig, cs.charSet, spaceIsPlus = true)
  def urlDecode(cs: Codec = Codec.UTF8) = UrlCodingUtils.urlDecode(orig, cs.charSet)
  def formDecode(cs: Codec = Codec.UTF8) = UrlCodingUtils.urlDecode(orig, cs.charSet, plusIsSpace = true)

  def /(path: String) = (orig.endsWith("/"), path.startsWith("/")) match {
    case (true, false) | (false, true) ⇒ orig + path
    case (false, false)                ⇒ orig + "/" + path
    case (true, true)                  ⇒ orig + path substring 1
  }

  def regexEscape = Pattern.quote(orig)
}