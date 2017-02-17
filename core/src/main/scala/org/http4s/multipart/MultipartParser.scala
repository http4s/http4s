package org.http4s
package multipart

import scodec.bits.ByteVector
import fs2._
import fs2.Stream._
//import fs2.Pull._
import fs2.util.{Async,Attempt,Free,Functor,Sub1}
import cats.syntax.either._
import fs2.util.syntax._

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object MultipartParser {
  import Process._

  private[this] val logger = org.log4s.getLogger

  private val CRLF = ByteVector('\r', '\n')
  private val DASHDASH = ByteVector('-', '-')

  private final case class Out[+A](a: A, tail: Option[ByteVector] = None)

  def parse(boundary: Boundary): Pipe[Task, Byte, Either[Headers, ByteVector]] = { s =>
    val boundaryBytes = boundary.toByteVector
    val startLine = DASHDASH ++ boundaryBytes
    val endLine = startLine ++ DASHDASH

    // At various points, we'll read up until we find this, to loop back into beginPart.
    val expected = CRLF ++ startLine

    def receiveLine(leading: Option[ByteVector]): Handle[Task, ByteVector] => Pull[Task, Either[ByteVector, Out[ByteVector]], Unit] = h => {
      def handle(bv: ByteVector): Handle[Task, ByteVector] => Pull[Task, Either[ByteVector, Out[ByteVector]], Unit] = h => {
        logger.trace(s"handle: $bv")
        val index = bv indexOfSlice CRLF

        if (index >= 0) {
          val (head, tailPre) = bv splitAt index
          val tail = tailPre drop 2       // remove the line feed characters
          val tailM = Some(tail) filter { !_.isEmpty }
          Pull.output1(Either.right[ByteVector, Out[ByteVector]](Out(head, tailM)))
        }
        else if (bv.nonEmpty && bv.get(bv.length - 1) == '\r') {
          // The CRLF may have split across chunks.
          val (head, cr) = bv.splitAt(bv.length - 1)
          Pull.outputs(emit(Either.left(head))) >> h.receive1((next, h) => handle(cr ++ next)(h))
        }
        else {
          Pull.outputs(emit(Either.left(bv))) >>  receiveLine(None)(h)
        }
      }

      leading.map(handle(_)(h)) getOrElse h.receive1((b, h) => handle(b)(h))
    }

    def receiveCollapsedLine(leading: Option[ByteVector]): Handle[Task, ByteVector] => Pull[Task, Out[ByteVector], Unit] = h => {
      receiveLine(leading)(h).close.through(pipe.fold(Out(ByteVector.empty)) {
        case (acc, Left(partial)) => acc.copy(acc.a ++ partial)
        case (acc, Right(Out(term, tail))) => Out(acc.a ++ term, tail)
      })}.output


    def start: Handle[Task, ByteVector] =>  Pull[Task, Either[Headers,ByteVector], Unit] = h => {

      def beginPart(leading: Option[ByteVector]): Handle[Task, ByteVector] => Pull[Task, Option[ByteVector], Unit] = h => {
        def isStartLine(line: ByteVector): Boolean =
          line.startsWith(startLine) && isTransportPadding(line.drop(startLine.size))

        def isEndLine(line: ByteVector): Boolean =
          line.startsWith(endLine) && isTransportPadding(line.drop(endLine.size))

        def isTransportPadding(bv: ByteVector): Boolean =
          bv.toSeq.find(b => b != ' ' && b != '\t').isEmpty

        receiveCollapsedLine(leading)(h).close.flatMap { case Out(line, tail) =>
          if (isStartLine(line)) {
            logger.debug("Found start line. Beginning new part.")
            Stream.emit(tail)
          }
          else if (isEndLine(line)) {
            logger.debug("Found end line. Halting.")
            Stream.empty
          }
          else {
            logger.trace(s"Discarding prelude: $line")
            beginPart(tail)(h).close

          }
        }.output
      }

      def go(leading: Option[ByteVector]): Handle[Task, ByteVector] => Pull[Task, Either[Headers, ByteVector], Unit] = h => {
        for {
          tail <- beginPart(leading)(h).close
          headerpair <- header(tail, expected)(h).close
          Out(headers, tail2) = headerpair
          //          Out(headers, tail2) = headerPair
          _ = logger.debug(s"Headers: $headers")
          spacePair <- receiveCollapsedLine(tail2)(h).close
          tail3 = spacePair.tail // eat the space between header and content
          part <-  Pull.output1(Either.left(headers)).close ++ {body(tail3.map(_.compact), expected)(h).close.flatMap{
            case Out(chunk, None) =>
              logger.debug(s"Chunk: $chunk")
              Pull.output1(Either.right(chunk)).close
            case Out(ByteVector.empty, tail) =>
              logger.trace(s"Resuming with $tail.")
              Pull.outputs(go(tail)(h).close).close
            case Out(chunk, tail) =>
              logger.debug(s"Last chunk: $chunk.")
              logger.trace(s"Resuming with $tail.")
              emit(Either.right(chunk)) ++  go(tail)(h).close
          }}
        } yield part

      }.output


        go(None)(h)
      }


      def header(leading: Option[ByteVector], expected: ByteVector): Handle[Task, ByteVector] => Pull[Task, Out[Headers], Unit] = h => {
        def go(leading: Option[ByteVector], expected: ByteVector): Handle[Task, ByteVector] => Pull[Task, Out[Headers], Unit] = h => {
          {
            receiveCollapsedLine(leading)(h).close flatMap {
              case Out(bv, tail) => {
                if (bv == expected) {
                  Stream.empty[Task, Out[Headers]]
                } else {
                  val headerM = for {
                    line <- bv.decodeAscii.right.toOption
                    idx <- Some(line indexOf ':')
                    if idx >= 0
                    if idx < line.length - 1
                  } yield Header(line.substring(0, idx), line.substring(idx + 1).trim)

                  headerM.map { header => emit(Out(Headers(header), tail)) }
                    .map {
                      _ ++ go(tail, expected)(h).close
                    }
                    .getOrElse(Stream.empty)
                }
              }
            }
          }.output
        }

        go(leading, expected)(h).close.fold(Out(Headers.empty)) {
          case (acc, Out(header, tail)) => Out(acc.a ++ header, tail)
        }.output
      }



    def body(leading: Option[ByteVector], expected: ByteVector):
    Handle[Task, ByteVector] => Pull[Task, Out[ByteVector], Unit] = h => {

      val heads = (0L until expected.length).scanLeft(expected) { (acc, _) =>
        acc dropRight 1
      }

      def pre(bv: ByteVector):
      Handle[Task, ByteVector] => Pull[Task, Out[ByteVector], Unit] = h => {
        logger.trace(s"pre: $bv")
        val idx = bv indexOfSlice expected
        if (idx >= 0) {
          // if we find the terminator *within* the chunk, we need to just trip and be done
          // the +2 consumes the CRLF before the multipart boundary
          Pull.output1(Out(bv take idx, Some(bv drop (idx + 2))))
        } else {
          // find goes from front to back, and heads is in order from longest to shortest, thus we always greedily match
          heads.find(bv.endsWith).map { tail =>
            Pull.output1(Out(bv dropRight tail.length)).covary[Task] >> mid(tail, expected drop tail.length)(h)
          }.getOrElse {
            Pull.output1(Out(bv)).covary[Task] >> h.receive1((bv, h) => pre(bv)(h))
          }
        }
      }

      /* We might be looking at a boundary, or we might not.  This is how we
       * decide that incrementally.
       *
       * @param found the part of the next boundary that we've matched so far
       * @param remainder the part of the next boundary we're looking for on the next read.
       */
      def mid(found: ByteVector, remainder: ByteVector):
      Handle[Task, ByteVector] => Pull[Task, Out[ByteVector], Unit] = h => {
        logger.trace(s"mid: $found remainder")
        if (remainder.isEmpty) {
          // found should start with a CRLF, because mid is called with it.
          Pull.output1(Out(ByteVector.empty, Some(found.drop(2))))
        } else {
          h.receive1 { (bv, h) =>
            val (remFront, remBack) = remainder splitAt bv.length
            if (bv startsWith remFront) {
              // If remBack is nonEmpty, then the progress toward our match
              // is represented by bv, and we'll loop back to read more to
              // look for remBack.
              //
              // If remBack is empty, then `found ++ bv` starts with the
              // next boundary, and we'll emit out everything we've read
              // on the next loop.
              mid(found ++ bv, remBack)(h)
            }
            else {
              // ok, so this buffer frame-slipped, but we might have a different terminator within bv
              Pull.output1(Out(found)) >> pre(bv)(h)
            }
          }
        }
      }

      leading.map(bv => pre(bv)(h)) getOrElse h.receive1((bv, h) => pre(bv)(h))
    }

    s.mapChunks(chunk => Chunk.singleton(ByteVector(chunk.toArray))).pull(start)
  }

}
