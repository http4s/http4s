package org.http4s
package multipart

import cats.effect.Sync
import fs2.Stream
import fs2.text.utf8Encode
import org.http4s.headers.`Content-Disposition`
import org.http4s.{EmptyBody, Header, Headers}

final case class Part[F[_]](headers: Headers, body: Stream[F, Byte]) {
  def name: Option[String] = headers.get(`Content-Disposition`).flatMap(_.parameters.get("name"))
}

object Part extends PlatformPart {
  def empty[F[_]]: Part[F] =
    Part(Headers.empty, EmptyBody)

  def formData[F[_]: Sync](name: String, value: String, headers: Header*): Part[F] =
    Part(
      `Content-Disposition`("form-data", Map("name" -> name)) +: headers,
      Stream.emit(value).through(utf8Encode))

}
