package org.http4s

import java.nio.{ByteBuffer, CharBuffer}

import fs2._
import fs2.util.Attempt
import org.http4s.util.chunk
import scodec.bits.ByteVector

import scala.util.control.NonFatal

package object util {
  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = {
    val decoder = charset.nioCharset.newDecoder
    val avgBytesPerChar = math.ceil(1.0 / decoder.averageCharsPerByte().toDouble).toInt
    val maxCharsPerByte = math.ceil(decoder.maxCharsPerByte().toDouble).toInt
    val bufferSize = 8 * avgBytesPerChar + 1

    def push(chunkMaybe: Option[NonEmptyChunk[Byte]], carryOver: ByteVector): (ByteVector, Option[String]) = {
      val (nextBuffer, eof) = chunkMaybe match {
        case Some(chunk) =>
          (ByteVector(chunk.toArray), false)
        case None =>
          (ByteVector.empty, true)
      }
      val in = carryOver ++ nextBuffer
      val byteBuffer = in.toByteBuffer
      val charBuffer = CharBuffer.allocate(in.size.toInt + 1)
      decoder.decode(byteBuffer, charBuffer, eof)
      val nextByteVector = if (eof) {
        decoder.flush(charBuffer)
        ByteVector.empty
      } else {
        ByteVector.view(byteBuffer.slice)
      }
      (nextByteVector, Some(charBuffer.flip().toString))
    }

    _.rechunkN(bufferSize, allowFewer = true)
//      .chunks
//      .map(chunk => ByteVector(chunk.toArray))
//      .noneTerminate
      .repeatPull[String] {
        _.awaitN(bufferSize, allowFewer = true).optional.flatMap {
          case None =>
            val charBuffer = CharBuffer.allocate(bufferSize)
            decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
            decoder.flush(charBuffer)
            val outputString = charBuffer.flip().toString
            if (outputString.isEmpty) Pull.done
            else Pull.output1(outputString) as Handle.empty
          case Some((chunks, handle)) =>
            val chunk = chunks.flatMap(_.toList)
            val byteVector = ByteVector(chunk.toArray)
            val byteBuffer = byteVector.toByteBuffer
            val charBuffer = CharBuffer.allocate(byteVector.size.toInt * maxCharsPerByte)
            decoder.decode(byteBuffer, charBuffer, false)
            val nextByteVector = ByteVector.view(byteBuffer.slice)
            val nextHandle = handle.push(Chunk.bytes(nextByteVector.toArray))
            Pull.output1(charBuffer.flip().toString) as nextHandle
        }

      /*
        _.receive[String, Handle[F, (ByteVector, Byte)]] {
          case (chunk, handle) =>
            println(s"handling chunk ${new String(chunk.toArray)}")
            val byteVector = ByteVector(chunk.toArray)
            val byteBuffer = byteVector.toByteBuffer
            val charBuffer = CharBuffer.allocate(byteVector.size.toInt + 1)
            if (chunk.isEmpty) {
              println(s"empty chunk")
              decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
              decoder.flush(charBuffer)
              Pull.done
            } else {
              decoder.decode(byteBuffer, charBuffer, false)
              //            val nextHandle = if (eof) {
              //              decoder.flush(charBuffer)
              //              handle
              //            } else {
              //            }
              val nextByteVector = ByteVector.view(byteBuffer.slice)
              Pull.output1(charBuffer.flip().toString) as handle.map(nextByteVector -> _)
            }
            */


          /*
          case None =>
            println("handling none")
            val charBuffer = CharBuffer.allocate(bufferSize)
            decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
            decoder.flush(charBuffer)
            val outputString = charBuffer.flip().toString
            println(s"flushed all: '$outputString'")
            if (outputString.isEmpty) {
              println("well we're done")
              Pull.done
            }
            else Pull.output1(outputString) as Handle.empty
          case Some((chunk, handle)) =>
            println(s"handling chunk ${new String(chunk.toArray)}")
            val byteVector = ByteVector(chunk.toArray)
            val byteBuffer = byteVector.toByteBuffer
            val charBuffer = CharBuffer.allocate(byteVector.size.toInt + 1)
            if (chunk.isEmpty) {
              println(s"empty chunk")
              decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
              decoder.flush(charBuffer)
              Pull.done
            } else {
              decoder.decode(byteBuffer, charBuffer, false)
              //            val nextHandle = if (eof) {
              //              decoder.flush(charBuffer)
              //              handle
              //            } else {
              //            }
              val nextByteVector = ByteVector.view(byteBuffer.slice)
              val nextHandle = handle.push(Chunk.bytes(nextByteVector.toArray))
              Pull.output1(charBuffer.flip().toString) as nextHandle
            }
            */
      }
      /*
      .fold[(ByteVector, Option[String])]((ByteVector.empty, None)) {
        case ((byteVector, strMaybe), chunkMaybe) =>
          push(chunkMaybe, byteVector)
      }
      */
    /*
    val decoder = charset.nioCharset.newDecoder
    var carryOver = ByteVector.empty

    def push(chunk: ByteVector, eof: Boolean) = {
      val in = carryOver ++ chunk
      val byteBuffer = in.toByteBuffer
      val charBuffer = CharBuffer.allocate(in.size.toInt + 1)
      decoder.decode(byteBuffer, charBuffer, eof)
      if (eof) decoder.flush(charBuffer)
      else carryOver = ByteVector.view(byteBuffer.slice)
      charBuffer.flip().toString
    }

    // A ByteVector can now be longer than Int.MaxValue, but the CharBuffer
    // above cannot.  We need to split enormous chunks just in case.
    def breakBigChunks(): Process1[ByteVector, ByteVector] =
      receive1[ByteVector, ByteVector] { chunk =>
        def loop(chunk: ByteVector): Process1[ByteVector, ByteVector] =
          chunk.splitAt(Long.MaxValue - 1L) match {
            case (bv, ByteVector.empty) =>
              emit(bv) ++ breakBigChunks()
            case (bv, tail) =>
              emit(bv) ++ loop(tail)
          }
        loop(chunk)
      }

    def go(): Process1[ByteVector, String] = receive1[ByteVector, String] { chunk =>
      val s = push(chunk, false)
      val sChunk = if (s.nonEmpty) emit(s) else halt
      sChunk ++ go()
    }

    def flush() = {
      val s = push(ByteVector.empty, true)
      if (s.nonEmpty) emit(s) else halt
    }

    breakBigChunks() pipe go() onComplete flush()
  }
    */




      /*
      .mapAccumulate[Option[ByteBuffer], String](None) {
        case (remainingBytesMaybe, Some(chunk)) =>
//          println(s"Handling Some(${chunk.toArray.mkString(",")}) with remaining bytes ${remainingBytesMaybe.map(_.duplicate().array().mkString(","))}")
          //val byteBuffer = remainingBytesMaybe.map(remaining => ByteBuffer.wrap(remaining.array() ++ chunk.toArray[Byte])).getOrElse(ByteBuffer.wrap(chunk.toArray))
          //val byteBuffer = remainingBytesMaybe.map(buffer => buffer.put(chunk.toArray, buffer.limit(), chunk.size)).getOrElse(ByteBuffer.wrap(chunk.toArray))
          //val byteBuffer = remainingBytesMaybe.map(_.put(chunk.toArray)).getOrElse(ByteBuffer.wrap(chunk.toArray))
          val byteBuffer = remainingBytesMaybe.map { remainingBuffer =>
            val newBuffer = ByteBuffer.allocate(remainingBuffer.remaining() + chunk.size)
            val out = newBuffer.put(remainingBuffer).put(chunk.toArray)
            println(s"out is: ${out.array().mkString(",")}")
            out
//            val remainingBytes = Array.ofDim[Byte](buffer.remaining())
//            ByteBuffer.wrap(buffer.get(remainingBytes) ++ buffer.put(chunk.toArray))
          }.getOrElse(ByteBuffer.wrap(chunk.toArray))
          println(s"pre-decode byte buffer details: limit ${byteBuffer.limit()}, position ${byteBuffer.position()}, array ${byteBuffer.array().mkString(",")}")
          val charBuffer = CharBuffer.allocate(2 * byteBuffer.remaining() * maxCharsPerByte)
//          println(s"decoding ${byteBuffer.duplicate().array().mkString(",")}")
          val decodeResult = decoder.decode(byteBuffer, charBuffer, false)
          val outputString = charBuffer.flip().toString
          println(s"output string: '$outputString' from decode: $decodeResult")
          println(s"post-decode byte buffer details: limit ${byteBuffer.limit()}, position ${byteBuffer.position()}, array ${byteBuffer.array().mkString(",")}\n")
          (Some(byteBuffer).filter(_.hasRemaining), outputString)
        case (remainingBytesMaybe, None) =>
//          println(s"Handling None with remaining ${remainingBytesMaybe.map(_.duplicate().array().mkString(","))}")
          val byteBuffer = remainingBytesMaybe.getOrElse(ByteBuffer.allocate(0))
          val charBuffer = CharBuffer.allocate(2 * byteBuffer.remaining() * maxCharsPerByte)
//          println(s"decoding ${byteBuffer.duplicate().array().mkString(",")}")
          decoder.decode(byteBuffer, charBuffer, true)
          decoder.flush(charBuffer)
          (None, charBuffer.flip().toString)
      }.map(_._2)
      */
      /*
      .map { chunk =>
        val byteBuffer = ByteBuffer.wrap(chunk.toArray)
        val charBuffer = CharBuffer.allocate(chunk.size * maxCharsPerByte)
        val result = decoder.decode(byteBuffer, charBuffer, false)
        val flipped = charBuffer.flip()
        println(s"result: $result, CharBuffer: $flipped. ByteBuffer has remaining ${byteBuffer.hasRemaining}")
        flipped.toString
      }
      */
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")

  private[http4s] def tryCatchNonFatal[A](f: => A): Attempt[A] =
    try Right(f)
    catch { case NonFatal(t) => Left(t) }

  @deprecated("Moved to org.http4s.syntax.StringOps", "0.16")
  type CaseInsensitiveStringOps = org.http4s.syntax.StringOps

  @deprecated("Moved to org.http4s.syntax.StringSyntax", "0.16")
  type CaseInsensitiveStringSyntax = org.http4s.syntax.StringSyntax
}
