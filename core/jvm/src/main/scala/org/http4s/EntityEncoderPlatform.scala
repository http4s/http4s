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

import fs2.io.file.Files
import java.io.File
import java.nio.file.Path

private[http4s] trait EntityEncoderCompanionPlatform { self: EntityEncoder.type =>

  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  @deprecated("Use pathEncoder with fs2.io.file.Path", "0.23.5")
  implicit def fileEncoder[F[_]: Files]: EntityEncoder[F, File] =
    pathEncoder.contramap(f => fs2.io.file.Path.fromNioPath(f.toPath()))

  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  @deprecated("Use pathEncoder with fs2.io.file.Path", "0.23.5")
  implicit def filePathEncoder[F[_]: Files]: EntityEncoder[F, Path] =
    pathEncoder.contramap(p => fs2.io.file.Path.fromNioPath(p))

}
