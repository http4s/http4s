package org.http4s
package twirl

import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import _root_.play.twirl.api._

trait TwirlInstances {
  implicit def htmlContentEncoder[F[_]](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Html] =
    contentEncoder(MediaType.text.html)

  /**
    * Note: Twirl uses a media type of `text/javascript`.  This is obsolete, so we instead return
    * [[org.http4s.MediaType.application/javascript]].
    */
  implicit def jsContentEncoder[F[_]](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, JavaScript] =
    contentEncoder(MediaType.application.javascript)

  implicit def xmlContentEncoder[F[_]](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Xml] =
    contentEncoder(MediaType.application.xml)

  implicit def txtContentEncoder[F[_]](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Txt] =
    contentEncoder(MediaType.text.plain)

  private def contentEncoder[F[_], C <: Content](mediaType: MediaType)(
      implicit charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.body)
      .withContentType(`Content-Type`(mediaType, charset))
}
