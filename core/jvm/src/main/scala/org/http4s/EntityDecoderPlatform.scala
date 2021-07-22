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

import cats.effect.Concurrent
import fs2.io.file.Files
import java.io.File
import org.http4s.multipart.MultipartDecoder
import org.http4s.multipart.Multipart

private[http4s] trait EntityDecoderCompanionPlatform {

  // File operations
  def binFile[F[_]: Files: Concurrent](file: File): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      val pipe = Files[F].writeAll(file.toPath)
      DecodeResult.success(msg.body.through(pipe).compile.drain).map(_ => file)
    }

  def textFile[F[_]: Files: Concurrent](file: File): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      val pipe = Files[F].writeAll(file.toPath)
      DecodeResult.success(msg.body.through(pipe).compile.drain).map(_ => file)
    }

  def mixedMultipart[F[_]: Concurrent: Files](
      headerLimit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false): EntityDecoder[F, Multipart[F]] =
    MultipartDecoder.mixedMultipart(headerLimit, maxSizeBeforeWrite, maxParts, failOnLimit)

}
