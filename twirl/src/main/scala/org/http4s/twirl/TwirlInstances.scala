package org.http4s
package twirl

import cats._
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType._
import play.twirl.api._

trait TwirlInstances {
  implicit def htmlContentEncoder[F[_]: Applicative](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Html] =
    contentEncoder(`text/html`)

  /**
    * Note: Twirl uses a media type of `text/javascript`.  This is obsolete, so we instead return
    * [[org.http4s.MediaType.application/javascript]].
    */
  implicit def jsContentEncoder[F[_]: Applicative](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, JavaScript] =
    contentEncoder(`application/javascript`)

  implicit def xmlContentEncoder[F[_]: Applicative](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Xml] =
    contentEncoder(`application/xml`)

  implicit def txtContentEncoder[F[_]: Applicative](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Txt] =
    contentEncoder(`text/plain`)

  private def contentEncoder[F[_], C <: Content](mediaType: MediaType)(
      implicit F: Applicative[F],
      charset: Charset = DefaultCharset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.body)
      .withContentType(`Content-Type`(mediaType, charset))
}
