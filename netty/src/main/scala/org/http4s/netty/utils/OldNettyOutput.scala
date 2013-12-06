//package org.http4s
//package netty
//package utils
//
//import scalaz.stream.Process
//import Process._
//
//import scalaz.concurrent.Task
//import scalaz.{-\/, \/-, \/, Monad}
//
//import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelHandlerContext}
//import io.netty.buffer.{Unpooled, ByteBuf}
//import scala.annotation.tailrec
//import io.netty.util.concurrent.GenericFutureListener
//import com.typesafe.scalalogging.slf4j.Logging
//
///**
// * @author Bryce Anderson
// *         Created on 12/1/13
// */
//trait OldNettyOutput[MsgType] { self: Logging =>
//
//  type CBType = Throwable \/ Unit => Unit
//
//  def bufferToMessage(buff: ByteBuf): MsgType
//
//  def endOfStreamChunk(trailer: Option[TrailerChunk]): MsgType
//
//  def writeStream(p: Process[Task, Chunk], ctx: ChannelHandlerContext): Task[Unit] = Task.async(go(p, ctx, _))
//
//  /** give an opportunity to check if the buffer should be written
//    *
//    * @param tail rest of the process
//    * @param buff accumulated body to be written
//    * @param ctx ChannelHandlerContext of this channel
//    * @param cb callback of the Task returned at the end of writing the body
//    */
//  def writeBytes(tail: Process[Task, Chunk], buff: ByteBuf, ctx: ChannelHandlerContext, cb: CBType) {
//    ctx.channel().writeAndFlush(bufferToMessage(buff)).addListener(new ChannelFutureListener {
//      def operationComplete(future: ChannelFuture) {
//        if (future.isSuccess)         go(tail, ctx, cb)
//        else if (future.isCancelled)  cb(-\/((new Cancelled(future.channel))))
//        else                          cb(-\/((future.cause)))
//      }
//    })
//  }
//
//  /** Write the last bytes, if there are any, and the trailer. Only called in the event of a trailer
//    *
//    * @param buff last buffer. May be empty
//    * @param t trailer to be sent
//    * @param ctx ChannelHandlerContext of this channel
//    * @param cb callback of the Task returned at the end of writing the body
//    */
//  def writeLast(buff: ByteBuf, t: TrailerChunk, ctx: ChannelHandlerContext, cb: CBType) {
//    if (buff.readableBytes() > 0) ctx.channel().write(bufferToMessage(buff))
//
//    ctx.channel.writeAndFlush(endOfStreamChunk(Some(t))).addListener(new ChannelFutureListener {
//      def operationComplete(future: ChannelFuture) {
//        if (future.isSuccess)         cb(\/-(()))
//        else if (future.isCancelled)  cb(-\/((new Cancelled(future.channel))))
//        else                          cb(-\/((future.cause)))
//      }
//    })
//  }
//
//  /** Unwinds the Process, writing the bytes along the way
//    *
//    * @param p Process[Task, Chunk] to unwind
//    * @param ctx the channel handler context of this channel
//    * @param cb a callback to alert when done
//    */
//  final protected def go(p: Process[Task, Chunk], ctx: ChannelHandlerContext, cb: CBType): Unit = p match {
//    case Emit(seq, tail) =>
//      if (seq.isEmpty) go(tail, ctx, cb)
//      else {
//        val buffandt = copyChunks(seq)
//        val buff = buffandt._1
//        val t = buffandt._2
//
//        if (t == null) {
//          if (buff.readableBytes() > 0) writeBytes(tail, buff, ctx, cb) // We have some bytes to write
//          else go(tail, ctx, cb)  // Possible stack overflow issue on a stream of empty BodyChunks
//
//        } else {  // Got a trailer
//          if (!tail.isInstanceOf[Halt] ||
//            (tail.asInstanceOf[Halt].cause ne End) )  // Got trailer, not end!
//            logger.warn(s"Received trailer, but not at end of stream. Tail: $tail")
//
//          writeLast(buff, t, ctx, cb)
//        }
//      }
//
//    case Await(t, f, fb, c) => t.runAsync {  // Wait for it to finish, then continue to unwind
//      case \/-(r) => go(f(r), ctx, cb)
//      case -\/(t) => cb(-\/(t))
//
//    }
//
//    case Halt(End) =>
//      val msg = endOfStreamChunk(None)
//      ctx.channel.writeAndFlush(msg); cb(\/-(()))  // Just flush it
//
//    case Halt(error) => cb(-\/(error))
//  }
//
//  // Must get a non-empty sequence
//  private def copyChunks(seq: Seq[Chunk]): (ByteBuf, TrailerChunk) = {
//
//    @tailrec
//    def go(acc: BodyChunk, seq: Seq[Chunk]): (ByteBuf, TrailerChunk) = seq.head match {
//      case c: BodyChunk =>
//        val cc = acc ++ c
//        if (!seq.tail.isEmpty) go(cc, seq.tail)
//        else (Unpooled.wrappedBuffer(cc.toArray), null)
//
//      case c: TrailerChunk => (Unpooled.wrappedBuffer(acc.toArray), c)
//    }
//
//    if (seq.tail.isEmpty) seq.head match {
//      case c: BodyChunk     => (Unpooled.wrappedBuffer(c.toArray), null)
//      case c: TrailerChunk  => (Unpooled.EMPTY_BUFFER, c)
//    } else go(BodyChunk(), seq)
//  }
//
//}
