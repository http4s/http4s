package org.http4s
package blaze

import scalaz.stream.Process
import Process._

import scalaz.concurrent.Task
import scalaz.{\/, -\/, \/-}

import scala.annotation.tailrec

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Failure, Success}

/**
 * @author Bryce Anderson
 *         Created on 12/4/13
 */

// TODO: this is a straight copy of the netty trait
trait ProcessWriter {

  implicit protected def ec: ExecutionContext

  type CBType = Throwable \/ Unit => Unit

  /** write a BodyChunk to the wire
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue
    */
  protected def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any]

  /** write the ending BodyChunk and possibly a trailer to the wire
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @param t optional TrailerChunk to write
    * @return a future letting you know when its safe to continue
    */
  protected def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any]

  /** Called in the event of an Await failure to alert the pipeline to cleanup */
  protected def exceptionFlush(): Future[Any] = Future.successful()

  /** Creates a Task that writes the contents the Process to the output.
    * Cancelled exceptions fall through to the Task cb
    * This method will halt writing the process once a trailer is encountered
    *
    * @param p Process[Task, Chunk] to write out
    * @return the Task which when run will unwind the Process
    */
  def writeProcess(p: Process[Task, Chunk]): Task[Unit] = Task.async(go(p, _))

  final private def go(p: Process[Task, Chunk], cb: CBType): Unit = p match {
    case Emit(seq, tail) =>
      if (seq.isEmpty) go(tail, cb)
      else {
        val buffandt = copyChunks(seq)
        val buff = buffandt._1
        val trailer = buffandt._2

        if (trailer == null) {  // TODO: is it worth the complexity to try to predict the tail?
          if (!tail.isInstanceOf[Halt]) writeBodyChunk(buff, false).onComplete {
            case Success(_) => go(tail, cb)
            case Failure(t) => tail.killBy(t).run.runAsync(cb)
          }
          else { // Tail is a Halt state
            if (tail.asInstanceOf[Halt].cause eq End) {  // Tail is normal termination
              writeEnd(buff, None).onComplete(completionListener(_, cb))
            } else {   // Tail is exception
              val e = tail.asInstanceOf[Halt].cause
              writeEnd(buff, None).onComplete {
                case Success(_) => cb(-\/(e))
                case Failure(t) => cb(-\/(new CausedBy(t, e)))
              }
            }
          }
        }
        else writeEnd(buff, Some(trailer)).onComplete {
          case Success(_) => tail.kill.run.runAsync(cb)
          case Failure(t) => tail.killBy(t).run.runAsync(cb)
        }
      }

    case Await(t, f, fb, c) => t.runAsync {  // Wait for it to finish, then continue to unwind
      case \/-(r)   => go(f(r), cb)
      case -\/(End) => go(fb, cb)
      case -\/(t)   => exceptionFlush().onComplete {
        case Success(_) => c.drain.causedBy(t).run.runAsync(cb)
        case Failure(t2) => c.drain.causedBy(t).causedBy(t2).run.runAsync(cb)
      }
    }

    case Halt(End) => writeEnd(BodyChunk(), None).onComplete(completionListener(_, cb))

    case Halt(error) => cb(-\/(error))
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

  private def completionListener(t: Try[_], cb: CBType): Unit = t match {
    case Success(_) =>  cb(\/-())
    case Failure(t) =>  cb(-\/(t))
  }
}