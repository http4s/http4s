package org.http4s
package netty

import scalaz.stream.Process
import Process._

import scalaz.concurrent.Task
import scalaz.{\/, -\/, \/-}

import scala.annotation.tailrec
import com.typesafe.scalalogging.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Failure, Success}

/**
 * @author Bryce Anderson
 *         Created on 12/4/13
 */
trait ProcessWriter { self: Logging =>

  implicit protected def ec: ExecutionContext

  type CBType = Throwable \/ Unit => Unit

  /** If a request is canceled, this method should return a canceled future */
  protected def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any]

  /** If a request is canceled, this method should return a canceled future */
  protected def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any]

  def writeProcess(p: Process[Task, Chunk]): Task[Unit] = Task.async(go(p, halt, _))

  final private def go(p: Process[Task, Chunk], cleanup: Process[Task, Chunk], cb: CBType): Unit = p match {
    case Emit(seq, tail) =>
      if (seq.isEmpty) go(tail, cleanup, cb)
      else {
        val buffandt = copyChunks(seq)
        val buff = buffandt._1
        val trailer = buffandt._2

        if (trailer == null) {  // TODO: is it worth the complexity to try to predict the tail?
          if (!tail.isInstanceOf[Halt]) writeBodyChunk(buff, true).onComplete {
            case Success(_) =>             go(tail, cleanup, cb)
            case Failure(Cancelled) =>  cleanup.run.runAsync(cb)
            case Failure(t) =>             cleanup.causedBy(t).run.runAsync(cb)
          }

          else { // Tail is a Halt state
            if (tail.asInstanceOf[Halt].cause eq End) {  // Tail is normal termination
              writeEnd(buff, None).onComplete(completionListener(_, cb, cleanup))
            } else {   // Tail is exception
              val e = tail.asInstanceOf[Halt].cause
              writeEnd(buff, None).onComplete(completionListener(_, cb, cleanup.causedBy(e)))
            }
          }
        }

        else {
          if (!tail.isInstanceOf[Halt] &&
            (tail.asInstanceOf[Halt].cause ne End))
            logger.warn("Received trailer, but stream may not be empty. Running cleanup.")

          writeEnd(buff, Some(trailer)).onComplete(completionListener(_, cb, cleanup))
        }
      }

    case Await(t, f, fb, c) => t.runAsync {  // Wait for it to finish, then continue to unwind
      case \/-(r)   => go(f(r), c, cb)
      case -\/(End) => go(fb, c, cb)
      case -\/(t)   => go(c.causedBy(t), halt, cb)
    }

    case Halt(End) => writeEnd(BodyChunk(), None).onComplete(completionListener(_, cb, cleanup))

    case Halt(error) => cleanup match {
      case Halt(_) =>  cb(-\/(error))  // if the cleanup is a halt, and if so, just pitch out the error
      case _       =>  go(cleanup.causedBy(error), halt, cb)   // give cleanup a chance
    }
  }

  // Must get a non-empty sequence
  private def copyChunks(seq: Seq[Chunk]): (BodyChunk, TrailerChunk) = {

    @tailrec
    def go(acc: BodyChunk, seq: Seq[Chunk]): (BodyChunk, TrailerChunk) = seq.head match {
      case c: BodyChunk =>
        val cc = acc ++ c
        if (!seq.tail.isEmpty) go(cc, seq.tail)
        else (c, null)

      case c: TrailerChunk => (acc, c)
    }

    if (seq.tail.isEmpty) seq.head match {
      case c: BodyChunk     => (c, null)
      case c: TrailerChunk  => (BodyChunk(), c)
    } else go(BodyChunk(), seq)
  }

  private def completionListener(t: Try[_], cb: CBType, cleanup: Process[Task, Chunk]): Unit = t match {
    case Success(_)        =>    cleanup.run.runAsync(cb)
    case Failure(Cancelled) =>   cleanup.run.runAsync(cb)
    case Failure(t) =>           cleanup.causedBy(t).run.runAsync(cb)
  }
}
