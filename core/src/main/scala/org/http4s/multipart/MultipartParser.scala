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
    parse(boundary, 10 * 1024)

  def parse(boundary: Boundary, headerLimit: Long): Writer1[Headers, ByteVector, ByteVector] = {
    val boundaryBytes = boundary.toByteVector
    val startLine = DASHDASH ++ boundaryBytes
    val endLine = startLine ++ DASHDASH

    // At various points, we'll read up until we find this, to loop back into beginPart.
    val expected = CRLF ++ startLine

    def receiveLine(leading: Option[ByteVector]): Writer1[ByteVector, ByteVector, Out[ByteVector]] = {
      def handle(bv: ByteVector): Writer1[ByteVector, ByteVector, Out[ByteVector]] = {
        logger.trace(s"handle: $bv")
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

    def takeUpTo(n: Long, pf: => ParseFailure): Process1[ByteVector, ByteVector] =
      if (n > 0) receive1(bv => emit(bv) ++ takeUpTo(n - bv.size, pf))
      else fail(pf)

    def receiveCollapsedLine(leading: Option[ByteVector]): Process1[ByteVector, Out[ByteVector]] = {
      receiveLine(leading) pipe process1.fold(Out(ByteVector.empty)) {
        case (acc, -\/(partial)) => acc.copy(acc.a ++ partial)
        case (acc, \/-(Out(term, tail))) => Out(acc.a ++ term, tail)
      }
    }

    def start: Writer1[Headers, ByteVector, ByteVector] = {
      val partHeaderTooLong =
        ParseFailure("Invalid multipart entity", s"Part header was longer than ${headerLimit}-byte limit")

      logger.info("Exepected " + expected)

      def preamble(leading: Option[ByteVector]): Process1[ByteVector, Option[ByteVector]] =
        body(leading.map(_.compact), expected).flatMap {
          case Out(chunk, None) =>
            logger.debug(s"preamble chunk: $chunk")
            halt
          case Out(ByteVector.empty, tail) =>
            logger.trace(s"Resuming preamble with $tail.")
            emit(tail)
          case Out(chunk, tail) =>
            logger.debug(s"Last preamble chunk: $chunk.")
            logger.trace(s"Resuming with $tail.")
            emit(tail)
        }

      def beginPart(leading: Option[ByteVector]): Process1[ByteVector, Option[ByteVector]] = {
        def isStartLine(line: ByteVector): Boolean =
          line.startsWith(startLine) && isTransportPadding(line.drop(startLine.size))

        def isEndLine(line: ByteVector): Boolean =
          line.startsWith(endLine) && isTransportPadding(line.drop(endLine.size))

        def isTransportPadding(bv: ByteVector): Boolean =
          bv.toSeq.find(b => b != ' ' && b != '\t').isEmpty

        receiveCollapsedLine(leading) flatMap { case Out(line, tail) =>
          if (isStartLine(line)) {
            logger.debug("Found start line. Beginning new part.")
            emit(tail)
          }
          else if (isEndLine(line)) {
            logger.debug("Found end line. Halting.")
            halt
          }
          else if (line.nonEmpty) {
            logger.trace(s"Discarding preamble: $line")
            beginPart(tail)
          }
          else {
            halt
          }
        }
      }

      def headers(leading: Option[ByteVector]): Process1[ByteVector, Out[Headers]] = {
        takeUpTo(headerLimit, partHeaderTooLong) pipe (for {
          tail <- beginPart(leading)
          headerPair <- header(tail, expected)
          Out(headers, tail2) = headerPair
          _ = logger.debug(s"Headers: $headers")
          spacePair <- receiveCollapsedLine(tail2)
          tail3 = spacePair.tail // eat the space between header and content
        } yield Out(headers, tail3))
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

      // RFC1341 says we can tolerate a multipart body that begins
      // with an enacpsulation line NOT initiated by a CRLF.  If we
      // supply our own to start the preamble, we are tolerant.  If
      // it's in the body, it gets eaten as part of the preamble.
      preamble(Some(CRLF)).flatMap(go)
    }

    def header(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, Out[Headers]] = {
      def go(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, Out[Headers]] = {
        receiveCollapsedLine(leading) flatMap {
          case Out(bv, tail) => {
            if (bv == expected) {
              halt
            } else {
              val headerM = for {
                line <- bv.decodeAscii.right.toOption
                idx <- Some(line indexOf ':')
                if idx >= 0
                if idx < line.length - 1
              } yield Header(line.substring(0, idx), line.substring(idx + 1).trim)

              headerM.map { header => emit(Out(Headers(header), tail)) }
                .map { _ ++ go(tail, expected) }
                .getOrElse(halt)
            }
          }
        }
      }

      go(leading, expected).fold(Out(Headers.empty)) {
        case (acc, Out(header, tail)) => Out(acc.a ++ header, tail)
      }
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
