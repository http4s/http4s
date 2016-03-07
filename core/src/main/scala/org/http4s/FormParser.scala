package org.http4s

import scala.language.postfixOps

import scodec.bits.ByteVector

import scalaz.{-\/, \/-, \/}
import scalaz.stream.{process1, Process, Process1, Writer1}

object FormParser {
  import Process._

  def parse: Writer1[Map[String, String], ByteVector, ByteVector] = {
    def receiveLine(leading: Option[ByteVector]): Writer1[ByteVector, ByteVector, (ByteVector, Option[ByteVector])] = {
      def handle(bv: ByteVector): Writer1[ByteVector, ByteVector, (ByteVector, Option[ByteVector])] = {
        val index = bv indexOfSlice ByteVector('\r', '\n')

        if (index >= 0) {
          val (head, tailPre) = bv splitAt index
          val tail = tailPre drop 2       // remove the line feed characters
          val tailM = Some(tail) filter { !_.isEmpty }

          emit(\/-((head, tailM)))
        } else {
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

    lazy val start: Writer1[Map[String, String], ByteVector, ByteVector] = {
      val receiveExpectInit = receiveCollapsedLine(None) collect {
        case (bv, tail) if bv startsWith ByteVector('-', '-') => (ByteVector('\r', '\n') ++ bv ++ ByteVector('-', '-'), tail)
      }

      receiveExpectInit flatMap {
        case (expected, leading) => {
          for {
            (params, leading2) <- header(leading, expected)
            (_, leading3) <- receiveCollapsedLine(leading2)   // eat the space between header and content
            chunk <- emit(-\/(params)) ++ (body(leading3, expected) map { \/-(_) })
          } yield chunk
        }
      }
    }

    def header(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, (Map[String, String], Option[ByteVector])] = {
      def go(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, (Map[String, String], Option[ByteVector])] = {
        receiveCollapsedLine(leading) flatMap {
          case (bv, tail) => {
            if (bv == expected) {
              halt
            } else {
              val pairM = for {
                line <- bv.decodeAscii.right.toOption
                idx <- Some(line indexOf ':')
                if idx >= 0
                if idx < line.length - 1
              } yield (line.substring(0, idx), line.substring(idx + 1))

              pairM map { pair => (Map(pair), tail) } map emit map { _ ++ go(tail, expected) } getOrElse halt
            }
          }
        }
      }

      go(leading, expected).fold((Map[String, String](), None: Option[ByteVector])) {
        case ((acc, _), (more, tail)) => (acc ++ more, tail)
      }
    }

    def body(leading: Option[ByteVector], expected: ByteVector): Process1[ByteVector, ByteVector] = {
      val heads = (0 until expected.length).scanLeft(expected) { (acc, _) =>
        acc dropRight 1
      }

      def pre(bv: ByteVector): Process1[ByteVector, ByteVector] = {
        val idx = bv indexOfSlice expected
        if (idx >= 0) {
          emit(bv take idx)     // if we find the terminator *within* the chunk, we need to just trip and be done
        } else {
          // find goes from front to back, and heads is in order from longest to shortest, thus we always greedily match
          heads find (bv endsWith) map { tail =>
            emit(bv dropRight tail.length) ++ mid(tail, expected drop tail.length)
          } getOrElse (emit(bv) ++ receive1(pre))
        }
      }

      def mid(buffer: ByteVector, remainder: ByteVector): Process1[ByteVector, ByteVector] = {
        if (remainder.isEmpty) {
          halt
        } else {
          receive1Or[ByteVector, ByteVector](emit(buffer)) { bv =>      // the "or" is basically an error case, but whatever
            val (remFront, remBack) = remainder splitAt bv.length

            if (bv startsWith remFront)     // there might be trailing junk
              mid(buffer ++ remFront, remBack)
            else
              emit(buffer) ++ pre(bv)   // ok, so this buffer frame-slipped, but we might have a different terminator within bv
          }
        }
      }

      leading map pre getOrElse receive1(pre)
    }

    start
  }
}
