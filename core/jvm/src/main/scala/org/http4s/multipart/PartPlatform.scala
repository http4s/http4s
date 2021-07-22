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

import fs2.io.file.Files
import fs2.io.readInputStream
import java.io.{File, InputStream}
import cats.effect.Sync
import java.net.URL

private[multipart] trait PartCompanionPlatform { self: Part.type =>

  def fileData[F[_]: Sync](name: String, resource: URL, headers: Header.ToRaw*): Part[F] =
    fileData(name, resource.getPath.split("/").last, resource.openStream(), headers: _*)

  def fileData[F[_]: Files](name: String, file: File, headers: Header.ToRaw*): Part[F] =
    fileData(name, file.getName, Files[F].readAll(file.toPath, ChunkSize), headers: _*)

  // The InputStream is passed by name, and we open it in the by-name
  // argument in callers, so we can avoid lifting into an effect.  Exposing
  // this API publicly would invite unsafe use, and the `EntityBody` version
  // should be safe.
  private[multipart] def fileData[F[_]](
      name: String,
      filename: String,
      in: => InputStream,
      headers: Header.ToRaw*)(implicit F: Sync[F]): Part[F] =
    fileData(name, filename, readInputStream(F.delay(in), ChunkSize), headers: _*)
}
