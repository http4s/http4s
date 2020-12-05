/*
 * Copyright 2018 http4s.org
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
package scalatags

import _root_.scalatags.Text.TypedTag
import org.http4s.headers.`Content-Type`

trait ScalatagsInstances {
  implicit def scalatagsEncoder[F[_]](implicit
      charset: Charset = DefaultCharset): EntityEncoder[F, TypedTag[String]] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: TypedTag[String]](mediaType: MediaType)(implicit
      charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))
}
