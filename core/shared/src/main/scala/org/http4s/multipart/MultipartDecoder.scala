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

import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.std.Supervisor
import cats.syntax.all._
import fs2.Pipe
import fs2.io.file.Files

private[http4s] object MultipartDecoder {
  def decoder[F[_]: Concurrent]: EntityDecoder[F, Multipart[F]] =
    makeDecoder(MultipartParser.parseToPartsStream[F](_))

  /** Multipart decoder that streams all parts past a threshold
    * (anything above `maxSizeBeforeWrite`) into a temporary file.
    * The decoder is only valid inside the `Resource` scope; once
    * the `Resource` is released, all the created files are deleted.
    *
    * Note that no files are deleted until the `Resource` is released.
    * Thus, sharing and reusing the resulting `EntityDecoder` is not
    * recommended, and can lead to disk space leaks.
    *
    * The intended way to use this is as follows:
    *
    * {{{
    * mixedMultipartResource[F]()
    *   .flatTap(request.decodeWith(_, strict = true))
    *   .use { multipart =>
    *     // Use the decoded entity
    *   }
    * }}}
    *
    * @param headerLimit the max size for the headers, in bytes. This is required as
    *                    headers are strictly evaluated and parsed.
    * @param maxSizeBeforeWrite the maximum size of a particular part before writing to a file is triggered
    * @param maxParts the maximum number of parts this decoder accepts. NOTE: this also may mean that a body that doesn't
    *                 conform perfectly to the spec (i.e isn't terminated properly) but has a lot of parts might
    *                 be parsed correctly, despite the total body being malformed due to not conforming to the multipart
    *                 spec. You can control this by `failOnLimit`, by setting it to true if you want to raise
    *                 an error if sending too many parts to a particular endpoint
    * @param failOnLimit Fail if `maxParts` is exceeded _during_ multipart parsing.
    * @param chunkSize the size of chunks created when reading data from temporary files.
    * @return A multipart/form-data encoded vector of parts with some part bodies held in
    *         temporary files.
    */
  def mixedMultipartResource[F[_]: Concurrent: Files](
      headerLimit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false,
      chunkSize: Int = 8192,
  ): Resource[F, EntityDecoder[F, Multipart[F]]] =
    Supervisor[F].map { supervisor =>
      makeDecoder(
        MultipartParser.parseToPartsSupervisedFile[F](
          supervisor,
          _,
          headerLimit,
          maxSizeBeforeWrite,
          maxParts,
          failOnLimit,
          chunkSize,
        )
      )
    }

  /** Multipart decoder that streams all parts past a threshold
    * (anything above maxSizeBeforeWrite) into a temporary file.
    *
    * Note: (BIG NOTE) Using this decoder for multipart decoding is good for the sake of
    * not holding all information in memory, as it will never have more than
    * `maxSizeBeforeWrite` in memory before writing to a temporary file. On top of this,
    * you can gate the # of parts to further stop the quantity of parts you can have.
    * That said, because after a threshold it writes into a temporary file, given
    * bincompat reasons on 0.18.x, there is no way to make a distinction about which `Part[F]`
    * is a stream reference to a file or not. Thus, consumers using this decoder
    * should drain all `Part[F]` bodies if they were decoded correctly. That said,
    * this decoder gives you more control about how many part bodies it parses in the first place, thus you can have
    * more fine-grained control about how many parts you accept.
    *
    * @param headerLimit the max size for the headers, in bytes. This is required as
    *                    headers are strictly evaluated and parsed.
    * @param maxSizeBeforeWrite the maximum size of a particular part before writing to a file is triggered
    * @param maxParts the maximum number of parts this decoder accepts. NOTE: this also may mean that a body that doesn't
    *                 conform perfectly to the spec (i.e isn't terminated properly) but has a lot of parts might
    *                 be parsed correctly, despite the total body being malformed due to not conforming to the multipart
    *                 spec. You can control this by `failOnLimit`, by setting it to true if you want to raise
    *                 an error if sending too many parts to a particular endpoint
    * @param failOnLimit Fail if `maxParts` is exceeded _during_ multipart parsing.
    * @return A multipart/form-data encoded vector of parts with some part bodies held in
    *         temporary files.
    */
  @deprecated("Use mixedMultipartResource", "0.23")
  def mixedMultipart[F[_]: Concurrent: Files](
      headerLimit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false,
  ): EntityDecoder[F, Multipart[F]] =
    makeDecoder(
      MultipartParser.parseToPartsStreamedFile[F](
        _,
        headerLimit,
        maxSizeBeforeWrite,
        maxParts,
        failOnLimit,
      )
    )

  private def makeDecoder[F[_]: Concurrent](
      impl: Boundary => Pipe[F, Byte, Part[F]]
  ): EntityDecoder[F, Multipart[F]] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(impl(Boundary(boundary)))
              .compile
              .toVector
              .map[Either[DecodeFailure, Multipart[F]]](parts =>
                Right(Multipart(parts, Boundary(boundary)))
              )
              .recover { case e: DecodeFailure =>
                Left(e)
              }
          }
        case None =>
          DecodeResult.failureT(
            InvalidMessageBodyFailure("Missing boundary extension to Content-Type")
          )
      }
    }
}
