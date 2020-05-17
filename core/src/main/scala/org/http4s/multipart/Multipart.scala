/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package multipart

import org.http4s.headers._

final case class Multipart[F[_]](parts: Vector[Part[F]], boundary: Boundary = Boundary.create) {
  def headers: Headers =
    Headers(
      List(
        `Transfer-Encoding`(TransferCoding.chunked),
        `Content-Type`(MediaType.multipartType("form-data", Some(boundary.value)))
      )
    )
}
