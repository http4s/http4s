/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package twirl

import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import _root_.play.twirl.api._

trait TwirlInstances {
  implicit def htmlContentEncoder[F[_]](implicit
      charset: Charset = DefaultCharset): EntityEncoder[F, Html] =
    contentEncoder(MediaType.text.html)

  /** Note: Twirl uses a media type of `text/javascript`.  This is obsolete, so we instead return
    * `application/javascript`.
    */
  implicit def jsContentEncoder[F[_]](implicit
      charset: Charset = DefaultCharset): EntityEncoder[F, JavaScript] =
    contentEncoder(MediaType.application.javascript)

  implicit def xmlContentEncoder[F[_]](implicit
      charset: Charset = DefaultCharset): EntityEncoder[F, Xml] =
    contentEncoder(MediaType.application.xml)

  implicit def txtContentEncoder[F[_]](implicit
      charset: Charset = DefaultCharset): EntityEncoder[F, Txt] =
    contentEncoder(MediaType.text.plain)

  private def contentEncoder[F[_], C <: Content](mediaType: MediaType)(implicit
      charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.body)
      .withContentType(`Content-Type`(mediaType, charset))
}
