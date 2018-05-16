package org.http4s
package multipart
package file

import org.http4s.headers._

final case class MixedMultipart[F[_]](
    parts: Vector[MixedPart[F]],
    boundary: Boundary = Boundary.create) {
  def headers: Headers =
    Headers(`Content-Type`(MediaType.multipart("form-data", Some(boundary.value))))
}
