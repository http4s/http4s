package org.http4s
package netty

import io.netty.channel.{ChannelFutureListener, ChannelFuture}

import scalaz.stream.Process
import Process._
import scalaz.concurrent.Task
import scalaz.{\/, -\/, \/-}

import io.netty.buffer.{Unpooled, ByteBuf}
import scala.annotation.tailrec
import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Bryce Anderson
 *         Created on 12/4/13
 */
trait NettyOutput { self: Logging =>

  type CBType = Throwable \/ Unit => Unit

  /** If a request is canceled, this method should return a canceled future */
  protected def writeBodyBuffer(buff: ByteBuf): ChannelFuture

  /** If a request is canceled, this method should return a canceled future */
  protected def writeEnd(buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture

  def writeProcess(p: Process[Task, Chunk]): Task[Unit] = Task.async(go(p, halt, _))

  final private def go(p: Process[Task, Chunk], cleanup: Process[Task, Chunk], cb: CBType): Unit = p match {
    case Emit(seq, tail) =>
      if (seq.isEmpty) go(tail, cleanup, cb)
      else {
        val buffandt = copyChunks(seq)
        val buff = buffandt._1
        val trailer = buffandt._2

        if (trailer == null) {  // TODO: is it worth the complexity to try to predict the tail?
          if (!tail.isInstanceOf[Halt]) writeBodyBuffer(buff).addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) {
              if (future.isSuccess)         go(tail, cleanup, cb)
              else if (future.isCancelled)  cleanup.run.runAsync(cb)
              else                          cleanup.causedBy(future.cause).run.runAsync(cb)
            }
          })
          else { // Tail is a Halt state
            if (tail.asInstanceOf[Halt].cause eq End) {  // Tail is normal termination
              writeEnd(buff, None).addListener(new CompletionListener(cb, cleanup))
            } else {   // Tail is exception
              val e = tail.asInstanceOf[Halt].cause
              writeEnd(buff, None).addListener(new CompletionListener(cb, cleanup.causedBy(e)))
            }
          }
        }

        else {
          if (!tail.isInstanceOf[Halt] &&
            (tail.asInstanceOf[Halt].cause ne End))
            logger.warn("Received trailer, but stream may not be empty. Running cleanup.")

          writeEnd(buff, Some(trailer)).addListener(new CompletionListener(cb, cleanup))
        }
      }

    case Await(t, f, fb, c) => t.runAsync {  // Wait for it to finish, then continue to unwind
      case \/-(r)   => go(f(r), c, cb)
      case -\/(End) => go(fb, c, cb)
      case -\/(t)   => go(c.causedBy(t), halt, cb)
    }

    case Halt(End) => writeEnd(Unpooled.EMPTY_BUFFER, None).addListener(new CompletionListener(cb, cleanup))

    case Halt(error) => cleanup match {
      case Halt(_) =>  cb(-\/(error))  // if the cleanup is a halt, and if so, just pitch out the error
      case _       =>  go(cleanup.causedBy(error), halt, cb)   // give cleanup a chance
    }
  }

  // Must get a non-empty sequence
  private def copyChunks(seq: Seq[Chunk]): (ByteBuf, TrailerChunk) = {

    @tailrec
    def go(acc: BodyChunk, seq: Seq[Chunk]): (ByteBuf, TrailerChunk) = seq.head match {
      case c: BodyChunk =>
        val cc = acc ++ c
        if (!seq.tail.isEmpty) go(cc, seq.tail)
        else (Unpooled.wrappedBuffer(cc.toArray), null)

      case c: TrailerChunk => (Unpooled.wrappedBuffer(acc.toArray), c)
    }

    if (seq.tail.isEmpty) seq.head match {
      case c: BodyChunk     => (Unpooled.wrappedBuffer(c.toArray), null)
      case c: TrailerChunk  => (Unpooled.EMPTY_BUFFER, c)
    } else go(BodyChunk(), seq)
  }

  private class CompletionListener(cb: CBType, cleanup: Process[Task, Chunk]) extends ChannelFutureListener {
    def operationComplete(future: ChannelFuture) {
      if (future.isSuccess)         cleanup.run.runAsync(cb)
      else if (future.isCancelled)  cleanup.run.runAsync(cb)
      else                          cleanup.causedBy(future.cause).run.runAsync(cb)
    }
  }
}
