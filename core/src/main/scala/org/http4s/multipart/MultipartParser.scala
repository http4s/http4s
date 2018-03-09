package org.http4s
package multipart

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import scala.annotation.tailrec
import scodec.bits.ByteVector

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {

  private[this] val logger = org.log4s.getLogger

  /** New whey **/
  private val CRLFBytesR = Array[Byte]('\r', '\n')
  private val DoubleCRLFBytesR = Array[Byte]('\r', '\n', '\r', '\n')
  private val DashDashBytesR = Array[Byte]('-', '-')
  private val boundaryBytesR: Boundary => Array[Byte] = boundary => boundary.value.getBytes("UTF-8")
  private val startLineBytesR: Boundary => Array[Byte] = boundaryBytesR
    .andThen { r =>
      val newArr = new Array[Byte](r.length + DashDashBytesR.length)

      /** Disgusting but FAST BOII **/
      System.arraycopy(DashDashBytesR, 0, newArr, 0, DashDashBytesR.length)
      System.arraycopy(r, 0, newArr, DashDashBytesR.length, r.length)
      newArr
    }

  private val endLineBytesR: Boundary => Array[Byte] = boundaryBytesR.andThen { r =>
    val newArr = new Array[Byte](r.length + DashDashBytesR.length * 2)

    /** Disgusting but FAST BOII **/
    System.arraycopy(DashDashBytesR, 0, newArr, 0, DashDashBytesR.length)
    System.arraycopy(r, 0, newArr, DashDashBytesR.length, r.length)
    System.arraycopy(
      DashDashBytesR,
      0,
      newArr,
      DashDashBytesR.length + r.length,
      DashDashBytesR.length)
    newArr
  }
//  private val endLineBytesR: Boundary => Array[Byte] = startLineBytesR.andThen(_ ++ DashDashBytes)
  private val expectedBytesR: Boundary => Array[Byte] = boundaryBytesR.andThen { r =>
    val newArr = new Array[Byte](r.length + DashDashBytesR.length + CRLFBytesR.length)

    /** Disgusting but FAST BOII **/
    System.arraycopy(CRLFBytesR, 0, newArr, 0, CRLFBytesR.length)
    System.arraycopy(DashDashBytesR, 0, newArr, CRLFBytesR.length, DashDashBytesR.length)
    System.arraycopy(r, 0, newArr, DashDashBytesR.length + CRLFBytesR.length, r.length)
    newArr
  }

  /** Ol' way **/
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

  def parseR[F[_]: Sync](boundary: Boundary): Pipe[F, Byte, Multipart[F]] = { st =>
    val expectedStart = startLineBytesR(boundary)
    val expectedEnd = endLineBytesR(boundary)
    val expectedMiddles = expectedBytesR(boundary)
    findAndSplit[F](expectedMiddles, ignoreAfter[F](expectedEnd, ignorePrior(expectedStart, st)))
      .flatMap(parseToPartsR[F])
      .fold(Vector.empty[Part[F]])(_ :+ _)
      .map(Multipart(_))
  }

  def parseStreamed[F[_]: Sync](boundary: Boundary): Pipe[F, Byte, Stream[F, Byte]] = { st =>
    val expectedStart = startLineBytesR(boundary)
    val expectedEnd = endLineBytesR(boundary)
    val expectedMiddles = expectedBytesR(boundary)
    findAndSplit[F](expectedMiddles, ignoreAfter[F](expectedEnd, ignorePrior(expectedStart, st)))
  }

  /** Split an already truncated stream,
    * parse the headers from the first part, and set the rest as the
    * (potentially empty) body.
    *
    * This function assumes the stream `s` to have been already
    * split by the delimited portions
    */
  private def parseToPartsR[F[_]: Sync](s: Stream[F, Byte]): Stream[F, Part[F]] =
    findAndSplit[F](DoubleCRLFBytesR, s).pull.uncons1.flatMap {
      case Some((strim1, rest)) =>
        rest.pull.uncons1.flatMap {
          case Some((strim2, rest2)) =>
            rest2.pull.uncons1.flatMap {
              case Some(_) =>
                Pull.raiseError(MalformedMessageBodyFailure("Part split incorrectly"))
              case None =>
                parseHeadersR[F](strim1)
                  .map(Part[F](_, strim2))
                  .pull
                  .echo
            }
          case None =>
            parseHeadersR[F](strim1)
              .map(Part[F](_, Stream.empty))
              .pull
              .echo
        }
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Malformed Boundary"))
    }.stream

  /** Parse headers from CRLF separated
    * stream components.
    */
  def parseHeadersR[F[_]: Sync](s: Stream[F, Byte]): Stream[F, Headers] = {
    def splitH(s1: Stream[F, Byte]): Stream[F, Headers] =
      s1.through(text.utf8Decode).map { line =>
        val idx = line.indexOf(':')
        if (idx >= 0) {
          Headers(List(Header(line.substring(0, idx), line.substring(idx + 1).trim)))
        } else {
          Headers.empty
        }
      }

    findAndSplit[F](CRLFBytesR, s).flatMap(splitH).fold(Headers.empty)(_ ++ _)
  }

  /** This function is the integral component in detecting whether `values`
    * is either entirely contained in the chunk, partially contained
    * in the chunk or not in the chunk at all
    *
    * @param values the value to look for in the chunk
    * @param valueIx the current index of the value relative to the chunk.
    *                This can mean the value is split across chunks.
    * @param c the offending chunk.
    * @return
    */
  private def stateIndex(values: Array[Byte], valueIx: Int, c: Chunk.Bytes): (Int, Int) = {
    var i = 0
    var sti = 0
    val len2 = values.length - valueIx
    while (sti < len2 && i < c.length) {
      if (c(i) != values(sti + valueIx) && sti != 0) {
        sti = 0
      } else {
        sti += 1
      }
      i += 1
    }
    (sti, i)
  }

  /** Apply our stateful indexing function, but only return the chunk
    * after the final index matching the value array
    */
  private def emitRest(values: Array[Byte], st: Int, c: Chunk.Bytes): (Int, Chunk.Bytes) = {
    val (sti, i) = stateIndex(values, st, c)
    val str = {
      if (sti == 0) {
        c
      } else if (i == c.length) {
        Chunk.Bytes(Array.empty[Byte])
      } else {
        Chunk.Bytes(c.values, c.offset + i, c.length - i)
      }
    }
    (sti + st, str)
  }

  /** Apply our stateful indexing function, but only return the chunk
    * before the final index matching the value array
    */
  private def emitPrev(values: Array[Byte], st: Int, c: Chunk.Bytes): (Int, Chunk.Bytes) = {
    val (sti, i) = stateIndex(values, st, c)
    val str = {
      if (sti == 0) {
        c
      } else {
        Chunk.Bytes(c.values, c.offset, i - sti)
      }
    }
    (sti + st, str)
  }

  /** Like `emitPrev` and `emitRest`,
    * except it returns everything except the matched
    * `values`.
    */
  def emitBoth[F[_]: Sync](
      values: Array[Byte],
      state: Int,
      c: Chunk.Bytes): (Int, Segment[Byte, Unit], Vector[Stream[F, Byte]]) = {
    @tailrec def segmentRecurse(
        st: Int,
        c: Chunk.Bytes,
        acc: Segment[Byte, Unit],
        out: Vector[Stream[F, Byte]]): (Int, Segment[Byte, Unit], Vector[Stream[F, Byte]]) = {
      val (sti, i) = stateIndex(values, state, c)
      if (sti == 0) {
        (sti, acc ++ Segment.chunk(c), out)
      } else if (sti + state == values.length) {
        segmentRecurse(
          0,
          Chunk.Bytes(c.values, c.offset + i, c.length - i),
          Segment.empty[Byte],
          out :+ Stream
            .segment(acc ++ Segment.chunk(Chunk.Bytes(c.values, c.offset, i - sti)))
            .covary[F]
        )
      } else {
        segmentRecurse(
          sti + st,
          Chunk.Bytes(c.values, c.offset + i, c.length - i),
          acc ++ Segment.chunk(Chunk.Bytes(c.values, c.offset, i - sti)),
          out
        )
      }
    }

    segmentRecurse(state, c, Segment.empty[Byte], Vector.empty)
  }

  /** Traverse the stream until the first match of `values`,
    * emit everything before the match
    * then discard everything after it.
    *
    * @param values the values that may be spread across boundaries
    * @param stream the stream to split on
    * @return
    */
  private def ignoreAfter[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte]): Stream[F, Byte] = {

    def go(s: Stream[F, Byte], state: Int, lastChunk: Chunk[Byte]): Pull[F, Byte, Unit] =
      if (state == values.length) {
        Pull.done
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val bytes = chnk.toBytes
            val (ix, strim) = emitPrev(values, state, bytes)
            Pull.outputChunk[F, Byte](lastChunk) *> go(str, ix, strim)
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Malformed Boundary"))
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chnk, strim)) =>
        val (ix, rest) = emitPrev(values, 0, chnk.toBytes)
        Pull.outputChunk(rest).flatMap(_ => go(strim, ix, rest))
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Malformed Boundary"))
    }.stream
  }

  /** Traverse the stream until the first match of `values`,
    * discard everything prior to the match, emit everything after.
    *
    * @param values the values that may be spread across boundaries
    * @param stream the stream to split on
    * @return
    */
  private def ignorePrior[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte]): Stream[F, Byte] = {

    def go(s: Stream[F, Byte], state: Int, lastChnk: Chunk[Byte]): Pull[F, Byte, Unit] =
      if (state == values.length) {
        Pull.outputChunk(lastChnk).flatMap(_ => s.pull.echo)
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, rest)) =>
            val bytes = chnk.toBytes
            //Ignore the unmatched portion
            val (ix, chnkN) = emitRest(values, state, bytes)
            go(rest, ix, chnkN)
          case None =>
            Pull.raiseError(MalformedMessageBodyFailure("Malformed Malformed match"))
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chnk, strim)) =>
        val (ix, chnk2) = emitRest(values, 0, chnk.toBytes)
        go(strim, ix, chnk2)
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Malformed Message Body"))
    }.stream
  }

  /** Traverse the stream, splitting it up into sub-streams
    * based on `value`
    *
    * @param values the values that may be spread across boundaries
    * @param stream the stream to split on
    * @return
    */
  def findAndSplit[F[_]: Sync](
      values: Array[Byte],
      stream: Stream[F, Byte]): Stream[F, Stream[F, Byte]] = {

    def go(s: Stream[F, Byte], state: Int, accum: Stream[F, Byte]): Pull[F, Stream[F, Byte], Unit] =
      if (state == values.length) {
        Pull.output1(accum).flatMap(_ => go(s, 0, Stream.empty.covary[F]))
      } else {
        s.pull.unconsChunk.flatMap {
          case Some((chnk, str)) =>
            val bytes = chnk.toBytes
            val (ix, seg, strim) = emitBoth(values, state, bytes)
            if (strim.isEmpty)
              go(str, ix, accum ++ Stream.segment(seg))
            else
              strim.tail
                .foldLeft(Pull.output1(strim.head))((l, r) => l.flatMap(_ => Pull.output1(r)))
                .flatMap(_ => go(str, ix, accum ++ Stream.segment(seg)))
          case None =>
            if (state != 0) {
              Pull.raiseError(MalformedMessageBodyFailure("Malformed Message Body"))
            } else {
              Pull.output1(accum)
            }
        }
      }

    stream.pull.unconsChunk.flatMap {
      case Some((chnk, str)) =>
        val (ix, seq, strim) = emitBoth(values, 0, chnk.toBytes)
        if (strim.isEmpty)
          go(str, ix, Stream.segment(seq))
        else
          strim.tail
            .foldLeft(Pull.output1(strim.head))((l, r) => l.flatMap(_ => Pull.output1(r)))
            .flatMap(_ => go(str, ix, Stream.segment(seq)))
      case None =>
        Pull.raiseError(MalformedMessageBodyFailure("Splitting on empty stream"))
    }.stream
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
}
