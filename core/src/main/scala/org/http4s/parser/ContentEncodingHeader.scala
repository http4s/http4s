package org.http4s
package parser

import org.parboiled.scala._
import ContentCodings._

private[parser] trait ContentEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def CONTENT_ENCODING = rule (
    ContentEncoding ~ EOI ~~> (Headers.ContentEncoding(_))
  )

  def ContentEncoding = rule {
    ContentCoding ~~> (x => ContentCodings.getForKey(x.toLowerCase).getOrElse(new CustomHttpContentCoding(x)))
  }

}