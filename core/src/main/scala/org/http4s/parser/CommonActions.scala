package org.http4s
package parser

import MediaType._

private[parser] trait CommonActions {

  def getMediaType(mainType: String, subType: String, boundary: Option[String] = None): MediaType = {
    mainType.toLowerCase match {
      case "multipart" => subType.toLowerCase match {
        case "mixed"       => new `multipart/mixed`      (boundary)
        case "alternative" => new `multipart/alternative`(boundary)
        case "related"     => new `multipart/related`    (boundary)
        case "form-data"   => new `multipart/form-data`  (boundary)
        case "signed"      => new `multipart/signed`     (boundary)
        case "encrypted"   => new `multipart/encrypted`  (boundary)
        case custom        => new MultipartMediaType(custom, boundary)
      }
      case mainLower =>
        MediaType.lookupOrElse((mainLower, subType.toLowerCase), new MediaType(mainType, subType))
    }
  }

  val getCharset: String => CharacterSet = CharacterSet.resolve
}