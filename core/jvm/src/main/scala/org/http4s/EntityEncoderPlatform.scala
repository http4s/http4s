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

import cats.effect.Sync
import cats.syntax.all._
import fs2.io.file
import fs2.io.file.Files
import fs2.io.readInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import org.http4s.headers._

private[http4s] trait EntityEncoderCompanionPlatform { self: EntityEncoder.type =>

  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit def fileEncoder[F[_]: Files]: EntityEncoder[F, File] =
    filePathEncoder[F].contramap(_.toPath)

  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit def filePathEncoder[F[_]: Files]: EntityEncoder[F, Path] =
    encodeBy[F, Path](`Transfer-Encoding`(TransferCoding.chunked)) { p =>
      Entity(Files[F].readAll(file.Path.fromNioPath(p), 4096, file.Flags.Read)) //2 KB :P
    }

  implicit def inputStreamEncoder[F[_]: Sync, IS <: InputStream]: EntityEncoder[F, F[IS]] =
    entityBodyEncoder[F].contramap { (in: F[IS]) =>
      readInputStream[F](in.widen[InputStream], DefaultChunkSize)
    }

}
