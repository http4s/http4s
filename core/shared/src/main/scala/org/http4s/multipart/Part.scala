package org.http4s
package multipart

import cats.effect.Sync
import fs2.Stream
import fs2.text.utf8Encode
import org.http4s.headers.`Content-Disposition`

final case class Part[F[_]](headers: Headers, body: Stream[F, Byte]) {
  def name: Option[String] = headers.get(`Content-Disposition`).flatMap(_.parameters.get("name"))
  def filename: Option[String] =
    headers.get(`Content-Disposition`).flatMap(_.parameters.get("filename"))
}

object Part extends PlatformPart {

  @deprecated(
    """Empty parts are not allowed by the multipart spec, see: https://tools.ietf.org/html/rfc7578#section-4.2
       Moreover, it allows the creation of potentially incorrect multipart bodies
    """.stripMargin,
    "0.18.12"
  )
  def empty[F[_]]: Part[F] =
    Part(Headers.empty, EmptyBody)

  def formData[F[_]: Sync](name: String, value: String, headers: Header*): Part[F] =
    Part(
      `Content-Disposition`("form-data", Map("name" -> name)) +: headers,
      Stream.emit(value).through(utf8Encode))

}
