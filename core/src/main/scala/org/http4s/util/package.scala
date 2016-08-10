package org.http4s

import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder

import scodec.bits.ByteVector

import scalaz.State
import scalaz.concurrent.Task
import scalaz.stream.{process1, Channel, Process, Process1}
import scalaz.stream.Process._
import scalaz.stream.io.bufferedChannel
import scalaz.std.option.none

package object util {
  /** Temporary.  Contribute back to scalaz-stream. */
  def decode(charset: Charset): Process1[ByteVector, String] = suspend {
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
          chunk.splitAt(Int.MaxValue - 1) match {
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

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")
}
