package org.http4s
package parserold

import org.parboiled.scala._
import ContentCoding._
import java.util.Locale

private[parserold] trait ContentEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def CONTENT_ENCODING = rule (
    ContentEncoding ~ EOI ~~> (Header.`Content-Encoding`(_))
  )

  def ContentEncoding = rule {
    ContentCoding ~~> (org.http4s.ContentCoding.resolve _)
  }

}