package org.http4s
package parser

import org.parboiled.scala._
import ContentCoding._
import java.util.Locale

private[parser] trait ContentEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def CONTENT_ENCODING = rule (
    ContentEncoding ~ EOI ~~> (Header.`Content-Encoding`(_))
  )

  def ContentEncoding = rule {
    ContentCoding ~~> (x => org.http4s.ContentCoding.getForKey(x.lowercaseEn).getOrElse(org.http4s.ContentCoding(x.lowercaseEn)))
  }

}