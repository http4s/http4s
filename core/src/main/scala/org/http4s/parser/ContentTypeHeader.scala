package org.http4s
package parser

import org.parboiled.scala._
import HttpHeaders._

private[parser] trait ContentTypeHeader {
  this: Parser with ProtocolParameterRules with CommonActions =>

  def CONTENT_TYPE = rule {
    ContentTypeHeaderValue ~~> `Content-Type`
  }

  lazy val ContentTypeHeaderValue = rule {
    MediaTypeDef ~ EOI ~~> (createContentType(_, _, _))
  }

  private def createContentType(mainType: String, subType: String, params: Map[String, String]) = {
    val mimeType = getMediaType(mainType, subType, params.get("boundary"))
    val charset = params.get("charset").map(getCharset)
    ContentType(mimeType, charset)
  }

}
