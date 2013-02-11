package org.http4s
package parser

import org.parboiled.scala._
import HttpEncodings._

private[parser] trait ContentEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def CONTENT_ENCODING = rule (
    ContentEncoding ~ EOI ~~> HttpHeaders.`Content-Encoding`
  )

  def ContentEncoding = rule {
    ContentCoding ~~> (x => HttpEncodings.getForKey(x.toLowerCase).getOrElse(new CustomHttpEncoding(x)))
  }

}