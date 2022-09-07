/*
 * Copyright 2013 http4s.org
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
package multipart

import org.http4s.headers._

/** Create a new multipart from a vector of parts and a boundary.
  *
  * To create Multipart values from a generated boundary, see the [[Multiparts]] algebra.
  */
final case class Multipart[F[_]](
    parts: Vector[Part[F]],
    boundary: Boundary,
) {
  @deprecated(
    "Creating a boundary is an effect.  Use Multiparts.multipart to generate an F[Multipart[F]], or call the two-parameter constructor with your own boundary.",
    "0.23.12",
  )
  def this(parts: Vector[Part[F]]) =
    this(parts, Boundary.unsafeCreate())

  def headers: Headers =
    Headers(
      `Transfer-Encoding`(TransferCoding.chunked),
      `Content-Type`(MediaType.multipartType("form-data", Some(boundary.value))),
    )
}

object Multipart {
  @deprecated("Retaining for binary-compatibility", "0.23.12")
  def `<init>$default$2`: String = apply$default$2
  @deprecated("Retaining for binary-compatibility", "0.23.12")
  def apply$default$2: String = Boundary.unsafeCreate().value

  @deprecated(
    "Creating a boundary is an effect.  Use Multiparts.multipart to generate an F[Multipart[F]], or call the two-parameter apply with your own boundary.",
    "0.23.12",
  )
  def apply[F[_]](parts: Vector[Part[F]]) = new Multipart(parts)
}
