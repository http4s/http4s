/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package multipart

import cats.effect.Sync
import fs2.io.readInputStream
import fs2.io.file.Files
import java.io.{File, InputStream}
import java.net.URL
import org.http4s.headers.`Content-Disposition`
import org.typelevel.ci._
import java.nio.charset.StandardCharsets

final case class Part[F[_]](headers: Headers, entity: Entity[F]) extends Media[F] {
  def name: Option[String] = headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"name"))
  def filename: Option[String] =
    headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"filename"))

  override def covary[F2[x] >: F[x]]: Part[F2] = this.asInstanceOf[Part[F2]]
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
    Part(Headers.empty, Entity.empty)

  def formData[F[_]](name: String, value: String, headers: Header.ToRaw*): Part[F] =
    Part(
      Headers(`Content-Disposition`("form-data", Map(ci"name" -> name))).put(headers: _*),
      Entity.Strict(fs2.Chunk.array(value.getBytes(StandardCharsets.UTF_8))))

  def fileData[F[_]: Files](name: String, file: File, headers: Header.ToRaw*): Part[F] =
    fileData(name, file.getName, Entity.Chunked(Files[F].readAll(file.toPath, ChunkSize)), headers: _*)

  def fileData[F[_]: Sync](name: String, resource: URL, headers: Header.ToRaw*): Part[F] =
    fileData(name, resource.getPath.split("/").last, resource.openStream(), headers: _*)

  def fileData[F[_]](name: String, filename: String, entity: Entity[F], headers: Header.ToRaw*): Part[F] =
    Part(
      Headers(
        `Content-Disposition`("form-data", Map(ci"name" -> name, ci"filename" -> filename)),
          "Content-Transfer-Encoding" -> "binary"
      ).put(headers: _*),
      entity
    )

  // The InputStream is passed by name, and we open it in the by-name
  // argument in callers, so we can avoid lifting into an effect.  Exposing
  // this API publicly would invite unsafe use, and the `EntityBody` version
  // should be safe.
  private def fileData[F[_]](
      name: String,
      filename: String,
      in: => InputStream,
      headers: Header.ToRaw*)(implicit F: Sync[F]): Part[F] =
    fileData(name, filename, Entity.Chunked(readInputStream(F.delay(in), ChunkSize)), headers: _*)
}
