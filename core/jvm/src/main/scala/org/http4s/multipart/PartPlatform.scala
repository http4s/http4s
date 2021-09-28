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
import fs2.io.file.Files
import fs2.io.file.Path
import java.io.{File, InputStream}

private[multipart] trait PartCompanionPlatform { self: Part.type =>
  @deprecated("Use overload with fs2.io.file.Path", "0.23.5")
  def fileData[F[_]: Files](name: String, file: File, headers: Header.ToRaw*): Part[F] =
    fileData(name, Path.fromNioPath(file.toPath), headers: _*)

  protected def fileData[F[_]: Sync](
      name: String,
      filename: String,
      in: => InputStream,
      headers: Header.ToRaw*): Part[F]
}
