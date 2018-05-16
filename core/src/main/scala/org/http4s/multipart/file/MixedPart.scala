package org.http4s
package multipart
package file

import cats.effect.Sync
import fs2.Stream
import fs2.io.{readInputStream, file => fs2File}
import fs2.text.utf8Encode
import java.io.{File, FileInputStream, InputStream}
import java.net.URL
import java.nio.file.{Files, Path}

import org.http4s.headers.`Content-Disposition`

sealed abstract class MixedPart[F[_]] {
  def headers: Headers

  def body(implicit F: Sync[F]): Stream[F, Byte]

  def name: Option[String] = headers.get(`Content-Disposition`).flatMap(_.parameters.get("name"))

  def filename: Option[String] =
    headers.get(`Content-Disposition`).flatMap(_.parameters.get("filename"))
}

final case class BasicPart[F[_]](headers: Headers, bodyStream: Stream[F, Byte])
    extends MixedPart[F] {

  def body(implicit F: Sync[F]): Stream[F, Byte] = bodyStream
}

final case class FilePart[F[_]](headers: Headers, fileStream: Stream[F, Byte], filePath: Path)
    extends MixedPart[F] {

  def cleanupFile(implicit F: Sync[F]): F[Unit] =
    F.delay(Files.delete(filePath))

  def body(implicit F: Sync[F]): Stream[F, Byte] = fileStream.onFinalize(cleanupFile)
}

object MixedPart {
  private val ChunkSize = 8192

  def formData[F[_]: Sync](name: String, value: String, headers: Header*): MixedPart[F] =
    BasicPart(
      `Content-Disposition`("form-data", Map("name" -> name)) +: headers,
      Stream.emit(value).through(utf8Encode))

  def fileData[F[_]: Sync](name: String, file: File, headers: Header*): MixedPart[F] =
    fileData(name, file.getName, new FileInputStream(file), headers: _*)

  def fileData[F[_]: Sync](name: String, resource: URL, headers: Header*): MixedPart[F] =
    fileData(name, resource.getPath.split("/").last, resource.openStream(), headers: _*)

  def fileData[F[_]: Sync](
      name: String,
      filename: String,
      entityBody: EntityBody[F],
      headers: Header*): MixedPart[F] =
    BasicPart(
      `Content-Disposition`("form-data", Map("name" -> name, "filename" -> filename)) +:
        Header("Content-Transfer-Encoding", "binary") +: headers,
      entityBody)

  /** a part from a file path that gets deleted on stream completion **/
  def temporaryFileData[F[_]: Sync](
      name: String,
      filename: String,
      path: Path,
      headers: Header*): MixedPart[F] =
    FilePart(
      `Content-Disposition`("form-data", Map("name" -> name, "filename" -> filename)) +:
        Header("Content-Transfer-Encoding", "binary") +: headers,
      fs2File.readAll[F](path, ChunkSize),
      path
    )

  // The InputStream is passed by name, and we open it in the by-name
  // argument in callers, so we can avoid lifting into an effect.  Exposing
  // this API publicly would invite unsafe use, and the `EntityBody` version
  // should be safe.
  private def fileData[F[_]](name: String, filename: String, in: => InputStream, headers: Header*)(
      implicit F: Sync[F]): MixedPart[F] =
    fileData(name, filename, readInputStream[F](F.delay(in), ChunkSize), headers: _*)
}
