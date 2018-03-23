package org.http4s
package multipart

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import scala.annotation.tailrec
import scodec.bits.ByteVector
import org.http4s.util._

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

  private val CRLFBytesN = Array[Byte]('\r', '\n')
  private val DoubleCRLFBytesN = Array[Byte]('\r', '\n', '\r', '\n')
  private val DashDashBytesN = Array[Byte]('-', '-')
  private val BoundaryBytesN: Boundary => Array[Byte] = boundary => boundary.value.getBytes("UTF-8")
  val StartLineBytesN: Boundary => Array[Byte] = BoundaryBytesN.andThen(DashDashBytesN ++ _)

  private val ExpectedBytesN: Boundary => Array[Byte] =
    BoundaryBytesN.andThen(CRLFBytesN ++ DashDashBytesN ++ _)

  private val EndlineBytesN: Boundary => Array[Byte] =
    BoundaryBytesN.andThen(CRLFBytesN ++ DashDashBytesN ++ _ ++ DashDashBytesN)

  def parseStreamed[F[_]: Sync](
      boundary: Boundary,
      limit: Int = 1024): Pipe[F, Byte, Multipart[F]] = { st =>
    ignorePreludeStage[F](boundary, st, limit)
      .fold(Vector.empty[Part[F]])(_ :+ _)
      .map(Multipart(_, boundary))
  }

  def parseToPartsStream[F[_]: Sync](
      boundary: Boundary,
      limit: Int = 1024): Pipe[F, Byte, Part[F]] = { st =>
    ignorePreludeStage[F](boundary, st, limit)
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
      state: Int,
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
    } else if (state == 0) {
      (
        sti,
        //Ignore the partial match (carry)
        acc ++ Stream.chunk(c.take(i - sti)),
        Stream.chunk(c.drop(i)))
    } else {
      (
        sti,
        acc, //block completes partial match, so do not emit carry
        Stream.chunk(c.drop(i))) //Emit everything after the match
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
  def splitPartialMatch[F[_]: Sync](
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
  def splitOnChunk[F[_]: Sync](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte],
      acc: Stream[F, Byte],
      carry: Stream[F, Byte]): (Int, Stream[F, Byte], Stream[F, Byte]) = {
    var i = 0
    var currState = state
    val len = values.length
    var middleChunked = false
    while (currState < len && i < c.size) {
      if (c(i) == values(currState)) {
        currState += 1
      } else if (c(i) == values(0)) {
        middleChunked = true
        currState = 1
      } else {
        currState = 0
      }
      i += 1
    }

    if (currState == 0) {
      (0, acc ++ carry ++ Stream.chunk(c), Stream.empty)
    } else if (currState == len) {
      splitCompleteMatch(state, middleChunked, currState, i, acc, carry, c)
    } else {
      splitPartialMatch(state, middleChunked, currState, i, acc, carry, c)
    }
  }

  /** The first part of our streaming stages:
    *
    * Ignore the prelude and remove the first boundary
    *
    */
  private def ignorePreludeStage[F[_]: Sync](
      b: Boundary,
      stream: Stream[F, Byte],
      limit: Int): Stream[F, Part[F]] = {
    val values = StartLineBytesN(b)

    def go(s: Stream[F, Byte], state: Int, strim: Stream[F, Byte]): Pull[F, Part[F], Unit] =
      if (state == values.length) {
        streamStageIgnoreRest(b, strim ++ s, limit).pull.echo
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

  private def parseToPartStreamed[F[_]: Sync](s: Stream[F, Byte], limit: Int): Stream[F, Part[F]] =
    splitLimited[F](DoubleCRLFBytesN, s, limit).flatMap {
      case (l, r) =>
        l.pull.uncons.flatMap {
          case None =>
            Pull.raiseError(
              MalformedMessageBodyFailure("Invalid Separation between headers and body"))
          case Some(_) =>
            parseHeaders[F](l).map(Part[F](_, r)).pull.echo *> Pull.done
        }
    }.stream

  private def parseHeaders[F[_]: Sync](strim: Stream[F, Byte]): Stream[F, Headers] = {
    def tailrecParse(s: Stream[F, Byte], headers: Headers): Pull[F, Headers, Unit] =
      splitHalf[F](CRLFBytesN, s).flatMap {
        case (l, r) =>
          l.through(asciiDecode)
            .fold("")(_ ++ _)
            .map { string =>
              val ix = string.indexOf(':')
              if (string.indexOf(':') >= 0)
                headers.put(Header(string.substring(0, ix), string.substring(ix + 1).trim))
              else
                headers
            }
            .pull
            .echo *> r.pull.uncons.flatMap {
            case Some(_) =>
              tailrecParse(r, headers)
            case None =>
              Pull.done
          }
      }

    tailrecParse(strim, Headers.empty).stream
      .fold(Headers.empty)(_ ++ _)
  }

  private def streamStageIgnoreRest[F[_]: Sync](
      boundary: Boundary,
      s: Stream[F, Byte],
      limit: Int
  ): Stream[F, Part[F]] = {
    val endlineBytes = EndlineBytesN(boundary)
    val values = ExpectedBytesN(boundary)
    splitOrFail[F](endlineBytes, s).flatMap {
      case (l, _) =>
        streamStageParsePart[F](boundary, values, l ++ Stream.chunk(Chunk.bytes(values)), limit).pull.echo
    }.stream
  }

  private def streamStageParsePart[F[_]: Sync](
      boundary: Boundary,
      values: Array[Byte],
      s: Stream[F, Byte],
      limit: Int
  ): Stream[F, Part[F]] =
    splitHalf[F](values, s).flatMap {
      case (l, r) =>
        r.pull.unconsChunk.flatMap {
          case None =>
            parseToPartStreamed[F](l, limit).pull.echo *>
              Pull.done
          case Some(_) =>
            tailrecParts[F](boundary, values, l, r, limit)
        }
    }.stream

  private def tailrecParts[F[_]: Sync](
      b: Boundary,
      values: Array[Byte],
      last: Stream[F, Byte],
      next: Stream[F, Byte],
      limit: Int
  ): Pull[F, Part[F], Unit] =
    parseToPartStreamed[F](last, limit).pull.echo *> splitHalf[F](values, next).flatMap {
      case (l, r) =>
        r.pull.uncons.flatMap {
          case None =>
            Pull.done
          case Some(_) =>
            tailrecParts[F](b, values, l, r, limit)
        }
    }

  private def splitHalf[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte]): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])] = {

    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte]): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])] =
      if (state == values.length) {
        Pull.pure((lacc, racc ++ s))
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r) = splitOnChunk[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r)
          case None =>
            if (state != 0) {
              Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
            } else {
              Pull.pure((lacc, racc))
            }
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chunk, rest)) =>
        val (ix, l, r) = splitOnChunk[F](values, 0, chunk, Stream.empty, Stream.empty)
        go(rest, ix, l, r)
      case None =>
        Pull.pure((Stream.empty, Stream.empty))
    }
  }

  private def splitOrFail[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte]): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])] = {

    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte]): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])] =
      if (state == values.length) {
        Pull.pure((lacc, racc ++ s))
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val (ix, l, r) = splitOnChunk[F](values, state, chnk, lacc, racc)
            go(str, ix, l, r)
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chunk, rest)) =>
        val (ix, l, r) = splitOnChunk[F](values, 0, chunk, Stream.empty, Stream.empty)
        go(rest, ix, l, r)
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
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
    } else if (state == 0) {
      (
        sti,
        //Ignore the partial match (carry)
        acc ++ Stream.chunk(c.take(i - sti)),
        Stream.chunk(c.drop(i)),
        i - sti)
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
  def splitPartialLimited[F[_]: Sync](
      state: Int,
      middleChunked: Boolean,
      currState: Int,
      i: Int,
      acc: Stream[F, Byte],
      carry: Stream[F, Byte],
      c: Chunk[Byte]
  ): (Int, Stream[F, Byte], Stream[F, Byte], Int) = {
    val ixx = i - currState
    if (middleChunked || state == 0) {
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

  def splitOnChunkLimited[F[_]: Sync](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte],
      acc: Stream[F, Byte],
      carry: Stream[F, Byte]): (Int, Stream[F, Byte], Stream[F, Byte], Int) = {
    var i = 0
    var currState = state
    val len = values.length
    var middleChunked = false
    while (currState < len && i < c.size) {
      if (c(i) == values(currState)) {
        currState += 1
      } else if (c(i) == values(0)) {
        middleChunked = true
        currState = 1
      } else {
        currState = 0
      }
      i += 1
    }

    if (currState == 0) {
      (0, acc ++ carry ++ Stream.chunk(c), Stream.empty, i)
    } else if (currState == len) {
      splitCompleteLimited(state, middleChunked, currState, i, acc, carry, c)
    } else {
      splitPartialLimited(state, middleChunked, currState, i, acc, carry, c)
    }
  }

  private def splitLimited[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte],
      limit: Int): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])] = {

    def go(
        s: Stream[F, Byte],
        state: Int,
        lacc: Stream[F, Byte],
        racc: Stream[F, Byte],
        limitCTR: Int): Pull[F, Nothing, (Stream[F, Byte], Stream[F, Byte])] =
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
            if (state != 0) {
              Pull.raiseError(MalformedMessageBodyFailure("Invalid boundary - partial boundary"))
            } else {
              Pull.pure((lacc, racc))
            }
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chunk, rest)) =>
        val (ix, l, r, add) =
          splitOnChunkLimited[F](values, 0, chunk, Stream.empty, Stream.empty)
        go(rest, ix, l, r, add)
      case None =>
        Pull.pure((Stream.empty, Stream.empty))
    }
  }

}
