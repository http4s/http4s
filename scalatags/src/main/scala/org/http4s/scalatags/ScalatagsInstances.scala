/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package scalatags

import _root_.scalatags.generic.Frag
import org.http4s.headers.`Content-Type`

trait ScalatagsInstances {
  implicit def scalatagsEncoder[F[_], C <: Frag[_, String]](implicit
      charset: Charset = DefaultCharset): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: Frag[_, String]](mediaType: MediaType)(implicit
      charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))
}
