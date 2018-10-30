package org.http4s
package multipart

import java.io.{File, InputStream}
import java.net.URL

import cats.effect.{ContextShift, Sync}
import fs2.io.readInputStream
import fs2.io.file.readAll
import org.http4s.headers.`Content-Disposition`
import org.http4s.Header
import scala.concurrent.ExecutionContext

trait PlatformPart {
  private val ChunkSize = 8192

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
      `Content-Disposition`("form-data", Map("name" -> name, "filename" -> filename)) +:
        Header("Content-Transfer-Encoding", "binary") +:
        headers,
      entityBody)

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
