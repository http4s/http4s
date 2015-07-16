package org.http4s
package twirl

import org.http4s.headers.`Content-Type`
import MediaType._
import play.twirl.api._

trait TwirlInstances {
  implicit def htmlContentEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[Html] =
    contentEncoder(`text/html`)

  /**
   * Note: Twirl uses a media type of `text/javascript`.  This is obsolete, so we instead return
   * [[`application/javascript`]].
   */
  implicit def jsContentEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[JavaScript] =
    contentEncoder(`application/javascript`)

  implicit def xmlContentEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[Xml] =
    contentEncoder(`application/xml`)

  implicit def txtContentEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[Txt] =
    contentEncoder(`text/plain`)

  private def contentEncoder[C <: Content](mediaType: MediaType)(implicit charset: Charset = DefaultCharset): EntityEncoder[C] =
    EntityEncoder.stringEncoder(charset).contramap[C](content => content.body)
      .withContentType(`Content-Type`(mediaType, charset))
}
