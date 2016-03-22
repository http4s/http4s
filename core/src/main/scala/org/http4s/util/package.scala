package org.http4s

import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder

import scodec.bits.ByteVector

import scalaz.{Codensity, State}
import scalaz.concurrent.Task
import scalaz.stream.{process1, Channel, Process, Process1}
import scalaz.stream.Process._
import scalaz.stream.io.bufferedChannel
import scalaz.std.option.none

package object util {

  import java.util.concurrent.ExecutorService

  private[this] val managedLogger = org.log4s.getLogger("org.http4s.util.managed")

  def managed[R, A](acquire: Task[R])(shutdown: R => Task[Unit]): Codensity[Task, R] =
    new Codensity[Task, R] {
      def apply[A](f: R => Task[A]): Task[A] =
        acquire.flatMap { r =>
          managedLogger.info(s"Acquired managed resource: $r")
          f(r).onFinish {
            case _ =>
              managedLogger.info(s"Shutting down managed resource: $r")
              shutdown(r).handle {
                case t: Throwable =>
                  managedLogger.error(t)("Error closing managed resource")
              }
          }
        }
    }

  def manageExecutorService(esTask: Task[ExecutorService]): Codensity[Task, ExecutorService] =
    managed(esTask)(es => Task.delay(es.shutdown))

  implicit class ExecutorServiceSyntax(val self: ExecutorService) extends AnyVal {
    def manage: Codensity[Task, ExecutorService] =
      manageExecutorService(Task.now(self))
  }

  /** Temporary.  Contribute back to scalaz-stream. */
  def decode(charset: Charset): Process1[ByteVector, String] = suspend {
    val decoder = charset.nioCharset.newDecoder
    var carryOver = ByteVector.empty

    def push(chunk: ByteVector, eof: Boolean) = {
      val in = carryOver ++ chunk
      val byteBuffer = in.toByteBuffer
      val charBuffer = CharBuffer.allocate(in.size + 1)
      decoder.decode(byteBuffer, charBuffer, eof)
      if (eof)
        decoder.flush(charBuffer)
      else
        carryOver = ByteVector.view(byteBuffer.slice)
      charBuffer.flip().toString
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

    go() onComplete flush()
  }

  /** Constructs an assertion error with a reference back to our issue tracker. Use only with head hung low. */
  def bug(message: String): AssertionError =
    new AssertionError(s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}")
}
