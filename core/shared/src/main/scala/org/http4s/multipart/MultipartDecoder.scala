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

import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.std.Supervisor
import cats.syntax.all.*
import fs2.Collector
import fs2.Pipe
import fs2.Stream
import fs2.io.file.Files

private[http4s] object MultipartDecoder {

  /** Creates an EntityDecoder for `multipart/form-data` bodies, using
    * the given `MultipartReceiver` to decide how to handle each Part.
    *
    * The creation of the EntityDecoder happens as a Resource which,
    * when release, will release any underlying Resources that may
    * have been allocated during the decoding process (e.g. temp files
    * used for buffering Part bodies). Since the underlying Resources
    * will not be released until this Resource is released, it is not
    * recommended to reuse the allocated EntityDecoder.
    *
    * The `MultipartReceiver` is responsible for deciding the decoding
    * logic for each Part, on a case-by-case basis. Some `MultipartReceiver`s
    * are uniform, processing each part the same way. Others may be fully
    * custom, processing each part in a different way. See the [[PartReceiver]]
    * companion for examples.
    *
    * This allows the decoder to avoid unnecessary work in many cases.
    * For example, a MultipartReceiver can be defined to only expect
    * a specific set of parts, such that a request containing a data
    * in unexpected parts would cause decoding to halt with an error,
    * rather than continuing to read each part and potentially write
    * their data to buffers.
    *
    * This also allows for an explicit choice of how each part is
    * buffered, if at all. As opposed to [[mixedMultipartResource]],
    * which buffers Part bodies opaquely and exposes them as a
    * `Stream[F, Byte]`, a `MultipartReceiver` can make explicit choices
    * about the destination of, and representation of, its Part bodies.
    *
    * Furthermore, since each `Part` is subject to decoding as it is
    * encountered (i.e. before any potential buffering), when the decoding
    * of a Part fails, the overall decoding process can halt early, without
    * bothering to pull the rest of the stream.
    *
    * @param recv A MultipartReceiver which dictates that decoding logic for each Part
    * @param headerLimit Maximum acceptable size of any Part's headers
    * @param maxParts Maximum acceptable number of parts
    * @param failOnLimit If `true`, exceeding the `maxParts` will cause a decoding failure.
    *                    If `false`, any parts beyond the limit will be ignored.
    * @return A resource which allocates an EntityDecoder for `multipart/form-data`
    *         media and releases any resources allocated by that decoder.
    */
  def fromReceiver[F[_]: Concurrent, A](
      recv: MultipartReceiver[F, A],
      headerLimit: Int = 1024,
      maxParts: Option[Int] = Some(50),
      failOnLimit: Boolean = false,
  ): Resource[F, EntityDecoder[F, A]] =
    Supervisor[F].map { supervisor =>
      makeDecoder[F, recv.Partial, List, A](
        MultipartParser.decodePartsSupervised[F, recv.Partial](
          supervisor,
          _,
          part => EitherT(recv.decideOrReject(part.headers).receive(part)),
          limit = headerLimit,
          maxParts = maxParts,
          failOnLimit = failOnLimit,
        ),
        List,
        (partials, _) => recv.assemble(partials),
      )
    }

  /** A decoder for `multipart/form-data` content, where each "part"
    * is stored to an in-memory buffer which can be read repeatedly
    * as a `Stream[F, Byte]`.
    *
    * Note that this decoder should typically be avoided when expecting
    * to receive file uploads, as this would cause the content of each
    * uploaded file to be loaded into memory.
    *
    * @return A decoder for `multipart/form-data` content, with part
    *         bodies buffered in memory.
    */
  def decoder[F[_]: Concurrent]: EntityDecoder[F, Multipart[F]] =
    makeDecoder[F, Part[F], Vector, Multipart[F]](
      MultipartParser.parseToPartsStream[F](_).andThen(_.map(Right(_))),
      Vector,
      (parts, boundary) => Right(Multipart(parts, boundary)),
    )

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
      val partReceiver = PartReceiver
        .toMixedBuffer[F](maxSizeBeforeWrite, chunkSize)
        .mapWithHeaders(Part.apply[F])
      makeDecoder[F, Part[F], Vector, Multipart[F]](
        MultipartParser.decodePartsSupervised[F, Part[F]](
          supervisor,
          _,
          part => EitherT(partReceiver.receive(part)),
          limit = headerLimit,
          maxParts = Some(maxParts),
          failOnLimit = failOnLimit,
        ),
        Vector,
        (parts, boundary) => Right(Multipart[F](parts, boundary)),
      )
    }

  /** Multipart decoder that streams all parts past a threshold
    * (anything above maxSizeBeforeWrite) into a temporary file.
    *
    * Note: (BIG NOTE) Using this decoder for multipart decoding is good for the sake of
    * not holding all information in memory, as it will never have more than
    * `maxSizeBeforeWrite` in memory per part before writing to a temporary file. On top of this,
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
    makeDecoder[F, Part[F], Vector, Multipart[F]](
      MultipartParser
        .parseToPartsStreamedFile[F](
          _,
          headerLimit,
          maxSizeBeforeWrite,
          maxParts,
          failOnLimit,
        )
        .andThen(_.map(Right(_))),
      Vector,
      (parts, boundary) => Right(Multipart(parts, boundary)),
    )

  private def makeDecoder[F[_]: Concurrent, P, C[_], A](
      pipe: Boundary => Pipe[F, Byte, Either[DecodeFailure, P]],
      collector: Collector.Aux[P, C[P]],
      assemble: (C[P], Boundary) => Either[DecodeFailure, A],
  ): EntityDecoder[F, A] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundaryStr) =>
          val boundary = Boundary(boundaryStr)
          EitherT(
            msg.body
              // pipe to get individual parts or decode failures
              .through(pipe(boundary))
              // lift effect to EitherT[F, DecodeFailure, *] so that
              // `.eval`-ing each value can short-circuit the stream
              // when we get a `DecodeFailure`
              .translate(EitherT.liftK[F, DecodeFailure])
              .flatMap(r => Stream.eval(EitherT.fromEither[F](r)))
              .compile
              .to(collector)
              .subflatMap(assemble(_, boundary))
              .value
              // catch DecodeErrors that were thrown, moving them to
              // the EitherT's Left side
              .recover { case e: DecodeFailure => Left(e) }
          )
        case None =>
          DecodeResult.failureT(
            InvalidMessageBodyFailure("Missing boundary extension to Content-Type")
          )
      }
    }
}
