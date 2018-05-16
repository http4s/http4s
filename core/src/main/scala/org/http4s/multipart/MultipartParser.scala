package org.http4s
package multipart

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import scala.annotation.tailrec
import scodec.bits.ByteVector
import multipart.file._

import java.nio.file._

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {

  private[this] val logger = org.log4s.getLogger

  private val CRLFBytes = ByteVector('\r', '\n')
  private val DashDashBytes = ByteVector('-', '-')
  private val boundaryBytes: Boundary => ByteVector = boundary =>
    ByteVector(boundary.value.getBytes)
  private val startLineBytes: Boundary => ByteVector = boundaryBytes.andThen(DashDashBytes ++ _)
  private val endLineBytes: Boundary => ByteVector = startLineBytes.andThen(_ ++ DashDashBytes)
  private val expectedBytes: Boundary => ByteVector = startLineBytes.andThen(CRLFBytes ++ _)

  final case class Out[+A](a: A, tail: Option[ByteVector] = None)

  def parse[F[_]: Sync](boundary: Boundary): Pipe[F, Byte, Either[Headers, ByteVector]] = s => {
    val bufferedMultipartT = s.compile.toVector.map(ByteVector(_))
    val parts = bufferedMultipartT.flatMap(parseToParts(_)(boundary))
    val listT = parts.map(splitParts(_)(boundary)(List.empty[Either[Headers, ByteVector]]))

    Stream
      .eval(listT)
      .flatMap(list => Stream.emits(list))
  }

  /**
    * parseToParts - Removes Prelude and Trailer
    *
    * splitParts - Splits Into Parts
    * splitPart - Takes a Single Part of the Front
    * generatePart - Generates a tuple of Headers and a ByteVector of the Body, effectively a Part
    *
    * generateHeaders - Generate Headers from ByteVector
    * splitHeader - Splits a Header into the Name and Value
    */
  def parseToParts[F[_]](byteVector: ByteVector)(boundary: Boundary)(
      implicit F: Sync[F]): F[ByteVector] = {
    val startLine = startLineBytes(boundary)
    val startIndex = byteVector.indexOfSlice(startLine)
    val endLine = endLineBytes(boundary)
    val endIndex = byteVector.indexOfSlice(endLine)

    if (startIndex >= 0 && endIndex >= 0) {
      val parts = byteVector.slice(
        startIndex + startLine.length + CRLFBytes.length,
        endIndex - CRLFBytes.length)
      F.delay(parts)
    } else {
      F.raiseError(MalformedMessageBodyFailure("Expected a multipart start or end line"))
    }
  }

  def splitPart(byteVector: ByteVector)(boundary: Boundary): Option[(ByteVector, ByteVector)] = {
    val expected = expectedBytes(boundary)
    val index = byteVector.indexOfSlice(expected)

    if (index >= 0L) {
      val (part, restWithExpected) = byteVector.splitAt(index)
      val rest = restWithExpected.drop(expected.length)
      Option((part, rest))
    } else {
      Option((byteVector, ByteVector.empty))
    }
  }
  @tailrec
  def splitParts(byteVector: ByteVector)(boundary: Boundary)(
      acc: List[Either[Headers, ByteVector]]): List[Either[Headers, ByteVector]] = {

    val expected = expectedBytes(boundary)
    val containsExpected = byteVector.containsSlice(expected)

    val partOpt = if (!containsExpected) {

      Option((byteVector, ByteVector.empty))
    } else {
      splitPart(byteVector)(boundary)
    }

    partOpt match {
      case Some((part, rest)) =>
        logger.trace(s"splitParts part: ${part.decodeUtf8.toOption}")

        val (headers, body) = generatePart(part)

        val newAcc = Either.right(body) :: Either.left(headers) :: acc
        logger.trace(s"splitParts newAcc: $newAcc")

        if (rest.isEmpty) {
          newAcc.reverse
        } else {
          splitParts(rest)(boundary)(newAcc)
        }
      case None => acc.reverse
    }
  }

  def generatePart(byteVector: ByteVector): (Headers, ByteVector) = {
    val doubleCRLF = CRLFBytes ++ CRLFBytes
    val index = byteVector.indexOfSlice(doubleCRLF)
    // Each Part Should Contain this Separation between bodies and headers -- We could handle failure.
    val (headersSplit, bodyWithCRLFs) = byteVector.splitAt(index)
    val body = bodyWithCRLFs.drop(doubleCRLF.length)

    logger.trace(s"GeneratePart HeadersSplit ${headersSplit.decodeAscii}")
    logger.trace(s"GenerateParts Body ${body.decodeAscii}")

    val headers = generateHeaders(headersSplit ++ CRLFBytes)(Headers.empty)

    (headers, body)
  }

  @tailrec
  def generateHeaders(byteVector: ByteVector)(acc: Headers): Headers = {
    val headerO = splitHeader(byteVector)

    headerO match {
      case Some((lineBV, rest)) =>
        val headerO = for {
          line <- lineBV.decodeAscii.right.toOption
          idx <- Some(line.indexOf(':'))
          if idx >= 0
          header = Header(line.substring(0, idx), line.substring(idx + 1).trim)
        } yield header

        val newHeaders = acc ++ headerO

        logger.trace(s"Generate Headers Header0 = $headerO")
        generateHeaders(rest)(newHeaders)
      case None => acc
    }

  }

  def splitHeader(byteVector: ByteVector): Option[(ByteVector, ByteVector)] = {
    val index = byteVector.indexOfSlice(CRLFBytes)

    if (index >= 0L) {
      val (line, rest) = byteVector.splitAt(index)

      logger.trace(s"Split Header Line: ${line.decodeAscii}")
      logger.trace(s"Split Header Rest: ${rest.decodeAscii}")
      Option((line, rest.drop(CRLFBytes.length)))
    } else {
      Option.empty[(ByteVector, ByteVector)]
    }
  }

  private[this] val CRLFBytesN = Array[Byte]('\r', '\n')
  private[this] val DoubleCRLFBytesN = Array[Byte]('\r', '\n', '\r', '\n')
  private[this] val DashDashBytesN = Array[Byte]('-', '-')
  private[this] val BoundaryBytesN: Boundary => Array[Byte] = boundary =>
    boundary.value.getBytes("UTF-8")
  val StartLineBytesN: Boundary => Array[Byte] = BoundaryBytesN.andThen(DashDashBytesN ++ _)

  private[this] val ExpectedBytesN: Boundary => Array[Byte] =
    BoundaryBytesN.andThen(CRLFBytesN ++ DashDashBytesN ++ _)
  private[this] val dashByte: Byte = '-'.toByte
  private[this] val streamEmpty = Stream.empty
  private[this] val PullUnit = Pull.pure[Pure, Unit](())

  private type SplitStream[F[_]] = Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])]
  private type SplitFileStream[F[_]] =
    Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte], Option[Path])]

  def parseStreamed[F[_]: Sync](
      boundary: Boundary,
      limit: Int = 1024): Pipe[F, Byte, Multipart[F]] = { st =>
    ignorePrelude[F](boundary, st, limit)
      .fold(Vector.empty[Part[F]])(_ :+ _)
      .map(Multipart(_, boundary))
  }

  def parseToPartsStream[F[_]: Sync](
      boundary: Boundary,
      limit: Int = 1024): Pipe[F, Byte, Part[F]] = { st =>
    ignorePrelude[F](boundary, st, limit)
  }

  private def splitAndIgnorePrev[F[_]](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte]): (Int, Stream[F, Byte]) = {
    var i = 0
    var currState = state
    val len = values.length
    while (currState < len && i < c.size) {
      if (c(i) == values(currState)) {
        currState += 1
      } else if (c(i) == values(0)) {
        currState = 1
      } else {
        currState = 0
      }
      i += 1
    }

    if (currState == 0) {
      (0, Stream.empty)
    } else if (currState == len) {
      (currState, Stream.chunk(c.drop(i)))
    } else {
      (currState, Stream.empty)
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
    *
    */
  private def splitCompleteMatch[F[_]: Sync](
      middleChunked: Boolean,
      sti: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte]
  ): (Int, Stream[F, Byte], Stream[F, Byte]) =
    if (middleChunked) {
      (
        sti,
        //Emit the partial match as well
        acc ++ carry ++ Stream.chunk(c.take(i - sti)),
        Stream.chunk(c.drop(i))) //Emit after the match
    } else {
      (
        sti,
        acc, //block completes partial match, so do not emit carry
        Stream.chunk(c.drop(i))) //Emit everything after the match
    }

  /** Split a chunk in the case of a partial match:
    *
    * DO NOT USE. Was made private[http4s] because
    * Jose messed up hard like 5 patches ago and now it breaks bincompat to
    * remove.
    *
    */
  private[http4s] def splitPartialMatch[F[_]: Sync](
      state: Int,
      middleChunked: Boolean,
      currState: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte]
  ): (Int, Stream[F, Byte], Stream[F, Byte]) = {
    val ixx = i - currState
    if (middleChunked || state == 0) {
      val (lchunk, rchunk) = c.splitAt(ixx)
      (currState, acc ++ carry ++ Stream.chunk(lchunk), Stream.chunk(rchunk))
    } else {
      (currState, acc, carry ++ Stream.chunk(c))
    }
  }

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
    *
    */
  private def splitPartialMatch0[F[_]: Sync](
      middleChunked: Boolean,
      currState: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte]
  ): (Int, Stream[F, Byte], Stream[F, Byte]) = {
    val ixx = i - currState
    if (middleChunked) {
      val (lchunk, rchunk) = c.splitAt(ixx)
      (currState, acc ++ carry ++ Stream.chunk(lchunk), Stream.chunk(rchunk))
    } else {
      (currState, acc, carry ++ Stream.chunk(c))
    }
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
    *
    */
  private[http4s] def splitOnChunk[F[_]: Sync](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte],
      acc: Stream[F, Byte],
      carry: Stream[F, Byte]): (Int, Stream[F, Byte], Stream[F, Byte]) = {
    var i = 0
    var currState = state
    val len = values.length
    while (currState < len && i < c.size) {
      if (c(i) == values(currState)) {
        currState += 1
      } else if (c(i) == values(0)) {
        currState = 1
      } else {
        currState = 0
      }
      i += 1
    }
    //It will only be zero if
    //the chunk matches from the very beginning,
    //since currstate can never be greater than
    //(i + state).
    val middleChunked = i + state - currState > 0

    if (currState == 0) {
      (0, acc ++ carry ++ Stream.chunk(c), Stream.empty)
    } else if (currState == len) {
      splitCompleteMatch(middleChunked, currState, i, acc, carry, c)
    } else {
      splitPartialMatch0(middleChunked, currState, i, acc, carry, c)
    }
  }

  /** The first part of our streaming stages:
    *
    * Ignore the prelude and remove the first boundary. Only traverses until the first
    * part
    */
  private[this] def ignorePrelude[F[_]: Sync](
      b: Boundary,
      stream: Stream[F, Byte],
      limit: Int): Stream[F, Part[F]] = {
    val values = StartLineBytesN(b)

    def go(s: Stream[F, Byte], state: Int, strim: Stream[F, Byte]): Pull[F, Part[F], Unit] =
      if (state == values.length) {
        pullParts[F](b, strim ++ s, limit)
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, rest)) =>
            val bytes = chnk
            val (ix, strim) = splitAndIgnorePrev(values, state, bytes)
            go(rest, ix, strim)
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Malformed Malformed match"))
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chnk, strim)) =>
        val (ix, rest) = splitAndIgnorePrev(values, 0, chnk)
        go(strim, ix, rest)
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Cannot parse empty stream"))
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
  private def pullParts[F[_]: Sync](
      boundary: Boundary,
      s: Stream[F, Byte],
      limit: Int
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
          Pull.raiseError(MalformedMessageBodyFailure("Cannot parse empty stream"))
        } else {
          tailrecParts[F](boundary, l, r, expectedBytes, limit)
        }
    }
  }

  private def tailrecParts[F[_]: Sync](
      b: Boundary,
      headerStream: Stream[F, Byte],
      rest: Stream[F, Byte],
      expectedBytes: Array[Byte],
      limit: Int): Pull[F, Part[F], Unit] =
    Pull
      .eval(parseHeaders(headerStream))
      .flatMap { hdrs =>
        splitHalf(expectedBytes, rest).flatMap {
          case (l, r) =>
            //We hit a boundary, but the rest of the stream is empty
            //and thus it's not a properly capped multipart body
            if (r == streamEmpty) {
              Pull.raiseError(MalformedMessageBodyFailure("Part not terminated properly"))
            } else {
              Pull.output1(Part[F](hdrs, l)) >> splitOrFinish(DoubleCRLFBytesN, r, limit).flatMap {
                case (hdrStream, remaining) =>
                  if (hdrStream == streamEmpty) { //Empty returned if it worked fine
                    Pull.done
                  } else {
                    tailrecParts[F](b, hdrStream, remaining, expectedBytes, limit)
                  }
              }
            }
        }
      }

  /** Split a stream in half based on `values`,
    * but check if it is either double dash terminated (end of multipart).
    * SplitOrFinish also tracks a header limit size
    *
    * If it is, return the empty stream. if it is not, split on the `values`
    * and raise an error if we lack a match
    */
  //noinspection ScalaStyle
  private def splitOrFinish[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte],
      limit: Int): SplitStream[F] = {

    //Check if a particular chunk a final chunk, that is,
    //whether it's the boundary plus an extra "--", indicating it's
    //the last boundary
    def checkIfLast(c: Chunk[Byte], rest: Stream[F, Byte]): SplitStream[F] =
      if (c.size <= 0) {
        Pull.raiseError(MalformedMessageBodyFailure("Invalid Chunk: Chunk is empty"))
      } else if (c.size == 1) {
        rest.pull.unconsChunk.flatMap {
          case Some((chnk, remaining)) =>
            if (chnk.size <= 0)
              Pull.raiseError(MalformedMessageBodyFailure("Invalid Chunk: Chunk is empty"))
            else if (c(0) == dashByte && chnk(0) == dashByte) {
              Pull.pure((streamEmpty, streamEmpty))
            } else {
              val (ix, l, r, add) =
                splitOnChunkLimited[F](
                  values,
                  0,
                  Chunk.bytes(c.toArray[Byte] ++ chnk.toArray[Byte]),
                  Stream.empty,
                  Stream.empty)
              go(remaining, ix, l, r, add)
            }
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Malformed Multipart ending"))
        }
      } else if (c(0) == dashByte && c(1) == dashByte) {
        Pull.pure((streamEmpty, streamEmpty))
      } else {
        val (ix, l, r, add) =
          splitOnChunkLimited[F](values, 0, c, Stream.empty, Stream.empty)
        go(rest, ix, l, r, add)
      }

    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte],
        limitCTR: Int): SplitStream[F] =
      if (limitCTR >= limit) {
        Pull.raiseError(
          MalformedMessageBodyFailure(s"Part header was longer than $limit-byte limit"))
      } else if (state == values.length) {
        Pull.pure((lacc, racc ++ s))
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r, add) = splitOnChunkLimited[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r, limitCTR + add)
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chunk, rest)) =>
        checkIfLast(chunk, rest)
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
    }
  }

  /** Take the stream of headers separated by
    * double CRLF bytes and return the headers
    */
  private def parseHeaders[F[_]: Sync](strim: Stream[F, Byte]): F[Headers] = {
    def tailrecParse(s: Stream[F, Byte], headers: Headers): Pull[F, Headers, Unit] =
      splitHalf[F](CRLFBytesN, s).flatMap {
        case (l, r) =>
          l.through(fs2.text.utf8Decode[F])
            .fold("")(_ ++ _)
            .map { string =>
              val ix = string.indexOf(':')
              if (string.indexOf(':') >= 0) {
                headers.put(Header(string.substring(0, ix), string.substring(ix + 1).trim))
              } else {
                headers
              }
            }
            .pull
            .echo >> r.pull.uncons.flatMap {
            case Some(_) =>
              tailrecParse(r, headers)
            case None =>
              Pull.done
          }
      }

    tailrecParse(strim, Headers.empty).stream.compile
      .fold(Headers.empty)(_ ++ _)
  }

  /** Spit our `Stream[F, Byte]` into two halves.
    * If we reach the end and the state is 0 (meaning we didn't match at all),
    * then we return the concatenated parts of the stream.
    *
    * This method _always_ caps
    */
  private def splitHalf[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte]): SplitStream[F] = {

    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte]): SplitStream[F] =
      if (state == values.length) {
        Pull.pure((lacc, racc ++ s))
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r) = splitOnChunk[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r)
          case None =>
            //We got to the end, and matched on nothing.
            Pull.pure((lacc ++ racc, streamEmpty))
        }
      }

    stream.pull.unconsChunk.flatMap {
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
    *
    */
  private def splitCompleteLimited[F[_]: Sync](
      state: Int,
      middleChunked: Boolean,
      sti: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte]
  ): (Int, Stream[F, Byte], Stream[F, Byte], Int) =
    if (middleChunked) {
      (
        sti,
        //Emit the partial match as well
        acc ++ carry ++ Stream.chunk(c.take(i - sti)),
        //Emit after the match
        Stream.chunk(c.drop(i)),
        state + i - sti)
    } else {
      (
        sti,
        acc, //block completes partial match, so do not emit carry
        Stream.chunk(c.drop(i)), //Emit everything after the match
        0)
    }

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
    *
    */
  private[http4s] def splitPartialLimited[F[_]: Sync](
      state: Int,
      middleChunked: Boolean,
      currState: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte]
  ): (Int, Stream[F, Byte], Stream[F, Byte], Int) = {
    val ixx = i - currState
    if (middleChunked) {
      val (lchunk, rchunk) = c.splitAt(ixx)
      (
        currState,
        acc ++ carry ++ Stream.chunk(lchunk), //Emit previous carry
        Stream.chunk(rchunk),
        state + ixx)
    } else {
      //Whole thing is partial match
      (currState, acc, carry ++ Stream.chunk(c), 0)
    }
  }

  private[http4s] def splitOnChunkLimited[F[_]: Sync](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte],
      acc: Stream[F, Byte],
      carry: Stream[F, Byte]): (Int, Stream[F, Byte], Stream[F, Byte], Int) = {
    var i = 0
    var currState = state
    val len = values.length
    while (currState < len && i < c.size) {
      if (c(i) == values(currState)) {
        currState += 1
      } else if (c(i) == values(0)) {
        currState = 1
      } else {
        currState = 0
      }
      i += 1
    }

    //It will only be zero if
    //the chunk matches from the very beginning,
    //since currstate can never be greater than
    //(i + state).
    val middleChunked = i + state - currState > 0

    if (currState == 0) {
      (0, acc ++ carry ++ Stream.chunk(c), Stream.empty, i)
    } else if (currState == len) {
      splitCompleteLimited(state, middleChunked, currState, i, acc, carry, c)
    } else {
      splitPartialLimited(state, middleChunked, currState, i, acc, carry, c)
    }
  }

  ////////////////////////////////////////////////////////////
  // File writing encoder
  ///////////////////////////////////////////////////////////

  /** Same as the other streamed parsing, except
    * after a particular size, it buffers on a File.
    */
  def parseStreamedFile[F[_]: Sync](
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false): Pipe[F, Byte, MixedMultipart[F]] = { st =>
    ignorePreludeFileStream[F](boundary, st, limit, maxSizeBeforeWrite, maxParts, failOnLimit)
      .fold(Vector.empty[MixedPart[F]])(_ :+ _)
      .map(MixedMultipart(_, boundary))
  }

  def parseToPartsStreamedFile[F[_]: Sync](
      boundary: Boundary,
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 20,
      failOnLimit: Boolean = false): Pipe[F, Byte, MixedPart[F]] = { st =>
    ignorePreludeFileStream[F](boundary, st, limit, maxSizeBeforeWrite, maxParts, failOnLimit)
  }

  /** The first part of our streaming stages:
    *
    * Ignore the prelude and remove the first boundary. Only traverses until the first
    * part
    */
  private[this] def ignorePreludeFileStream[F[_]: Sync](
      b: Boundary,
      stream: Stream[F, Byte],
      limit: Int,
      maxSizeBeforeWrite: Int,
      maxParts: Int,
      failOnLimit: Boolean): Stream[F, MixedPart[F]] = {
    val values = StartLineBytesN(b)

    def go(s: Stream[F, Byte], state: Int, strim: Stream[F, Byte]): Pull[F, MixedPart[F], Unit] =
      if (state == values.length) {
        pullPartsFileStream[F](b, strim ++ s, limit, maxSizeBeforeWrite, maxParts, failOnLimit)
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, rest)) =>
            val bytes = chnk
            val (ix, strim) = splitAndIgnorePrev(values, state, bytes)
            go(rest, ix, strim)
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Malformed Malformed match"))
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chnk, strim)) =>
        val (ix, rest) = splitAndIgnorePrev(values, 0, chnk)
        go(strim, ix, rest)
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Cannot parse empty stream"))
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
  private def pullPartsFileStream[F[_]: Sync](
      boundary: Boundary,
      s: Stream[F, Byte],
      limit: Int,
      maxBeforeWrite: Int,
      maxParts: Int,
      failOnLimit: Boolean
  ): Pull[F, MixedPart[F], Unit] = {
    val values = DoubleCRLFBytesN
    val expectedBytes = ExpectedBytesN(boundary)

    splitOrFinish[F](values, s, limit).flatMap {
      case (l, r) =>
        //We can abuse reference equality here for efficiency
        //Since `splitOrFinish` returns `empty` on a capped stream
        //However, we must have at least one part, so `splitOrFinish` on this function
        //Indicates an error
        if (r == streamEmpty) {
          Pull.raiseError(MalformedMessageBodyFailure("Cannot parse empty stream"))
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
            failOnLimit
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
        F.raiseError(err)
      }

  private[this] def tailrecPartsFileStream[F[_]: Sync](
      b: Boundary,
      headerStream: Stream[F, Byte],
      rest: Stream[F, Byte],
      expectedBytes: Array[Byte],
      headerLimit: Int,
      maxBeforeWrite: Int,
      partsCounter: Int,
      partsLimit: Int,
      failOnLimit: Boolean): Pull[F, MixedPart[F], Unit] =
    Pull
      .eval(parseHeaders(headerStream))
      .flatMap { hdrs =>
        splitWithFileStream(expectedBytes, rest, maxBeforeWrite).flatMap {
          case (partBody, rest, fileRef) =>
            //We hit a boundary, but the rest of the stream is empty
            //and thus it's not a properly capped multipart body
            if (rest == streamEmpty) {
              cleanupFileOption[F](fileRef) >> Pull.raiseError(
                MalformedMessageBodyFailure("Part not terminated properly"))
            } else {
              Pull.output1(makeMixedPart(hdrs, partBody, fileRef)) >> splitOrFinish(
                DoubleCRLFBytesN,
                rest,
                headerLimit)
                .flatMap {
                  case (hdrStream, remaining) =>
                    if (hdrStream == streamEmpty) { //Empty returned if it worked fine
                      Pull.done
                    } else if (partsCounter >= partsLimit) {
                      if (failOnLimit) {
                        Pull.raiseError(MalformedMessageBodyFailure("Parts limit exceeded"))
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
                        failOnLimit)
                        .handleErrorWith(e => cleanupFileOption(fileRef) >> Pull.raiseError(e))
                    }
                }
            }
        }
      }

  private[this] def makeMixedPart[F[_]](
      hdrs: Headers,
      body: Stream[F, Byte],
      path: Option[Path]): MixedPart[F] = path match {
    case Some(p) => FilePart(hdrs, body, p)
    case None => BasicPart(hdrs, body)
  }

  /** Split the stream on `values`, but when
    */
  //noinspection ScalaStyle
  private def splitWithFileStream[F[_]](
      values: Array[Byte],
      stream: Stream[F, Byte],
      maxBeforeWrite: Int)(implicit F: Sync[F]): SplitFileStream[F] = {

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
            .through(io.file.writeAll[F](fileRef, List(StandardOpenOption.APPEND)))
            .compile
            .drain) >> Pull.pure(
          (io.file.readAll[F](fileRef, maxBeforeWrite), racc ++ s, Some(fileRef)))
      } else if (limitCTR >= maxBeforeWrite) {
        Pull.eval(
          lacc
            .through(io.file.writeAll[F](fileRef, List(StandardOpenOption.APPEND)))
            .compile
            .drain) >> streamAndWrite(s, state, Stream.empty, racc, 0, fileRef)
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r, add) = splitOnChunkLimited[F](values, state, chnk, lacc, racc)
            streamAndWrite(str, ix, l, r, limitCTR + add, fileRef)
          case None =>
            Pull.eval(F.delay(Files.delete(fileRef)).attempt) >> Pull.raiseError(
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
              _ <- Pull.eval(lacc.through(io.file.writeAll[F](path)).compile.drain)
              split <- streamAndWrite(s, state, Stream.empty, racc, 0, path)
            } yield split)
              .handleErrorWith(e => Pull.eval(cleanupFile(path)) >> Pull.raiseError(e))
          }
      } else if (state == values.length) {
        Pull.pure((lacc, racc ++ s, None))
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r, add) = splitOnChunkLimited[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r, limitCTR + add)
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chunk, rest)) =>
        val (ix, l, r, add) =
          splitOnChunkLimited[F](values, 0, chunk, Stream.empty, Stream.empty)
        go(rest, ix, l, r, add)
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
    }
  }

}
