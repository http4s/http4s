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

import fs2.Stream
import fs2.text.utf8
import org.http4s.headers.`Content-Disposition`
import org.typelevel.ci._

final case class Part[F[_]](headers: Headers, body: Stream[F, Byte]) extends Media[F] {
  def name: Option[String] = headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"name"))
  def filename: Option[String] =
    headers.get[`Content-Disposition`].flatMap(_.parameters.get(ci"filename"))

  override def covary[F2[x] >: F[x]]: Part[F2] = this.asInstanceOf[Part[F2]]
}

object Part extends PartCompanionPlatform {
  private[multipart] val ChunkSize = 8192

  def formData[F[_]](name: String, value: String, headers: Header.ToRaw*): Part[F] =
    Part(
      Headers(`Content-Disposition`("form-data", Map(ci"name" -> name))).put(headers: _*),
      Stream.emit(value).through(utf8.encode))

  def fileData[F[_]](
      name: String,
      filename: String,
      entityBody: EntityBody[F],
      headers: Header.ToRaw*): Part[F] =
    Part(
      Headers(
        `Content-Disposition`("form-data", Map(ci"name" -> name, ci"filename" -> filename)),
        "Content-Transfer-Encoding" -> "binary"
      ).put(headers: _*),
      entityBody
    )

}
