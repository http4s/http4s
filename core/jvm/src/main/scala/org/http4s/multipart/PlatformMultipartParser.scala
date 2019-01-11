package org.http4s
package multipart

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2.io.file.{readAll, writeAll}
import fs2._
import java.nio.file._
import scala.concurrent.ExecutionContext

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
trait PlatformMultipartParser {
  this: MultipartParserBase =>

  private type SplitFileStream[F[_]] =
    Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte], Option[Path])]

  ////////////////////////////////////////////////////////////
  // File writing encoder
  ///////////////////////////////////////////////////////////

  /** Same as the other streamed parsing, except
    * after a particular size, it buffers on a File.
    */
  def parseStreamedFile[F[_]: Sync: ContextShift](
      boundary: Boundary,
      blockingExecutionContext: ExecutionContext,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false): Pipe[F, Byte, Multipart[F]] = { st =>
    ignorePreludeFileStream[F](
      boundary,
      st,
      limit,
      maxSizeBeforeWrite,
      maxParts,
      failOnLimit,
      blockingExecutionContext)
      .fold(Vector.empty[Part[F]])(_ :+ _)
      .map(Multipart(_, boundary))
  }

  def parseToPartsStreamedFile[F[_]: Sync: ContextShift](
      boundary: Boundary,
      blockingExecutionContext: ExecutionContext,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false): Pipe[F, Byte, Part[F]] = { st =>
    ignorePreludeFileStream[F](
      boundary,
      st,
      limit,
      maxSizeBeforeWrite,
      maxParts,
      failOnLimit,
      blockingExecutionContext)
  }

  /** The first part of our streaming stages:
    *
    * Ignore the prelude and remove the first boundary. Only traverses until the first
    * part
    */
  private[this] def ignorePreludeFileStream[F[_]: Sync: ContextShift](
      b: Boundary,
      stream: Stream[F, Byte],
      limit: Int,
      maxSizeBeforeWrite: Int,
      maxParts: Int,
      failOnLimit: Boolean,
      blockingExecutionContext: ExecutionContext): Stream[F, Part[F]] = {
    val values = StartLineBytesN(b)

    def go(s: Stream[F, Byte], state: Int, strim: Stream[F, Byte]): Pull[F, Part[F], Unit] =
      if (state == values.length) {
        pullPartsFileStream[F](
          b,
          strim ++ s,
          limit,
          maxSizeBeforeWrite,
          maxParts,
          failOnLimit,
          blockingExecutionContext)
      } else {
        s.pull.uncons.flatMap {
          case Some((chnk, rest)) =>
            val (ix, strim) = splitAndIgnorePrev(values, state, chnk)
            go(rest, ix, strim)
          case None =>
            Pull.raiseError[F](MalformedMessageBodyFailure("Malformed Malformed match"))
        }
      }

    stream.pull.uncons.flatMap {
      case Some((chnk, strim)) =>
        val (ix, rest) = splitAndIgnorePrev(values, 0, chnk)
        go(strim, ix, rest)
      case None =>
        Pull.raiseError[F](MalformedMessageBodyFailure("Cannot parse empty stream"))
    }.stream
  }

  /**
    *
    * @param boundary
    * @param s
    * @param limit
    * @tparam F
    * @return
    */
  private def pullPartsFileStream[F[_]: Sync: ContextShift](
      boundary: Boundary,
      s: Stream[F, Byte],
      limit: Int,
      maxBeforeWrite: Int,
      maxParts: Int,
      failOnLimit: Boolean,
      blockingExecutionContext: ExecutionContext
  ): Pull[F, Part[F], Unit] = {
    val values = DoubleCRLFBytesN
    val expectedBytes = ExpectedBytesN(boundary)

    splitOrFinish[F](values, s, limit).flatMap {
      case (l, r) =>
        //We can abuse reference equality here for efficiency
        //Since `splitOrFinish` returns `empty` on a capped stream
        //However, we must have at least one part, so `splitOrFinish` on this function
        //Indicates an error
        if (r == streamEmpty) {
          Pull.raiseError[F](MalformedMessageBodyFailure("Cannot parse empty stream"))
        } else {
          tailrecPartsFileStream[F](
            boundary,
            l,
            r,
            expectedBytes,
            limit,
            maxBeforeWrite,
            1,
            maxParts,
            failOnLimit,
            blockingExecutionContext
          )
        }
    }
  }

  private[this] def cleanupFileOption[F[_]](p: Option[Path])(
      implicit F: Sync[F]): Pull[F, Nothing, Unit] =
    p match {
      case Some(path) =>
        Pull.eval(cleanupFile(path))

      case None =>
        PullUnit //Todo: Move to fs2
    }

  private[this] def cleanupFile[F[_]](path: Path)(implicit F: Sync[F]): F[Unit] =
    F.delay(Files.delete(path))
      .handleErrorWith { err =>
        logger.error(err)("Caught error during file cleanup for multipart")
        //Swallow and report io exceptions in case
        F.unit
      }

  private[this] def tailrecPartsFileStream[F[_]: Sync: ContextShift](
      b: Boundary,
      headerStream: Stream[F, Byte],
      rest: Stream[F, Byte],
      expectedBytes: Array[Byte],
      headerLimit: Int,
      maxBeforeWrite: Int,
      partsCounter: Int,
      partsLimit: Int,
      failOnLimit: Boolean,
      blockingExecutionContext: ExecutionContext): Pull[F, Part[F], Unit] =
    Pull
      .eval(parseHeaders(headerStream))
      .flatMap { hdrs =>
        splitWithFileStream(expectedBytes, rest, maxBeforeWrite, blockingExecutionContext).flatMap {
          case (partBody, rest, fileRef) =>
            //We hit a boundary, but the rest of the stream is empty
            //and thus it's not a properly capped multipart body
            if (rest == streamEmpty) {
              cleanupFileOption[F](fileRef) >> Pull.raiseError[F](
                MalformedMessageBodyFailure("Part not terminated properly"))
            } else {
              Pull.output1(makePart(hdrs, partBody, fileRef)) >> splitOrFinish(
                DoubleCRLFBytesN,
                rest,
                headerLimit)
                .flatMap {
                  case (hdrStream, remaining) =>
                    if (hdrStream == streamEmpty) { //Empty returned if it worked fine
                      Pull.done
                    } else if (partsCounter >= partsLimit) {
                      if (failOnLimit) {
                        Pull.raiseError[F](MalformedMessageBodyFailure("Parts limit exceeded"))
                      } else {
                        Pull.done
                      }
                    } else {
                      tailrecPartsFileStream[F](
                        b,
                        hdrStream,
                        remaining,
                        expectedBytes,
                        headerLimit,
                        maxBeforeWrite,
                        partsCounter + 1,
                        partsLimit,
                        failOnLimit,
                        blockingExecutionContext)
                        .handleErrorWith(e => cleanupFileOption(fileRef) >> Pull.raiseError[F](e))
                    }
                }
            }
        }
      }

  private[this] def makePart[F[_]](hdrs: Headers, body: Stream[F, Byte], path: Option[Path])(
      implicit F: Sync[F]): Part[F] = path match {
    case Some(p) => Part(hdrs, body.onFinalize(F.delay(Files.delete(p))))
    case None => Part(hdrs, body)
  }

  /** Split the stream on `values`, but when
    */
  //noinspection ScalaStyle
  private def splitWithFileStream[F[_]](
      values: Array[Byte],
      stream: Stream[F, Byte],
      maxBeforeWrite: Int,
      blockingExecutionContext: ExecutionContext)(
      implicit F: Sync[F],
      cs: ContextShift[F]): SplitFileStream[F] = {

    def streamAndWrite(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte],
        limitCTR: Int,
        fileRef: Path): SplitFileStream[F] =
      if (state == values.length) {
        Pull.eval(
          lacc
            .through(
              writeAll[F](fileRef, blockingExecutionContext, List(StandardOpenOption.APPEND)))
            .compile
            .drain) >> Pull.pure(
          (readAll[F](fileRef, blockingExecutionContext, maxBeforeWrite), racc ++ s, Some(fileRef)))
      } else if (limitCTR >= maxBeforeWrite) {
        Pull.eval(
          lacc
            .through(io.file
              .writeAll[F](fileRef, blockingExecutionContext, List(StandardOpenOption.APPEND)))
            .compile
            .drain) >> streamAndWrite(s, state, Stream.empty, racc, 0, fileRef)
      } else {
        s.pull.uncons.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r, add) = splitOnChunkLimited[F](values, state, chnk, lacc, racc)
            streamAndWrite(str, ix, l, r, limitCTR + add, fileRef)
          case None =>
            Pull.eval(F.delay(Files.delete(fileRef)).attempt) >> Pull.raiseError[F](
              MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
        }
      }

    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte],
        limitCTR: Int): SplitFileStream[F] =
      if (limitCTR >= maxBeforeWrite) {
        Pull
          .eval(F.delay(Files.createTempFile("", "")))
          .flatMap { path =>
            (for {
              _ <- Pull.eval(
                lacc.through(writeAll[F](path, blockingExecutionContext)).compile.drain)
              split <- streamAndWrite(s, state, Stream.empty, racc, 0, path)
            } yield split)
              .handleErrorWith(e => Pull.eval(cleanupFile(path)) >> Pull.raiseError[F](e))
          }
      } else if (state == values.length) {
        Pull.pure((lacc, racc ++ s, None))
      } else {
        s.pull.uncons.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r, add) = splitOnChunkLimited[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r, limitCTR + add)
          case None =>
            Pull.raiseError[F](MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
        }
      }

    stream.pull.uncons.flatMap {
      case Some((chunk, rest)) =>
        val (ix, l, r, add) =
          splitOnChunkLimited[F](values, 0, chunk, Stream.empty, Stream.empty)
        go(rest, ix, l, r, add)
      case None =>
        Pull.raiseError[F](MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
    }
  }
}
