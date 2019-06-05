package org.http4s
package multipart

import cats.Functor
import cats.instances.long._
import cats.effect.{ContextShift, Sync}
import fs2.Stream
import fs2.io.readInputStream
import fs2.io.file.readAll
import fs2.text.utf8Encode
import java.io.{File, InputStream}
import java.net.URL
import org.http4s.headers.`Content-Disposition`
import scala.concurrent.ExecutionContext
import java.nio.file.{Files, Path}

final case class Part[F[_]](
    headers: Headers,
    body: Stream[F, Byte],
    contentLength: Option[Long] = None) {
  def name: Option[String] = headers.get(`Content-Disposition`).flatMap(_.parameters.get("name"))
  def filename: Option[String] =
    headers.get(`Content-Disposition`).flatMap(_.parameters.get("filename"))

  def fullLength: Option[Long] = contentLength.map(_ + headers.foldMap(_.renderedLength))
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

  def formData[F[_]: Sync](name: String, value: String, headers: Header*): Part[F] =
    Part(
      Headers(`Content-Disposition`("form-data", Map("name" -> name)) :: headers.toList),
      Stream.emit(value).through(utf8Encode))

  def formDataKnownContentLength[F[_]: Sync](
      name: String,
      value: String,
      headers: Header*): Part[F] = {
    val body = Stream.emit(value).through(utf8Encode)
    val bodyLength = body.as(1L).compile.foldMonoid
    Part(
      Headers(`Content-Disposition`("form-data", Map("name" -> name)) :: headers.toList),
      Stream.emit(value).through(utf8Encode),
      Some(bodyLength))
  }

  /**
    * Caution, this function will read in the size of the body according to `java.nio.Files.size`, which may differ from the actual size.
    * Furthermore, this function will place no lock on the file and it could be modified between being sized and being read.
    */
  def fileDataKnownContentLength[F[_]: Sync: ContextShift](
      name: String,
      path: Path,
      blockingEC: ExecutionContext,
      headers: Header*): F[Part[F]] = Functor[F].map(Sync[F].delay(Files.size(path))) { size =>
    Part(
      Headers(
        `Content-Disposition`("form-data", Map("name" -> name, "filename" -> path.toFile.getName)) ::
          Header("Content-Transfer-Encoding", "binary") ::
          headers.toList
      ),
      readAll[F](path, blockingEC, ChunkSize),
      Some(size.toLong)
    )
  }

  /**
    * This function will read in the full file in order to calculate the Content-Length header.
    * Be aware that this can lead to OutOfMemory exceptions if the file is too large to fit in memory.
    */
  def fileDataBuffered[F[_]: Sync: ContextShift](
      name: String,
      path: Path,
      blockingEC: ExecutionContext,
      headers: Header*): F[Part[F]] =
    fileDataBuffered(
      name,
      path.toFile.getName,
      readAll[F](path, blockingEC, ChunkSize),
      headers: _*)

  /**
    * This function will read in the full entity body in order to calculate the Content-Length header.
    * Be aware that this can lead to OutOfMemory exceptions if it is too large to fit in memory.
    */
  def fileDataBuffered[F[_]: Sync](
      name: String,
      filename: String,
      entityBody: EntityBody[F],
      headers: Header*): F[Part[F]] = Functor[F].map(entityBody.compile.toChunk) { byteChunk =>
    Part(
      Headers(
        `Content-Disposition`("form-data", Map("name" -> name, "filename" -> filename)) ::
          Header("Content-Transfer-Encoding", "binary") ::
          headers.toList
      ),
      Stream.chunk(byteChunk),
      Some(byteChunk.size.toLong)
    )
  }

  def fileData[F[_]: Sync: ContextShift](
      name: String,
      file: File,
      blockingExecutionContext: ExecutionContext,
      headers: Header*): Part[F] =
    fileData(
      name,
      file.getName,
      readAll[F](file.toPath, blockingExecutionContext, ChunkSize),
      headers: _*)

  def fileData[F[_]: Sync: ContextShift](
      name: String,
      resource: URL,
      blockingExecutionContext: ExecutionContext,
      headers: Header*): Part[F] =
    fileData(
      name,
      resource.getPath.split("/").last,
      resource.openStream(),
      blockingExecutionContext,
      headers: _*)

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
      blockingExecutionContext: ExecutionContext,
      headers: Header*)(implicit F: Sync[F], cs: ContextShift[F]): Part[F] =
    fileData(
      name,
      filename,
      readInputStream(F.delay(in), ChunkSize, blockingExecutionContext),
      headers: _*)
}
