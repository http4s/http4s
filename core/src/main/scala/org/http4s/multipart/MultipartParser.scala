package org.http4s
package multipart

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import org.http4s.util._

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {

  private val CRLFBytes = Array[Byte]('\r', '\n')
  private val DoubleCRLFBytes = Array[Byte]('\r', '\n', '\r', '\n')
  private val DashDashBytes = Array[Byte]('-', '-')
  private val boundaryBytesR: Boundary => Array[Byte] = boundary => boundary.value.getBytes("UTF-8")
  val startLineBytesR: Boundary => Array[Byte] = boundaryBytesR.andThen(DashDashBytes ++ _)

  private val expectedBytesR: Boundary => Array[Byte] =
    boundaryBytesR.andThen(CRLFBytes ++ DashDashBytes ++ _)

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

  /** This function is the integral component in detecting whether `values`
    * is either entirely contained in the chunk, partially contained
    * in the chunk or not in the chunk at all
    *
    * @param values the value to look for in the chunk
    * @param valueIx the current index of the value relative to the chunk.
    *                This can mean the value is split across chunks.
    * @param c the offending chunk.
    */
  private def stateIndex(values: Array[Byte], valueIx: Int, c: Chunk[Byte]): (Int, Int) = {
    var i = 0
    var sti = 0
    val len2 = values.length - valueIx
    while (sti < len2 && i < c.size) {
      if (c(i) == values(sti + valueIx)) {
        sti += 1
      } else if (c(i) == values(0)) {
        sti = 1
      } else {
        sti = 0
      }
      i += 1
    }
    (sti, i)
  }

  //Todo: This may fail for an edge case
  private def splitAndIgnorePrev[F[_]](
      values: Array[Byte],
      state: Int,
      c: Chunk[Byte]): (Int, Stream[F, Byte]) = {
    val (sti, i) = stateIndex(values, state, c)
    if (sti == 0) {
      (0, Stream.empty)
    } else if (i == c.size) {
      (sti + state, Stream.empty)
    } else {
      (sti + state, Stream.chunk(c.drop(i)))
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
    //Debug
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
    val values = startLineBytesR(b)

    def go(s: Stream[F, Byte], state: Int, strim: Stream[F, Byte]): Pull[F, Part[F], Unit] =
      if (state == values.length) {
        streamStageParsePart(b, strim ++ s, limit).pull.echo
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
    splitLimited[F](DoubleCRLFBytes, s, limit).flatMap {
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
      splitHalf[F](CRLFBytes, s).flatMap {
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

  private def streamStageParsePart[F[_]: Sync](
      boundary: Boundary,
      s: Stream[F, Byte],
      limit: Int
  ): Stream[F, Part[F]] = {
    val values = expectedBytesR(boundary)
    splitHalf[F](values, s).flatMap {
      case (l, r) =>
        r.pull.unconsChunk.flatMap {
          case None =>
            parseToPartStreamed[F](l, limit).pull.echo *>
              checkLast[F](r).pull.echo *>
              Pull.done
          case Some(_) =>
            tailrecParts[F](boundary, values, l, r, limit)
        }
    }.stream
  }

  private def checkLast[F[_]: Sync](s: Stream[F, Byte]): Stream[F, Part[F]] =
    s.take(2).fold("")(_ + _.toChar).flatMap { s =>
      if (s == "--") {
        Stream.empty
      } else {
        Stream.raiseError(MalformedMessageBodyFailure("Invalid closing boundary"))
      }
    }

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
            checkLast[F](l).pull.echo
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
