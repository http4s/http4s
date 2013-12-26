package org.http4s
package parserold

import org.http4s.MediaType._

private[parserold] trait CommonActions {

  def getMediaType(mainType: String, subType: String, boundary: Option[String] = None): MediaType = {
    mainType.toLowerCase match {
      case "multipart" => subType.toLowerCase match {
        case "mixed"       => multipart("mixed", boundary)
        case "alternative" => multipart("alternative", boundary)
        case "related"     => multipart("related", boundary)
        case "form-data"   => multipart("form-data", boundary)
        case "signed"      => multipart("signed", boundary)
        case "encrypted"   => multipart("encrypted", boundary)
        case custom        => multipart("custom", boundary)
      }
      case mainLower =>
        MediaType.lookupOrElse((mainLower, subType.toLowerCase), new MediaType(mainType, subType))
    }
  }

  val getCharset: String => CharacterSet = CharacterSet.resolve
}