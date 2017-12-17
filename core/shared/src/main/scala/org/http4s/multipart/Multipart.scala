package org.http4s
package multipart

import org.http4s.headers._

final case class Multipart[F[_]](parts: Vector[Part[F]], boundary: Boundary = Boundary.create) {
  def headers: Headers =
    Headers(`Content-Type`(MediaType.multipart("form-data", Some(boundary.value))))
}
