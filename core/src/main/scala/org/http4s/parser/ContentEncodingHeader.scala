package org.http4s
package parser

import org.parboiled.scala._
import ContentCodings._
import java.util.Locale

private[parser] trait ContentEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def CONTENT_ENCODING = rule (
    ContentEncoding ~ EOI ~~> (Headers.ContentEncoding(_))
  )

  def ContentEncoding = rule {
    ContentCoding ~~> (x => ContentCodings.getForKey(x.lowercaseEn).getOrElse(org.http4s.ContentCoding(x.lowercaseEn)))
  }

}