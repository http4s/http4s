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
import cats.syntax.all._
import fs2.{Pipe, Pull, Pure, Stream}
import fs2.io.file.Files
import java.nio.file.{Path, StandardOpenOption}
import fs2.RaiseThrowable
import org.http4s.internal.bug

trait MultipartParserPlatform { self: MultipartParser.type =>

  def parseToPartsStreamedFile[F[_]: Concurrent: Files](
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false): Pipe[F, Byte, Part[F]] = {

    val pullParts: Stream[F, Event] => Stream[F, Part[F]] =
      Pull
        .loop[F, Part[F], Stream[F, Event]](
          _.pull.uncons1.flatMap(
            _.traverse {
              case (PartStart(headers), s) =>
                partBodyFileStream(s, maxSizeBeforeWrite)
                  .flatMap { case (body, rest) =>
                    Pull.output1(Part(headers, body)).as(rest)
                  }
              // Shouldn't happen if the `parseToEventsStream` contract holds.
              case (_: PartChunk | PartEnd, _) =>
                Pull.raiseError(bug("Missing PartStart"))
            }
          )
        )(_)
        .stream

    _.through(
      parseEvents[F](boundary, limit)
    ).through(
      limitParts[F](maxParts, failOnLimit)
    ).through(pullParts)
  }

  private[this] def limitParts[F[_]: RaiseThrowable](
      maxParts: Int,
      failOnLimit: Boolean): Pipe[F, Event, Event] = {
    def go(st: Stream[F, Event], partsCounter: Int): Pull[F, Event, Unit] =
      st.pull.uncons1.flatMap {
        case Some((event: PartStart, rest)) =>
          if (partsCounter < maxParts) {
            Pull.output1(event) >> go(rest, partsCounter + 1)
          } else if (failOnLimit) {
            Pull.raiseError[F](MalformedMessageBodyFailure("Parts limit exceeded"))
          } else Pull.pure(())
        case Some((event, rest)) =>
          Pull.output1(event) >> go(rest, partsCounter)
        case None => Pull.pure(())
      }

    go(_, 0).stream
  }

  // Consume `PartChunk`s until the first `PartEnd`, produce a stream with all the consumed data.
  private[this] def partBodyFileStream[F[_]: Concurrent: Files](
      stream: Stream[F, Event],
      maxBeforeWrite: Int
  ): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Event])] = {
    // Consume `PartChunk`s until the first `PartEnd`, and write all the data into the file.
    def streamAndWrite(
        s: Stream[F, Event],
        lacc: Stream[Pure, Byte],
        limitCTR: Int,
        fileRef: Path
    ): Pull[F, Nothing, Stream[F, Event]] =
      if (limitCTR >= maxBeforeWrite)
        Pull.eval(
          lacc
            .through(Files[F].writeAll(fileRef, List(StandardOpenOption.APPEND)))
            .compile
            .drain) >> streamAndWrite(s, Stream.empty, 0, fileRef)
      else
        s.pull.uncons1.flatMap {
          case Some((PartChunk(chnk), str)) =>
            streamAndWrite(str, lacc ++ Stream.chunk(chnk), limitCTR + chnk.size, fileRef)
          case Some((PartEnd, str)) =>
            Pull
              .eval(
                lacc
                  .through(Files[F].writeAll(fileRef, List(StandardOpenOption.APPEND)))
                  .compile
                  .drain
              )
              .as(str)
          // Shouldn't happen if the `parseToEventsStream` contract holds.
          case Some((_: PartStart, _)) | None =>
            Pull.raiseError(bug("Missing PartEnd"))
        }

    // Consume `PartChunks` until the first `PartEnd`, accumulating the data in memory.
    // Produce a stream with all the accumulated data.
    // Fall back to `streamAndWrite` after the limit is reached
    def go(
        s: Stream[F, Event],
        lacc: Stream[Pure, Byte],
        limitCTR: Int): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Event])] =
      if (limitCTR >= maxBeforeWrite)
        Pull
          .eval(Files[F].tempFile(None, "", "").allocated)
          .flatMap { case (path, cleanup) =>
            streamAndWrite(s, lacc, limitCTR, path)
              .tupleLeft(Files[F].readAll(path, maxBeforeWrite).onFinalizeWeak(cleanup))
              .onError { case _ => Pull.eval(cleanup) }
          }
      else
        s.pull.uncons1.flatMap {
          case Some((PartChunk(chnk), str)) =>
            go(str, lacc ++ Stream.chunk(chnk), limitCTR + chnk.size)
          case Some((PartEnd, str)) =>
            Pull.pure((lacc, str))
          // Shouldn't happen if the `parseToEventsStream` contract holds.
          case Some((_: PartStart, _)) | None =>
            Pull.raiseError(bug("Missing PartEnd"))
        }

    go(stream, Stream.empty, 0)
  }

  ////////////////////////////////////////////////////////////
  // File writing encoder
  ///////////////////////////////////////////////////////////

  /** Same as the other streamed parsing, except
    * after a particular size, it buffers on a File.
    */
  def parseStreamedFile[F[_]: Concurrent: Files](
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false): Pipe[F, Byte, Multipart[F]] = { st =>
    st.through(
      parseToPartsStreamedFile(boundary, limit, maxSizeBeforeWrite, maxParts, failOnLimit)
    ).fold(Vector.empty[Part[F]])(_ :+ _)
      .map(Multipart(_, boundary))
  }

}
