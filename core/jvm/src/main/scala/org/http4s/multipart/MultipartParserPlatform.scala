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
import fs2.Chunk
import fs2.Pipe
import fs2.Pull
import fs2.Pure
import fs2.RaiseThrowable
import fs2.Stream
import fs2.io.file.Files
import org.http4s.internal.bug

import java.nio.file.Path

trait MultipartParserPlatform { self: MultipartParser.type =>

  ////////////////////////////////////////////////////////////
  // File writing encoder
  ///////////////////////////////////////////////////////////

  /** Same as the other streamed parsing, except
    * after a particular size, it buffers on a File.
    */
  @deprecated("Use parseSupervisedFile", "0.23")
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

  @deprecated("Use parseSupervisedFile", "0.23")
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
            .through(
              Files[F].writeAll(fs2.io.file.Path.fromNioPath(fileRef), fs2.io.file.Flags.Append))
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
                  .through(Files[F]
                    .writeAll(fs2.io.file.Path.fromNioPath(fileRef), fs2.io.file.Flags.Append))
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
          .eval(Files[F].tempFile(None, "", "", None).allocated)
          .flatMap { case (path, cleanup) =>
            streamAndWrite(s, lacc, limitCTR, path.toNioPath)
              .tupleLeft(
                Files[F]
                  .readAll(path, maxBeforeWrite, fs2.io.file.Flags.Read)
                  .onFinalizeWeak(cleanup))
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

  /////////////////////////////////////
  // Resource-safe file-based parser //
  /////////////////////////////////////

  /** Like parseStreamedFile, but the produced parts' resources are managed by the supervisor.
    */
  private[multipart] def parseSupervisedFile[F[_]: Concurrent: Files](
      supervisor: Supervisor[F],
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false,
      chunkSize: Int = 8192
  ): Pipe[F, Byte, Multipart[F]] = { st =>
    st.through(
      parseToPartsSupervisedFile(
        supervisor,
        boundary,
        limit,
        maxSizeBeforeWrite,
        maxParts,
        failOnLimit,
        chunkSize)
    ).fold(Vector.empty[Part[F]])(_ :+ _)
      .map(Multipart(_, boundary))
  }

  private[multipart] def parseToPartsSupervisedFile[F[_]](
      supervisor: Supervisor[F],
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false,
      chunkSize: Int = 8192
  )(implicit F: Concurrent[F], files: Files[F]): Pipe[F, Byte, Part[F]] = {
    val createFile = superviseResource(supervisor, files.tempFile)
    def append(file: Path, bytes: Stream[Pure, Byte]): F[Unit] =
      bytes
        .through(files.writeAll(fs2.io.file.Path.fromNioPath(file), fs2.io.file.Flags.Append))
        .compile
        .drain

    final case class Acc(file: Option[Path], bytes: Stream[Pure, Byte], bytesSize: Int)

    def stepPartChunk(oldAcc: Acc, chunk: Chunk[Byte]): F[Acc] = {
      val newSize = oldAcc.bytesSize + chunk.size
      val newBytes = oldAcc.bytes ++ Stream.chunk(chunk)
      if (newSize > maxSizeBeforeWrite) {
        oldAcc.file
          .fold(createFile.map(_.toNioPath))(F.pure)
          .flatTap(append(_, newBytes))
          .map(newFile => Acc(Some(newFile), Stream.empty, 0))
      } else F.pure(Acc(oldAcc.file, newBytes, newSize))
    }

    val stepPartEnd: Acc => F[Stream[F, Byte]] = {
      case Acc(None, bytes, _) => F.pure(bytes)
      case Acc(Some(file), bytes, size) =>
        append(file, bytes)
          .whenA(size > 0)
          .as(
            files.readAll(
              fs2.io.file.Path.fromNioPath(file),
              chunkSize = chunkSize,
              fs2.io.file.Flags.Read)
          )
    }

    val step: (Option[(Headers, Acc)], Event) => F[(Option[(Headers, Acc)], Option[Part[F]])] = {
      case (None, PartStart(headers)) =>
        val newAcc = Acc(None, Stream.empty, 0)
        F.pure((Some((headers, newAcc)), None))
      // Shouldn't happen if the `parseToEventsStream` contract holds.
      case (None, (_: PartChunk | PartEnd)) =>
        F.raiseError(bug("Missing PartStart"))
      case (Some((headers, oldAcc)), PartChunk(chunk)) =>
        stepPartChunk(oldAcc, chunk).map { newAcc =>
          (Some((headers, newAcc)), None)
        }
      case (Some((headers, acc)), PartEnd) =>
        // Part done - emit it and start over.
        stepPartEnd(acc)
          .map(body => (None, Some(Part(headers, body))))
      // Shouldn't happen if the `parseToEventsStream` contract holds.
      case (Some(_), _: PartStart) =>
        F.raiseError(bug("Missing PartEnd"))
    }

    _.through(
      parseEvents(boundary, limit)
    ).through(
      limitParts(maxParts, failOnLimit)
    ).evalMapAccumulate(none[(Headers, Acc)])(step)
      .mapFilter(_._2)
  }

  // Acquire the resource in a separate fiber, which will remain running until the provided
  // supervisor sees fit to cancel it. The resulting action waits for the resource to be acquired.
  private[this] def superviseResource[F[_], A](
      supervisor: Supervisor[F],
      resource: Resource[F, A]
  )(implicit F: Concurrent[F]): F[A] =
    F.deferred[Either[Throwable, A]].flatMap { deferred =>
      supervisor.supervise[Nothing](
        resource.attempt
          .evalTap(deferred.complete)
          // In case of an error the exception brings down the fiber.
          .rethrow
          // Success - keep the resource alive until the supervisor cancels this fiber.
          .useForever
      ) *> deferred.get.rethrow
    }

}
