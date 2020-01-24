package org.http4s
package multipart

import cats.effect.{Blocker, ContextShift, Sync}
import fs2.Stream
import fs2.io.readInputStream
import fs2.io.file.readAll
import fs2.text.utf8Encode
import java.io.{File, InputStream}
import java.net.URL
import org.http4s.headers.`Content-Disposition`

final case class Part[F[_]](headers: Headers, body: Stream[F, Byte]) extends Media[F] {
  def name: Option[String] = headers.get(`Content-Disposition`).flatMap(_.parameters.get("name"))
  def filename: Option[String] =
    headers.get(`Content-Disposition`).flatMap(_.parameters.get("filename"))
}

object Part {
  private val ChunkSize = 8192

  @deprecated(
    """Empty parts are not allowed by the multipart spec, see: https://tools.ietf.org/html/rfc7578#section-4.2
       Moreover, it allows the creation of potentially incorrect multipart bodies
    """.stripMargin,
    "0.18.12"
  )
  def empty[F[_]]: Part[F] =
    Part(Headers.empty, EmptyBody)

  def formData[F[_]](name: String, value: String, headers: Header*): Part[F] =
    Part(
      Headers(`Content-Disposition`("form-data", Map("name" -> name)) :: headers.toList),
      Stream.emit(value).through(utf8Encode))

  def fileData[F[_]: Sync: ContextShift](
      name: String,
      file: File,
      blocker: Blocker,
      headers: Header*): Part[F] =
    fileData(name, file.getName, readAll[F](file.toPath, blocker, ChunkSize), headers: _*)

  def fileData[F[_]: Sync: ContextShift](
      name: String,
      resource: URL,
      blocker: Blocker,
      headers: Header*): Part[F] =
    fileData(name, resource.getPath.split("/").last, resource.openStream(), blocker, headers: _*)

  def fileData[F[_]: Sync](
      name: String,
      filename: String,
      entityBody: EntityBody[F],
      headers: Header*): Part[F] =
    Part(
      Headers(
        `Content-Disposition`("form-data", Map("name" -> name, "filename" -> filename)) ::
          Header("Content-Transfer-Encoding", "binary") ::
          headers.toList
      ),
      entityBody
    )

  // The InputStream is passed by name, and we open it in the by-name
  // argument in callers, so we can avoid lifting into an effect.  Exposing
  // this API publicly would invite unsafe use, and the `EntityBody` version
  // should be safe.
  private def fileData[F[_]](
      name: String,
      filename: String,
      in: => InputStream,
      blocker: Blocker,
      headers: Header*)(implicit F: Sync[F], cs: ContextShift[F]): Part[F] =
    fileData(name, filename, readInputStream(F.delay(in), ChunkSize, blocker), headers: _*)
}
