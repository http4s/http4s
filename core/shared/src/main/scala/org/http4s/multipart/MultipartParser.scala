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
import cats.effect.Deferred
import cats.effect.Resource
import cats.effect.std.Supervisor
import cats.syntax.all.*
import fs2.Chunk
import fs2.Pipe
import fs2.Pull
import fs2.Pure
import fs2.RaiseThrowable
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Flags
import fs2.io.file.Path
import org.http4s.internal.bug
import org.typelevel.ci.CIString

import fs2.concurrent.Channel

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {
  private[this] val CRLFBytesN = Array[Byte]('\r', '\n')
  private[this] val DoubleCRLFBytesN = Array[Byte]('\r', '\n', '\r', '\n')
  private[this] val DashDashBytesN = Array[Byte]('-', '-')
  private[this] val BoundaryBytesN: Boundary => Array[Byte] = boundary =>
    boundary.value.getBytes("UTF-8")
  val StartLineBytesN: Boundary => Array[Byte] = BoundaryBytesN.andThen(DashDashBytesN ++ _)

  /** `delimiter` in RFC 2046 */
  private[this] val ExpectedBytesN: Boundary => Array[Byte] =
    BoundaryBytesN.andThen(CRLFBytesN ++ DashDashBytesN ++ _)
  private[this] val dashByte: Byte = '-'.toByte
  private[this] val streamEmpty = Stream.empty

  private type SplitStream[F[_]] = Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])]

  private[this] sealed trait Event
  private[this] final case class PartStart(value: Headers) extends Event
  private[this] final case class PartChunk(value: Chunk[Byte]) extends Event
  private[this] case object PartEnd extends Event

  def parseStreamed[F[_]: Concurrent](
      boundary: Boundary,
      limit: Int = 1024,
  ): Pipe[F, Byte, Multipart[F]] = { st =>
    st.through(
      parseToPartsStream(boundary, limit)
    ).fold(Vector.empty[Part[F]])(_ :+ _)
      .map(Multipart(_, boundary))
  }

  def parseToPartsStream[F[_]](boundary: Boundary, limit: Int = 1024)(implicit
      F: Concurrent[F]
  ): Pipe[F, Byte, Part[F]] = { st =>
    st.through(
      parseEvents[F](boundary, limit)
    )
      // The left half is the part under construction, the right half is a part to be emitted.
      .evalMapAccumulate[F, Option[Part[F]], Option[Part[F]]](None) { (acc, item) =>
        (acc, item) match {
          case (None, PartStart(headers)) =>
            F.pure((Some(Part(headers, Stream.empty)), None))
          // Shouldn't happen if the `parseToEventsStream` contract holds.
          case (None, _: PartChunk | PartEnd) =>
            F.raiseError(bug("Missing PartStart"))
          case (Some(acc0), PartChunk(chunk)) =>
            F.pure((Some(acc0.copy(body = acc0.body ++ Stream.chunk(chunk))), None))
          case (Some(_), PartEnd) =>
            // Part done - emit it and start over.
            F.pure((None, acc))
          // Shouldn't happen if the `parseToEventsStream` contract holds.
          case (Some(_), _: PartStart) =>
            F.raiseError(bug("Missing PartEnd"))
        }
      }
      .mapFilter(_._2)
  }

  private def splitAndIgnorePrev[F[_]](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte],
  ): (Int, Stream[F, Byte]) = {
    var i = 0
    var currState = state
    val len = values.length
    while (currState < len && i < c.size) {
      if (c(i) == values(currState))
        currState += 1
      else if (c(i) == values(0))
        currState = 1
      else
        currState = 0
      i += 1
    }

    if (currState == 0)
      (0, Stream.empty)
    else if (currState == len)
      (currState, Stream.chunk(c.drop(i)))
    else
      (currState, Stream.empty)
  }

  /** Split a chunk in the case of a complete match:
    *
    * If it is a chunk that is between a partial match
    * (middleChunked), consider the prior partial match
    * as part of the data to emit.
    *
    * If it is a fully matched, fresh chunk (no carry over partial match),
    * emit everything until the match, and everything after the match.
    *
    * If it is the continuation of a partial match,
    * emit everything after the partial match.
    */
  private def splitCompleteMatch[F[_]](
      middleChunked: Boolean,
      sti: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte],
  ): (Int, Stream[F, Byte], Stream[F, Byte]) =
    if (middleChunked)
      (
        sti,
        // Emit the partial match as well
        acc ++ carry ++ Stream.chunk(c.take(i - sti)),
        Stream.chunk(c.drop(i)),
      ) // Emit after the match
    else
      (
        sti,
        acc, // block completes partial match, so do not emit carry
        Stream.chunk(c.drop(i)),
      ) // Emit everything after the match

  /** Split a chunk in the case of a partial match:
    *
    * DO NOT USE. Was made private[http4s] because
    * Jose messed up hard like 5 patches ago and now it breaks bincompat to
    * remove.
    */
  private def splitPartialMatch[F[_]](
      middleChunked: Boolean,
      currState: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte],
  ): (Int, Stream[F, Byte], Stream[F, Byte]) = {
    val ixx = i - currState
    if (middleChunked) {
      val (lchunk, rchunk) = c.splitAt(ixx)
      (currState, acc ++ carry ++ Stream.chunk(lchunk), Stream.chunk(rchunk))
    } else
      (currState, acc, carry ++ Stream.chunk(c))
  }

  /** Split a chunk as part of either a left or right
    * stream depending on the byte sequence in `values`.
    *
    * `state` represents the current counter position
    * for `values`, which is necessary to keep track of in the
    * case of partial matches.
    *
    * `acc` holds the cumulative left stream values,
    * and `carry` holds the values that may possibly
    * be the byte sequence. As such, carry is re-emitted if it was an
    * incomplete match, or ignored (as such excluding the sequence
    * from the subsequent split stream).
    */
  private[http4s] def splitOnChunk[F[_]](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte],
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
  ): (Int, Stream[F, Byte], Stream[F, Byte]) = {
    var i = 0
    var currState = state
    val len = values.length
    while (currState < len && i < c.size) {
      if (c(i) == values(currState))
        currState += 1
      else if (c(i) == values(0))
        currState = 1
      else
        currState = 0
      i += 1
    }
    // It will only be zero if
    // the chunk matches from the very beginning,
    // since currstate can never be greater than
    // (i + state).
    val middleChunked = i + state - currState > 0

    if (currState == 0)
      (0, acc ++ carry ++ Stream.chunk(c), Stream.empty)
    else if (currState == len)
      splitCompleteMatch(middleChunked, currState, i, acc, carry, c)
    else
      splitPartialMatch(middleChunked, currState, i, acc, carry, c)
  }

  /** Split a stream in half based on `values`,
    * but check if it is either double dash terminated (end of multipart).
    * SplitOrFinish also tracks a header limit size
    *
    * If it is, drain the epilogue and return the empty stream. if it is not,
    * split on the `values` and raise an error if we lack a match
    */
  private def splitOrFinish[F[_]: Concurrent](
      values: Array[Byte],
      stream: Stream[F, Byte],
      limit: Int,
  ): SplitStream[F] = {
    // Check if a particular chunk a final chunk, that is,
    // whether it's the boundary plus an extra "--", indicating it's
    // the last boundary
    def checkIfLast(c: Chunk[Byte], rest: Stream[F, Byte]): SplitStream[F] = {
      // precond: both c1 and c2 are nonempty chunks
      def checkTwoNonEmpty(
          c1: Chunk[Byte],
          c2: Chunk[Byte],
          remaining: Stream[F, Byte],
      ): SplitStream[F] =
        if (c1(0) == dashByte && c2(0) == dashByte)
          // Drain the multipart epilogue.
          Pull.eval(rest.compile.drain) *>
            Pull.pure((streamEmpty, streamEmpty))
        else {
          val (ix, l, r, add) =
            splitOnChunkLimited[F](
              values,
              0,
              Chunk.array(c1.toArray[Byte] ++ c2.toArray[Byte]),
              Stream.empty,
              Stream.empty,
            )
          go(remaining, ix, l, r, add)
        }

      if (c.size == 1)
        rest.pull.uncons.flatMap {
          case Some((chnk, remaining)) =>
            checkTwoNonEmpty(c, chnk, remaining)
          case None =>
            Pull.raiseError[F](MalformedMessageBodyFailure("Malformed Multipart ending"))
        }
      else if (c(0) == dashByte && c(1) == dashByte)
        // Drain the multipart epilogue.
        Pull.eval(rest.compile.drain) *>
          Pull.pure((streamEmpty, streamEmpty))
      else {
        val (ix, l, r, add) =
          splitOnChunkLimited[F](values, 0, c, Stream.empty, Stream.empty)
        go(rest, ix, l, r, add)
      }
    }

    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte],
        limitCTR: Int,
    ): SplitStream[F] =
      if (limitCTR >= limit)
        Pull.raiseError[F](
          MalformedMessageBodyFailure(s"Part header was longer than $limit-byte limit")
        )
      else if (state == values.length)
        Pull.pure((lacc, racc ++ s))
      else
        s.pull.uncons.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r, add) = splitOnChunkLimited[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r, limitCTR + add)
          case None =>
            Pull.raiseError[F](MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
        }

    stream.pull.uncons.flatMap {
      case Some((chunk, rest)) =>
        checkIfLast(chunk, rest)
      case None =>
        Pull.raiseError[F](MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
    }
  }

  /** Take the stream of headers separated by
    * double CRLF bytes and return the headers
    */
  private def parseHeaders[F[_]: Concurrent](strim: Stream[F, Byte]): F[Headers] = {
    def tailrecParse(s: Stream[F, Byte], headers: Headers): Pull[F, Headers, Unit] =
      splitHalf[F](CRLFBytesN, s).flatMap { case (l, r) =>
        l.through(fs2.text.utf8.decode[F])
          .fold("")(_ ++ _)
          .map { string =>
            val ix = string.indexOf(':')
            if (ix >= 0)
              headers.put(
                Header.Raw(CIString(string.substring(0, ix)), string.substring(ix + 1).trim)
              )
            else
              headers
          }
          .pull
          .echo >> r.pull.uncons.flatMap {
          case Some(_) =>
            tailrecParse(r, headers)
          case None =>
            Pull.done
        }
      }

    tailrecParse(strim, Headers.empty).stream.compile.foldMonoid
  }

  /** Spit our `Stream[F, Byte]` into two halves.
    * If we reach the end and the state is 0 (meaning we didn't match at all),
    * then we return the concatenated parts of the stream.
    *
    * This method _always_ caps
    */
  private def splitHalf[F[_]](values: Array[Byte], stream: Stream[F, Byte]): SplitStream[F] = {
    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte],
    ): SplitStream[F] =
      if (state == values.length)
        Pull.pure((lacc, racc ++ s))
      else
        s.pull.uncons.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r) = splitOnChunk[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r)
          case None =>
            // We got to the end, and matched on nothing.
            Pull.pure((lacc ++ racc, streamEmpty))
        }

    stream.pull.uncons.flatMap {
      case Some((chunk, rest)) =>
        val (ix, l, r) = splitOnChunk[F](values, 0, chunk, Stream.empty, Stream.empty)
        go(rest, ix, l, r)
      case None =>
        Pull.pure((streamEmpty, streamEmpty))
    }
  }

  /** Split a chunk in the case of a complete match:
    *
    * If it is a chunk that is between a partial match
    * (middleChunked), consider the prior partial match
    * as part of the data to emit.
    *
    * If it is a fully matched, fresh chunk (no carry over partial match),
    * emit everything until the match, and everything after the match.
    *
    * If it is the continuation of a partial match,
    * emit everything after the partial match.
    */
  private def splitCompleteLimited[F[_]](
      state: Int,
      middleChunked: Boolean,
      sti: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte],
  ): (Int, Stream[F, Byte], Stream[F, Byte], Int) =
    if (middleChunked)
      (
        sti,
        // Emit the partial match as well
        acc ++ carry ++ Stream.chunk(c.take(i - sti)),
        // Emit after the match
        Stream.chunk(c.drop(i)),
        state + i - sti,
      )
    else
      (
        sti,
        acc, // block completes partial match, so do not emit carry
        Stream.chunk(c.drop(i)), // Emit everything after the match
        0,
      )

  /** Split a chunk in the case of a partial match:
    *
    * If it is a chunk that is between a partial match
    * (middle chunked), the prior partial match is added to
    * the accumulator, and the current partial match is
    * considered to carry over.
    *
    * If it is a fresh chunk (no carry over partial match),
    * everything prior to the partial match is added to the accumulator,
    * and the partial match is considered the carry over.
    *
    * Else, if the whole block is a partial match,
    * add it to the carry over
    */
  private[http4s] def splitPartialLimited[F[_]](
      state: Int,
      middleChunked: Boolean,
      currState: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte],
  ): (Int, Stream[F, Byte], Stream[F, Byte], Int) = {
    val ixx = i - currState
    if (middleChunked) {
      val (lchunk, rchunk) = c.splitAt(ixx)
      (
        currState,
        acc ++ carry ++ Stream.chunk(lchunk), // Emit previous carry
        Stream.chunk(rchunk),
        state + ixx,
      )
    } else
      // Whole thing is partial match
      (currState, acc, carry ++ Stream.chunk(c), 0)
  }

  private[http4s] def splitOnChunkLimited[F[_]](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte],
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
  ): (Int, Stream[F, Byte], Stream[F, Byte], Int) = {
    var i = 0
    var currState = state
    val len = values.length
    while (currState < len && i < c.size) {
      if (c(i) == values(currState))
        currState += 1
      else if (c(i) == values(0))
        currState = 1
      else
        currState = 0
      i += 1
    }

    // It will only be zero if
    // the chunk matches from the very beginning,
    // since currstate can never be greater than
    // (i + state).
    val middleChunked = i + state - currState > 0

    if (currState == 0)
      (0, acc ++ carry ++ Stream.chunk(c), Stream.empty, i)
    else if (currState == len)
      splitCompleteLimited(state, middleChunked, currState, i, acc, carry, c)
    else
      splitPartialLimited(state, middleChunked, currState, i, acc, carry, c)
  }

  // //////////////////////////////////////////////////////////
  // File writing encoder
  // /////////////////////////////////////////////////////////

  /** Same as the other streamed parsing, except
    * after a particular size, it buffers on a File.
    */
  @deprecated("Use parseSupervisedFile", "0.23")
  def parseStreamedFile[F[_]: Concurrent: Files](
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false,
  ): Pipe[F, Byte, Multipart[F]] = { st =>
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
      failOnLimit: Boolean = false,
  ): Pipe[F, Byte, Part[F]] = {

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
      failOnLimit: Boolean,
  ): Pipe[F, Event, Event] = {
    def go(st: Stream[F, Event], partsCounter: Int): Pull[F, Event, Unit] =
      st.pull.uncons1.flatMap {
        case Some((event: PartStart, rest)) =>
          if (partsCounter < maxParts) {
            Pull.output1(event) >> go(rest, partsCounter + 1)
          } else if (failOnLimit) {
            Pull.raiseError[F](MalformedMessageBodyFailure("Parts limit exceeded"))
          } else Pull.done
        case Some((event, rest)) =>
          Pull.output1(event) >> go(rest, partsCounter)
        case None => Pull.done
      }

    go(_, 0).stream
  }

  // Consume `PartChunk`s until the first `PartEnd`, produce a stream with all the consumed data.
  private[this] def partBodyFileStream[F[_]: Concurrent: Files](
      stream: Stream[F, Event],
      maxBeforeWrite: Int,
  ): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Event])] = {
    // Consume `PartChunk`s until the first `PartEnd`, and write all the data into the file.
    def streamAndWrite(
        s: Stream[F, Event],
        lacc: Stream[Pure, Byte],
        limitCTR: Int,
        fileRef: Path,
    ): Pull[F, Nothing, Stream[F, Event]] =
      if (limitCTR >= maxBeforeWrite)
        Pull.eval(
          lacc
            .through(Files[F].writeAll(fileRef, Flags.Append))
            .compile
            .drain
        ) >> streamAndWrite(s, Stream.empty, 0, fileRef)
      else
        s.pull.uncons1.flatMap {
          case Some((PartChunk(chnk), str)) =>
            streamAndWrite(str, lacc ++ Stream.chunk(chnk), limitCTR + chnk.size, fileRef)
          case Some((PartEnd, str)) =>
            Pull
              .eval(
                lacc
                  .through(Files[F].writeAll(fileRef, Flags.Append))
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
        limitCTR: Int,
    ): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Event])] =
      if (limitCTR >= maxBeforeWrite)
        Pull
          .eval(Files[F].tempFile(None, "", "", None).allocated)
          .flatMap { case (path, cleanup) =>
            streamAndWrite(s, lacc, limitCTR, path)
              .tupleLeft(Files[F].readAll(path, maxBeforeWrite, Flags.Read).onFinalizeWeak(cleanup))
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

  // ///////////////////////////////////
  // Resource-safe file-based parser //
  // ///////////////////////////////////

  /** Like parseStreamedFile, but the produced parts' resources are managed by the supervisor.
    */
  private[multipart] def parseSupervisedFile[F[_]: Concurrent: Files](
      supervisor: Supervisor[F],
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false,
      chunkSize: Int = 8192,
  ): Pipe[F, Byte, Multipart[F]] = { st =>
    st.through(
      parseToPartsSupervisedFile(
        supervisor,
        boundary,
        limit,
        maxSizeBeforeWrite,
        maxParts,
        failOnLimit,
        chunkSize,
      )
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
      chunkSize: Int = 8192,
  )(implicit F: Concurrent[F], files: Files[F]): Pipe[F, Byte, Part[F]] = {
    val createFile = superviseResource(supervisor, files.tempFile)
    def append(file: Path, bytes: Stream[Pure, Byte]): F[Unit] =
      bytes.through(files.writeAll(file, Flags.Append)).compile.drain

    final case class Acc(file: Option[Path], bytes: Stream[Pure, Byte], bytesSize: Int)

    def stepPartChunk(oldAcc: Acc, chunk: Chunk[Byte]): F[Acc] = {
      val newSize = oldAcc.bytesSize + chunk.size
      val newBytes = oldAcc.bytes ++ Stream.chunk(chunk)
      if (newSize > maxSizeBeforeWrite) {
        oldAcc.file
          .fold(createFile)(F.pure)
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
            files.readAll(file, chunkSize = chunkSize, Flags.Read)
          )
    }

    val step: (Option[(Headers, Acc)], Event) => F[(Option[(Headers, Acc)], Option[Part[F]])] = {
      case (None, PartStart(headers)) =>
        val newAcc = Acc(None, Stream.empty, 0)
        F.pure((Some((headers, newAcc)), None))
      // Shouldn't happen if the `parseToEventsStream` contract holds.
      case (None, _: PartChunk | PartEnd) =>
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
      resource: Resource[F, A],
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

  // //////////////////////////////////////
  // Resource-safe receiver-based parser //
  // //////////////////////////////////////

  /** Pipe that receives PartStart / PartChunk / PartEnd events,
    * extracting the chunk data between each PartStart / PartEnd
    * pair and feeding it through a new `partDecoder` for each part.
    * Emits the results from each `partDecoder`.
    *
    * Resources allocated by the returned Pipe are supervised by
    * the given `supervisor`, such that when the `supervisor` closes,
    * the allocated resources also close.
    *
    * The `partDecoder` may or may not actually *consume* the part body.
    * The underlying parser will wait at a `PartEnd` event until the
    * consumer finishes, i.e. the point when its Resource allocates a value
    * or raises an error. If the Resource never allocates, the Pipe will
    * hang forever.
    *
    * @param supervisor  A Supervisor used to restrict the lifetimes
    *                    of resources allocated by this Pipe
    * @param partDecoder A function that consumes the body of a part.
    *                    Will be called once for each `PartStart` in
    *                    the incoming stream.
    * @tparam A Value type returned for each successfully-parsed Part
    * @return A pipe that transforms Part events into corresponding
    *         decoded values.
    */
  private[this] def decodePartEventsSupervised[F[_], A](
      supervisor: Supervisor[F],
      partDecoder: Part[F] => DecodeResult[Resource[F, *], A],
  )(implicit F: Concurrent[F]): Pipe[F, Event, Either[DecodeFailure, A]] = {

    def pullPartStart(
        s: Stream[F, Event]
    ): Pull[F, Either[DecodeFailure, A], Unit] = s.pull.uncons1.flatMap {
      case Some((ps: PartStart, tail)) =>
        for {
          // Created a shared channel that allows us to represent the `partConsumer`
          // logic as a `Stream` consumer rather than some kind of "scan" operation.
          // As new `PartChunk` events are pulled, they will be sent to the channel,
          // and concurrently the `partConsumer` will consume the channel's `.stream`.
          channel <- Pull.eval(Channel.synchronous[F, Chunk[Byte]])

          // Since we'll run the `partConsumer` concurrently while pulling chunk events
          // from the input stream, we use a `Deferred` to allow us to block on the
          // consumer's completion. This also lets us un-block the event-pull during its
          // attempts to `send` to the channel, in case the consumer completed without
          // consuming the entire stream.
          resultPromise <- Pull.eval(Deferred[F, Either[Throwable, A]])

          // Start the "receiver" fiber to run in the background, sending
          // its result to the `resultPromise` when it becomes available
          _ <- Pull.eval(supervisor.supervise[Nothing] {
            partDecoder(Part(ps.value, channel.stream.unchunks)).value.attempt.evalTap { r =>
              resultPromise.complete(r.flatten) *> channel.close
            }
              // tries to allocate the resource but never close it, but since this
              // will be started by the supervisor, when the supervisor closes, it
              // will cancel the usage, allowing the resource to release
              .useForever
          })

          // Continue pulling Chunks for the current Part, feeding them to the receiver
          // via the shared Channel. Make sure the channel push operation doesn't block,
          // by racing its `send` effect with `resultPromise.get`, so that if the receiver
          // decides to abort early, we can stop trying to push to the channel. If the
          // receiver raises an error, stop pulling completely
          restOfStream <-
            pullUntilPartEnd(
              tail,
              chunk => {
                val keepPulling = F.pure(true)
                val stopPulling = F.pure(false)
                F.race(channel.send(chunk), resultPromise.get).flatMap {
                  case Left(Right(())) => keepPulling // send completed normally
                  case Left(Left(Channel.Closed)) =>
                    // Send no-opped because the channel was closed, presumably by the receiver side,
                    // but we care if it was closed due to an error or just due to lack of interest.
                    // In the case of an error, we should abort the pull entirely. Otherwise, we need
                    // to keep draining the data from the current Part until we find the next one.
                    resultPromise.tryGet.map {
                      case Some(Left(_)) =>
                        false // receiver raised an error; stop pulling
                      case Some(Right(_)) =>
                        true // receiver finished but didn't necessarily pull the whole stream; keep pulling
                      case None =>
                        true // receiver not finished yet; keep pulling
                    }
                  case Right(Right(_)) =>
                    keepPulling // send may have blocked, but the receiver already has a result
                  case Right(Left(_)) =>
                    stopPulling // send may have blocked, receiver raised an error, so abort the pull
                }
              },
            )
              // when this part of the Pull completes, make sure to close the channel
              // so that the `receiver` Stream sees an EOF signal.
              .handleErrorWith { err =>
                Pull.eval(channel.close) >> Pull.raiseError[F](err)
              }
              .productL(Pull.eval(channel.close))

          // Once we've reached the end of the current part, wait until the consumer
          // finishes and sends its result (or error) to the promise.
          partResult <- Pull.eval(resultPromise.get)

          // Output the partResult, continuing the Pull if it was not an error
          _ <- partResult match {
            case Right(a) => Pull.output1(Right(a)) >> pullPartStart(restOfStream)
            case Left(e: DecodeFailure) => Pull.output1(Left(e)).covary[F] >> Pull.done
            case Left(e) => Pull.raiseError[F](e)
          }
        } yield ()

      case None =>
        Pull.done

      case Some((PartEnd, _)) =>
        Pull.raiseError[F](new IllegalStateException("unexpected PartEnd"))

      case Some((PartChunk(_), _)) =>
        Pull.raiseError[F](new IllegalStateException("unexpected PartChunk"))
    }

    def pullUntilPartEnd(
        s: Stream[F, Event],
        pushChunk: Chunk[Byte] => F[Boolean],
    ): Pull[F, Nothing, Stream[F, Event]] = s.pull.uncons1.flatMap {
      case Some((PartEnd, tail)) =>
        Pull.pure(tail)
      case Some((PartChunk(chunk), tail)) =>
        Pull.eval(pushChunk(chunk)).flatMap { keepPulling =>
          if (keepPulling) pullUntilPartEnd(tail, pushChunk)
          else Pull.pure(Stream.empty)
        }
      case Some((PartStart(_), _)) | None =>
        Pull.raiseError[F](new IllegalStateException("Missing PartEnd"))
    }

    events => pullPartStart(events).stream
  }

  private[multipart] def decodePartsSupervised[F[_]: Concurrent, A](
      supervisor: Supervisor[F],
      boundary: Boundary,
      decodePart: Part[F] => DecodeResult[Resource[F, *], A],
      limit: Int = 1024,
      maxParts: Option[Int] = Some(20),
      failOnLimit: Boolean = false,
  ): Pipe[F, Byte, Either[DecodeFailure, A]] =
    _.through(
      parseEvents(boundary, limit)
    ).through(
      maxParts match {
        case Some(m) => limitParts(m, failOnLimit)
        case None => identity
      }
    ).through(
      decodePartEventsSupervised(supervisor, decodePart)
    )

  // //////////////////////////
  // Streaming event parser //
  // //////////////////////////

  /** Parse a stream of bytes into a stream of part events. The events come in the following order:
    *
    *   - one `PartStart`;
    *   - any number of `PartChunk`s;
    *   - one `PartEnd`.
    *
    * Any number of such sequences may be produced.
    */
  private[this] def parseEvents[F[_]: Concurrent](
      boundary: Boundary,
      headerLimit: Int,
  ): Pipe[F, Byte, Event] =
    skipPrelude(boundary, _)
      .flatMap(pullPartsEvents(boundary, _, headerLimit))
      .stream

  /** Drain the prelude and remove the first boundary. Only traverses until the first
    * part.
    */
  private[this] def skipPrelude[F[_]: Concurrent](
      boundary: Boundary,
      stream: Stream[F, Byte],
  ): Pull[F, Nothing, Stream[F, Byte]] = {
    val dashBoundaryBytes = StartLineBytesN(boundary)

    def go(s: Stream[F, Byte], state: Int): Pull[F, Nothing, Stream[F, Byte]] =
      s.pull.uncons.flatMap {
        case Some((chnk, rest)) =>
          val (ix, remainder) = splitAndIgnorePrev(dashBoundaryBytes, state, chnk)
          if (ix === dashBoundaryBytes.length) Pull.pure(remainder ++ rest)
          else go(rest, ix)
        case None =>
          Pull.raiseError[F](MalformedMessageBodyFailure("Malformed match"))
      }

    go(stream, 0)
  }

  /** Pull part events for parts until the end of the stream. */
  private[this] def pullPartsEvents[F[_]: Concurrent](
      boundary: Boundary,
      stream: Stream[F, Byte],
      headerLimit: Int,
  ): Pull[F, Event, Unit] = {
    val delimiterBytes = ExpectedBytesN(boundary)

    // Headers on the left, the remainder on the right.
    type Acc = (Stream[F, Byte], Stream[F, Byte])
    val pullPartEvents0: Acc => Pull[F, Event, Stream[F, Byte]] =
      (pullPartEvents[F](_, _, delimiterBytes)).tupled

    splitOrFinish[F](DoubleCRLFBytesN, stream, headerLimit)
      // We must have at least one part.
      .ensure(MalformedMessageBodyFailure("Cannot parse empty stream")) {
        // We can abuse reference equality here for efficiency, since `splitOrFinish`
        // returns `empty` on a capped stream.
        case (_, rest) => rest != streamEmpty
      }
      .flatMap(
        _.iterateWhileM { acc =>
          pullPartEvents0(acc).flatMap(
            splitOrFinish(
              DoubleCRLFBytesN,
              _,
              headerLimit,
            )
          )
        } { case (_, rest) => rest != streamEmpty }.void
      )
  }

  /** Pulls part events for a single part. */
  private[this] def pullPartEvents[F[_]: Concurrent](
      headerStream: Stream[F, Byte],
      rest: Stream[F, Byte],
      delimiterBytes: Array[Byte],
  ): Pull[F, Event, Stream[F, Byte]] =
    Pull
      .eval(parseHeaders(headerStream))
      .flatMap(headers => Pull.output1(PartStart(headers): Event))
      .productR(pullPartChunks(delimiterBytes, rest))
      .flatMap { case rest =>
        // We hit a boundary, but the rest of the stream is empty
        // and thus it's not a properly capped multipart body
        if (rest == streamEmpty)
          Pull.raiseError[F](MalformedMessageBodyFailure("Part not terminated properly"))
        else
          Pull.output1(PartEnd).as(rest)
      }

  /** Split the stream on `delimiterBytes`, emitting the left part as `PartChunk` events. */
  private[this] def pullPartChunks[F[_]: Concurrent](
      delimiterBytes: Array[Byte],
      stream: Stream[F, Byte],
  ): Pull[F, PartChunk, Stream[F, Byte]] = {
    def go(
        s: Stream[F, Byte],
        state: Int,
        racc: Stream[F, Byte],
    ): Pull[F, PartChunk, Stream[F, Byte]] =
      if (state == delimiterBytes.length)
        Pull.pure(racc ++ s)
      else
        s.pull.uncons.flatMap {
          case Some((chnk, rest)) =>
            val (ix, l, r) = splitOnChunk[F](delimiterBytes, state, chnk, Stream.empty, racc)
            l.chunks.map(PartChunk(_)).pull.echo >> {
              if (ix == delimiterBytes.length) Pull.pure(r ++ rest)
              else go(rest, ix, r)
            }
          case None =>
            Pull.raiseError[F](MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
        }

    go(stream, 0, Stream.empty)
  }
}
