package org.http4s
package parser

import MediaTypes._
import org.parboiled.errors.ParsingException

private[parser] trait CommonActions {

  def getMediaType(mainType: String, subType: String, boundary: Option[String] = None): MediaType = {
    mainType.toLowerCase match {
      case "multipart" => subType.toLowerCase match {
        case "mixed"       => new `multipart/mixed`      (boundary)
        case "alternative" => new `multipart/alternative`(boundary)
        case "related"     => new `multipart/related`    (boundary)
        case "form-bytes"   => new `multipart/form-data`  (boundary)
        case "signed"      => new `multipart/signed`     (boundary)
        case "encrypted"   => new `multipart/encrypted`  (boundary)
        case custom        => new MultipartMediaType(custom, boundary)
      }
      case mainLower =>
        MediaTypes.getForKey((mainLower, subType.toLowerCase)).getOrElse(new CustomMediaType(mainType, subType))
    }
  }

  val getCharset: String => HttpCharset = { charsetName =>
    HttpCharsets
      .getForKey(charsetName.toLowerCase)
      .orElse(HttpCharsets.CustomHttpCharset(charsetName))
      .getOrElse(throw new ParsingException("Unsupported charset: " + charsetName))
  }
}