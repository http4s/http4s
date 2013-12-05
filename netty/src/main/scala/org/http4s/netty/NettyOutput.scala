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

  protected def writeBodyBuffer(buff: ByteBuf): ChannelFuture

  protected def writeEnd(t: Option[TrailerChunk]): ChannelFuture

  def writeStream(p: Process[Task, Chunk]): Task[Unit] = Task.async(go(p, _))

  final private def go(p: Process[Task, Chunk], cb: CBType): Unit = p match {
    case Emit(seq, tail) =>
      if (seq.isEmpty) go(tail, cb)
      else {
        val buffandt = copyChunks(seq)
        val buff = buffandt._1
        val t = buffandt._2

        if (buff.readableBytes() > 0) {  // We have some bytes to write
          writeBodyBuffer(buff).addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) {
              if (future.isSuccess) {
                if (t == null) go(tail, cb)

                else {       // Send the last chunk
                  if (!tail.isInstanceOf[Halt] ||
                    (tail.asInstanceOf[Halt].cause ne End) )  // Got trailer, not end!
                    logger.warn(s"Received trailer, but not at end of stream. Tail: $tail")

                  writeEnd(Some(t)).addListener(new CompletionListener(cb))
                }
              }
              else if (future.isCancelled)  cb(-\/((new Cancelled(future.channel))))
              else                          cb(-\/((future.cause)))
            }
          })
        }
        else go(tail, cb)  // Possible stack overflow issue on a stream of empty BodyChunks
      }

    case Await(t, f, fb, c) => t.runAsync {  // Wait for it to finish, then continue to unwind
      case \/-(r) => go(f(r), cb)
      case -\/(t) => cb(-\/(t))
    }

    case Halt(End) => writeEnd(None).addListener(new CompletionListener(cb))

    case Halt(error) => cb(-\/(error))
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

  private class CompletionListener(cb: CBType) extends ChannelFutureListener {
    def operationComplete(future: ChannelFuture) {
      if (future.isSuccess) cb(\/-(()))
      else if (future.isCancelled)  cb(-\/((new Cancelled(future.channel))))
      else                          cb(-\/((future.cause)))
    }
  }
}
