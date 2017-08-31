package org.http4s
package multipart

import java.io.{File, FileInputStream, InputStream}
import java.net.URL

import cats.effect.Sync
import fs2.Stream
import fs2.io.readInputStream
import fs2.text.utf8Encode
import org.http4s.headers.`Content-Disposition`
import org.http4s.util.CaseInsensitiveString
import org.http4s.{EmptyBody, Header, Headers}

final case class Part[F[_]](headers: Headers, body: Stream[F, Byte]) {
  def name: Option[String] = headers.get(`Content-Disposition`).flatMap(_.parameters.get("name"))
}

object Part {
  private val ChunkSize = 8192

  def empty[F[_]]: Part[F] =
    Part(Headers.empty, EmptyBody)

  def formData[F[_]: Sync](name: String, value: String, headers: Header*): Part[F] =
    Part(`Content-Disposition`("form-data", Map("name" -> name)) +: headers,
      Stream.emit(value).through(utf8Encode))

  def fileData[F[_]: Sync](name: String, file: File, headers: Header*): Part[F] =
    fileData(name, file.getName, new FileInputStream(file), headers: _*)

  def fileData[F[_]: Sync](name: String, resource: URL, headers: Header*): Part[F] =
    fileData(name, resource.getPath.split("/").last, resource.openStream(), headers:_*)

  def fileData[F[_]: Sync](name: String, filename: String, entityBody: EntityBody[F], headers: Header*): Part[F] =
    Part(`Content-Disposition`("form-data", Map("name" -> name, "filename" -> filename)) +:
      Header("Content-Transfer-Encoding", "binary") +:
      headers,
      entityBody)

  // The InputStream is passed by name, and we open it in the by-name
  // argument in callers, so we can avoid lifting into a Task.  Exposing
  // this API publicly would invite unsafe use, and the `EntityBody` version
  // should be safe.
  private def fileData[F[_]](name: String, filename: String, in: => InputStream, headers: Header*)(implicit F: Sync[F]): Part[F] =
    fileData(name, filename, readInputStream(F.delay(in), ChunkSize), headers: _*)
}
