package org.http4s
package multipart

import scala.language.postfixOps

import scodec.bits.ByteVector

import scalaz.{-\/, \/-, \/, State}
import scalaz.stream.{process1, Process, Process1, Writer1}

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {
  import Process._

  private[this] val logger = org.log4s.getLogger

  private val CRLF = ByteVector('\r', '\n')
  private val DASHDASH = ByteVector('-', '-')

  private final case class Out[+A](a: A, tail: Option[ByteVector] = None)

  def parse(boundary: Boundary): Writer1[Headers, ByteVector, ByteVector] =
    parse(boundary, 40 * 1024)

  def parse(boundary: Boundary, headerLimit: Long): Writer1[Headers, ByteVector, ByteVector] = {
    val boundaryBytes = boundary.toByteVector
    val startLine = DASHDASH ++ boundaryBytes
    val endLine = startLine ++ DASHDASH

    // At various points, we'll read up until we find this, to loop back into beginPart.
    val expected = CRLF ++ startLine

    def receiveLine(leading: Option[ByteVector]): Writer1[ByteVector, ByteVector, Out[ByteVector]] = {
      def handle(bv: ByteVector): Writer1[ByteVector, ByteVector, Out[ByteVector]] = {
        logger.trace(s"handle: ${bv.decodeUtf8}")
        val index = bv indexOfSlice CRLF

        if (index >= 0) {
          val (head, tailPre) = bv splitAt index
          val tail = tailPre drop 2       // remove the line feed characters
          val tailM = Some(tail) filter { !_.isEmpty }
          emit(\/-(Out(head, tailM)))
        }
        else if (bv.nonEmpty && bv.get(bv.length - 1) == '\r') {
          // The CRLF may have split across chunks.
          val (head, cr) = bv.splitAt(bv.length - 1)
          emit(-\/(head)) ++ receive1(next => handle(cr ++ next))
        }
        else {
          emit(-\/(bv)) ++ receiveLine(None)
        }
      }

      leading map handle getOrElse receive1(handle)
    }

    def takeUpTo(n: Long): Process1[ByteVector, ByteVector] =
      if (n > 0) receive1(bv => emit(bv) ++ takeUpTo(n - bv.size))
      else fail(MalformedMessageBodyFailure(s"Part header was longer than ${headerLimit}-byte limit"))

    def receiveCollapsedLine(leading: Option[ByteVector]): Process1[ByteVector, Out[ByteVector]] = {
      receiveLine(leading) pipe process1.fold(Out(ByteVector.empty)) {
        case (acc, -\/(partial)) => acc.copy(acc.a ++ partial)
        case (acc, \/-(Out(term, tail))) => Out(acc.a ++ term, tail)
      }
    }

    def start: Writer1[Headers, ByteVector, ByteVector] = {
      def preamble(leading: Option[ByteVector]): Process1[ByteVector, Option[ByteVector]] =
        body(leading.map(_.compact), expected).flatMap {
          case Out(chunk, None) =>
            logger.trace(s"preamble chunk: ${chunk.decodeUtf8}")
            halt
          case Out(chunk, tail) =>
            logger.trace(s"Last preamble chunk: $chunk.")
            logger.trace(s"Resuming with $tail.")
            emit(tail)
        }

      def beginPart(leading: Option[ByteVector]): Process1[ByteVector, Option[ByteVector]] = {
        def isStartLine(line: ByteVector): Boolean =
          line.startsWith(startLine) && isTransportPadding(line.drop(startLine.size))

        def isEndLine(line: ByteVector): Boolean =
          line.startsWith(endLine)

        def isTransportPadding(bv: ByteVector): Boolean =
          bv.toSeq.find(b => b != ' ' && b != '\t').isEmpty

        receiveCollapsedLine(leading) flatMap { case Out(line, tail) =>
          if (isStartLine(line)) {
            logger.debug("Found start line. Beginning new part.")
            emit(tail)
          }
          else if (isEndLine(line)) {
            logger.debug("Found end line. Discarding epilogue.")
            process1.skip.repeat
          }
          else fail(new MalformedMessageBodyFailure(s"Expected a multipart start or end line: ${line.decodeUtf8}"))
        }
      }

      def headers(leading: Option[ByteVector]): Process1[ByteVector, Out[Headers]] = {
        takeUpTo(headerLimit) pipe (for {
          tail <- beginPart(leading)
          headerPair <- header(tail, expected)
          Out(headers, tail2) = headerPair
          _ = logger.debug(s"Headers: $headers")
        } yield Out(headers, tail2))
      }

      def go(leading: Option[ByteVector]): Writer1[Headers, ByteVector, ByteVector] = {
        for {
          headerPair <- headers(leading)
          Out(headers, tail) = headerPair
          part <- emit(-\/(headers)) ++ body(tail.map(_.compact), expected).flatMap {
            case Out(chunk, None) =>
              logger.debug(s"Chunk: $chunk")
              emit(\/-(chunk))
            case Out(ByteVector.empty, tail) =>
              logger.trace(s"Resuming with $tail.")
              go(tail)
            case Out(chunk, tail) =>
              logger.debug(s"Last chunk: $chunk.")
              logger.trace(s"Resuming with $tail.")
              emit(\/-(chunk)) ++ go(tail)
          }
        } yield part
      }

      // The requirement that the encapsulation boundary begins with a CRLF
      // implies that the body of a multipart entity must itself begin with a
      // CRLF before the first encapsulation line - that is, if the "preamble"
      // area is not used, the entity headers must be followed by TWO CRLFs.
      // This is indeed how such entities should be composed.  A tolerant mail
      // reading program, however, may interpret a body of type multipart that
      // begins with an encapsulation line NOT initiated by a CRLF as also
      // being an encapsulation boundary, but a compliant mail sending program
      // must not generate such entities. -- https://tools.ietf.org/html/rfc1341
      //
      // If we supply our own to start the preamble via `Some(CRLF)`,
      // we are tolerant.  If it's in the body, it gets eaten as part
      // of the preamble.
      preamble(Some(CRLF)).flatMap(go)
    }

    def header(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, Out[Headers]] = {
      def go(leading: Option[ByteVector]): Writer1[Header, ByteVector, Option[ByteVector]] = {
        logger.trace(s"Header go: ${leading.map(_.decodeUtf8)}  ")
        receiveCollapsedLine(leading) flatMap {
          case Out(bv, tail) => {
            if (bv == expected) {
              emitO(tail)
            } else {
              (for {
                line <- bv.decodeAscii.right.toOption
                idx <- Some(line indexOf ':')
                if idx >= 0
                if idx < line.length - 1
                header = Header(line.substring(0, idx), line.substring(idx + 1).trim)
              } yield emitW(header) ++ go(tail)).getOrElse(emitO(tail))
            }
          }
        }
      }

      go(leading).map(x => {logger.trace("Got: "+x.toString); x}).fold(Out(List.empty[Header])) {
        case (acc, -\/(header)) => acc.copy(a = header :: acc.a)
        case (acc, \/-(tail)) => acc.copy(tail = tail)
      }.map { case Out(hs, tail) => Out(Headers(hs.reverse), tail) }
    }

    def body(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, Out[ByteVector]] = {
      val heads = (0L until expected.length).scanLeft(expected) { (acc, _) =>
        acc dropRight 1
      }

      def pre(bv: ByteVector): Process1[ByteVector, Out[ByteVector]] = {
        logger.trace(s"pre: $bv")
        val idx = bv indexOfSlice expected
        if (idx >= 0) {
          // if we find the terminator *within* the chunk, we need to just trip and be done
          // the +2 consumes the CRLF before the multipart boundary
          emit(Out(bv take idx, Some(bv drop (idx + 2))))
        } else {
          // find goes from front to back, and heads is in order from longest to shortest, thus we always greedily match
          heads find (bv endsWith) map { tail =>
            emit(Out(bv dropRight tail.length)) ++ mid(tail, expected drop tail.length)
          } getOrElse (emit(Out(bv)) ++ receive1(pre))
        }
      }

      /* We might be looking at a boundary, or we might not.  This is how we
       * decide that incrementally.
       *
       * @param found the part of the next boundary that we've matched so far
       * @param remainder the part of the next boundary we're looking for on the next read.
       */
      def mid(found: ByteVector, remainder: ByteVector): Process1[ByteVector, Out[ByteVector]] = {
        logger.trace(s"mid: $found $remainder")
        if (remainder.isEmpty) {
          // found should start with a CRLF, because mid is called with it.
          emit(Out(ByteVector.empty, Some(found.drop(2))))
        } else {
          receive1Or[ByteVector, Out[ByteVector]](
            fail(new MalformedMessageBodyFailure("Part was not terminated"))) { bv =>
            val (remFront, remBack) = remainder splitAt bv.length
            logger.trace(s"remFront = $remFront; remBack = $remBack; bv = $bv")
            if (bv startsWith remFront) {
              // If remBack is nonEmpty, then the progress toward our match
              // is represented by bv, and we'll loop back to read more to
              // look for remBack.
              //
              // If remBack is empty, then `found ++ bv` starts with the
              // next boundary, and we'll emit out everything we've read
              // on the next loop.
              mid(found ++ bv, remBack)
            }
            else {
              // ok, so this buffer frame-slipped, but we might have a different terminator within bv
              emit(Out(found)) ++ pre(bv)
            }
          }
        }
      }

      leading map pre getOrElse receive1(pre)
    }

    start
  }
}
