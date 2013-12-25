package org.http4s
package parserold

import org.http4s.MediaType._

private[parserold] trait CommonActions {

  def getMediaType(mainType: String, subType: String, boundary: Option[String] = None): MediaType = {
    mainType.toLowerCase match {
      case "multipart" => subType.toLowerCase match {
        case "mixed"       => Multipart("mixed", boundary)
        case "alternative" => Multipart("alternative", boundary)
        case "related"     => Multipart("related", boundary)
        case "form-data"   => Multipart("form-data", boundary)
        case "signed"      => Multipart("signed", boundary)
        case "encrypted"   => Multipart("encrypted", boundary)
        case custom        => Multipart("custom", boundary)
      }
      case mainLower =>
        MediaType.lookupOrElse((mainLower, subType.toLowerCase), new MediaType(mainType, subType))
    }
  }

  val getCharset: String => CharacterSet = CharacterSet.resolve
}