/** TODO fs2 port
package org.http4s.util

import java.io.{Writer => JWriter, _}

import scalaz.concurrent._
import scalaz.stream._
import scalaz.stream.async.{boundedQueue, unboundedQueue}
import scalaz.stream.async.mutable.Queue
import scodec.bits.ByteVector

/** Utilities for converting java.io APIs to scalaz-streams */
package object io {
  /** Passes an OutputStream to an action that captures the output as a Process.
  * Useful for adapting the `java.io` push model to scalaz-stream's pull model.
  *
  * @param size of the bounded queue that buffers writes.  If `f` writes
  * faster than the result is consumed, `f` will be blocked.
  * @param f an action that writes to an OutputStream
  * @param S the strategy to call f on.  Should be a different thread from the
  * consumer of the resulting Process, or else deadlock is possible.
  * @return a Process of what was written to the OutputStream passed to f
  */
  def captureOutputStream(bound: Int)(f: OutputStream => Unit)(implicit S: Strategy): Process[Task, ByteVector] =
    captureOutputStream(boundedQueue[ByteVector](bound))(f)

  /** Passes an OutputStream to an action that captures the output as a Process.
  * Useful for adapting the `java.io` push model to scalaz-stream's pull model.
  *
  * This variant uses an unbounded queue, which assures that `f` never blocks,
  * but may consume boundless memory if the consumer is slow.
  *
  * @param f an action that writes to an OutputStream
  * @param S the strategy to call f on.  Should be a different thread from the
  * consumer of the resulting Process, or else deadlock is possible.
  * @return a Process of what was written to the OutputStream passed to f
  */
  def captureOutputStream(f: OutputStream => Unit)(implicit S: Strategy): Process[Task, ByteVector] =
    captureOutputStream(unboundedQueue[ByteVector])(f)

  private def captureOutputStream(makeQueue: => Queue[ByteVector])(f: OutputStream => Unit)(implicit S: Strategy): Process[Task, ByteVector] =
    Process.suspend {
      val q = makeQueue
      val os = new OutputStream {
        override def close(): Unit =
          q.close.run
        override def write(bytes: Array[Byte]): Unit =
          q.enqueueOne(ByteVector(bytes)).run
        override def write(bytes: Array[Byte], off: Int, len: Int): Unit =
          q.enqueueOne(ByteVector(bytes, off, len)).run
        def write(b: Int): Unit =
          q.enqueueOne(ByteVector(b.toByte)).run
      }
      val start = Process.eval_(Task(f(os))).onComplete(Process eval_ q.close)
      q.dequeue.merge(start.drain)
    }

  /** Passes an Writer to an action that captures the output as a Process.
  * Useful for adapting the `java.io` push model to scalaz-stream's pull model.
  *
  * @param size of the bounded queue that buffers writes.  If `f` writes
  * faster than the result is consumed, `f` will be blocked.
  * @param f an action that writes to an Writer
  * @param S the strategy to call f on.  Should be a different thread from the
  * consumer of the resulting Process, or else deadlock is possible.
  * @return a Process of what was written to the Writer passed to f
  */
  def captureWriter(bound: Int)(f: JWriter => Unit)(implicit S: Strategy): Process[Task, ByteVector] =
    captureOutputStream(bound)(osCallbackToWriterCallback(f))

  /** Passes an Writer to an action that captures the output as a Process.
  * Useful for adapting the `java.io` push model to scalaz-stream's pull model.
  *
  * This variant uses an unbounded queue, which assures that `f` never blocks,
  * but may consume boundless memory if the consumer is slow.
  *
  * @param f an action that writes to an Writer
  * @param S the strategy to call f on.  Should be a different thread from the
  * consumer of the resulting Process, or else deadlock is possible.
  * @return a Process of what was written to the Writer passed to f
  */
  def captureWriter(f: JWriter => Unit)(implicit S: Strategy): Process[Task, ByteVector] =
    captureOutputStream(osCallbackToWriterCallback(f))

  private def osCallbackToWriterCallback(f: JWriter => Unit) = { out: OutputStream =>
    val w = new OutputStreamWriter(out)
    f(w)
    w.flush()
  }
}
  */
