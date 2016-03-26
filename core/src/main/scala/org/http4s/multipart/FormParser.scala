package org.http4s
package multipart

import scala.language.postfixOps

import scodec.bits.ByteVector

import scalaz.{-\/, \/-, \/}
import scalaz.stream.{process1, Process, Process1, Writer1}

/** A low-level multipart-parsing pipe.  Most end users will prefer EntityDecoder[Multipart]. */
object FormParser {
  import Process._

  def parse: Writer1[Headers, ByteVector, ByteVector] = {
    def receiveLine(leading: Option[ByteVector]): Writer1[ByteVector, ByteVector, (ByteVector, Option[ByteVector])] = {
      def handle(bv: ByteVector): Writer1[ByteVector, ByteVector, (ByteVector, Option[ByteVector])] = {
        val index = bv indexOfSlice ByteVector('\r', '\n')

        if (index >= 0) {
          val (head, tailPre) = bv splitAt index
          val tail = tailPre drop 2       // remove the line feed characters
          val tailM = Some(tail) filter { !_.isEmpty }

          emit(\/-((head, tailM)))
        }
        else if (bv.nonEmpty && bv.get(bv.length - 1) == '\r') {
          val (head, cr) = bv.splitAt(bv.length - 1)
          emit(-\/(head)) ++ receive1(next => handle(cr ++ next))
        }
        else {
          emit(-\/(bv)) ++ receiveLine(None)
        }
      }

      leading map handle getOrElse receive1(handle)
    }

    def receiveCollapsedLine(leading: Option[ByteVector]): Process1[ByteVector, (ByteVector, Option[ByteVector])] = {
      receiveLine(leading) pipe process1.fold((ByteVector.empty, None: Option[ByteVector])) {
        case ((acc, tail), -\/(left)) => (acc ++ left, tail)
        case ((acc, _), \/-((term, tail))) => (acc ++ term, tail)
      }
    }

    lazy val start: Writer1[Headers, ByteVector, ByteVector] = {
      def receiveExpectInit(leading: Option[ByteVector]): Process1[ByteVector, (ByteVector, Option[ByteVector])] = receiveCollapsedLine(leading) flatMap {
        case (ByteVector.empty, tail) =>
          // I suspect this could be rewritten to eliminate this condition.
          receiveExpectInit(tail)
        case (bv, tail) if bv.startsWith(ByteVector('\r', '\n')) =>
          // This one, too.
          receiveExpectInit(tail.map(bv.drop(2) ++ _))
        case (bv, tail) if bv.startsWith(ByteVector('-', '-')) =>
          if (bv.endsWith(ByteVector('-', '-')))
            halt
          else
            emit((ByteVector('\r', '\n') ++ bv, tail))
        case (bv, tail) =>
          // If we can make a nice string message, do it.  If it's binary goo, still show it.
          val badLineDetails = bv.decodeUtf8.fold(_ => bv.toString, identity)
          fail(InvalidMessageBodyFailure(s"""Expected next line to start with with "--": $badLineDetails"""))
      }

      def go(leading: Option[ByteVector]): Writer1[Headers, ByteVector, ByteVector] = {
        receiveExpectInit(leading) flatMap {
          case (ByteVector.empty, leading) =>
            go(leading)
          case (expected, leading) => {
            for {
              headerPair <- header(leading, expected)
              (headers, leading2) = headerPair
              spacePair <- receiveCollapsedLine(leading2)
              (chomp, leading3) = spacePair // eat the space between header and content
              part <- emit(-\/(headers)) ++ body(leading3, expected).flatMap {
                case (chunk, None) =>
                  emit(\/-(chunk))
                case (chunk, some @ Some(remainder)) => 
                  emit(\/-(chunk)) ++ go(some)
              }
            } yield part
          }
        }
      }

      go(None)
    }

    def header(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, (Headers, Option[ByteVector])] = {
      def go(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, (Headers, Option[ByteVector])] = {
        receiveCollapsedLine(leading) flatMap {
          case (bv, tail) => {
            if (bv == expected) {
              halt
            } else {
              val headerM = for {
                line <- bv.decodeAscii.right.toOption
                idx <- Some(line indexOf ':')
                if idx >= 0
                if idx < line.length - 1
              } yield Header(line.substring(0, idx), line.substring(idx + 1).trim)

              headerM.map { header => (Headers(header), tail) }
                .map(emit)
                .map { _ ++ go(tail, expected) }
                .getOrElse(halt)
            }
          }
        }
      }

      go(leading, expected).fold(Headers.empty, None: Option[ByteVector]) {
        case ((acc, _), (header, tail)) => (acc ++ header, tail)
      }
    }

    def body(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, (ByteVector, Option[ByteVector])] = {
      val heads = (0 until expected.length).scanLeft(expected) { (acc, _) =>
        acc dropRight 1
      }

      def pre(bv: ByteVector): Process1[ByteVector, (ByteVector, Option[ByteVector])] = {
        val idx = bv indexOfSlice expected
        if (idx >= 0) {
          // if we find the terminator *within* the chunk, we need to just trip and be done
          emit((bv take idx, Some(bv drop idx)))
        } else {
          // find goes from front to back, and heads is in order from longest to shortest, thus we always greedily match
          heads find (bv endsWith) map { tail =>
            emit((bv dropRight tail.length, None)) ++ mid(tail, expected drop tail.length)
          } getOrElse (emit((bv, None)) ++ receive1(pre))
        }
      }

      def mid(buffer: ByteVector, remainder: ByteVector): Process1[ByteVector, (ByteVector, Option[ByteVector])] = {
        if (remainder.isEmpty) {
          halt
        } else {
          // the "or" is basically an error case, but whatever
          receive1Or[ByteVector, (ByteVector, Option[ByteVector])](emit((buffer, None))) { bv =>
            val (remFront, remBack) = remainder splitAt bv.length

            if (bv startsWith remFront)     // there might be trailing junk
              mid(buffer ++ remFront, remBack)
            else
              emit((buffer, None)) ++ pre(bv)   // ok, so this buffer frame-slipped, but we might have a different terminator within bv
          }
        }
      }

      leading map pre getOrElse receive1(pre)
    }

    start
  }
}
