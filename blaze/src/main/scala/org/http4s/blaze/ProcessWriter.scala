package org.http4s
package blaze

import scalaz.stream.Process
import Process._

import scalaz.concurrent.Task
import scalaz.{\/, -\/, \/-}

import scala.annotation.tailrec

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Failure, Success}
import scodec.bits.ByteVector

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
  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Any]

  /** write the ending BodyChunk and possibly a trailer to the wire
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @param trailers optional Trailers to write
    * @return a future letting you know when its safe to continue
    */
  protected def writeEnd(chunk: ByteVector, trailers: Headers): Future[Any]

  def requireClose(): Boolean = false

  /** Called in the event of an Await failure to alert the pipeline to cleanup */
  protected def exceptionFlush(): Future[Any] = Future.successful()

  /** Creates a Task that writes the contents the Process to the output.
    * Cancelled exceptions fall through to the Task cb
    * This method will halt writing the process once a trailer is encountered
    *
    * @param p Process[Task, Chunk] to write out
    * @return the Task which when run will unwind the Process
    */
  def writeProcess(p: Process[Task, ByteVector]): Task[Unit] = Task.async(go(p, _))

  final private def go(p: Process[Task, ByteVector], cb: CBType): Unit = p match {
    case Emit(seq, tail) =>
      if (seq.isEmpty) go(tail, cb)
      else {
        val buffandt = copyChunks(seq.map(-\/(_)))
        val buff = buffandt._1
        val trailers = buffandt._2

        if (trailers == null) {  // TODO: is it worth the complexity to try to predict the tail?
          if (!tail.isInstanceOf[Halt]) writeBodyChunk(buff, false).onComplete {
            case Success(_) => go(tail, cb)
            case Failure(t) => tail.killBy(t).run.runAsync(cb)
          }
          else { // Tail is a Halt state
            if (tail.asInstanceOf[Halt].cause eq End) {  // Tail is normal termination
              writeEnd(buff, Headers.empty).onComplete(completionListener(_, cb))
            } else {   // Tail is exception
              val e = tail.asInstanceOf[Halt].cause
              writeEnd(buff, Headers.empty).onComplete {
                case Success(_) => cb(-\/(e))
                case Failure(t) => cb(-\/(new CausedBy(t, e)))
              }
            }
          }
        }
        else writeEnd(buff, trailers).onComplete {
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

    case Halt(End) => writeEnd(ByteVector.empty, Headers.empty).onComplete(completionListener(_, cb))

    case Halt(error) => cb(-\/(error))
  }

  // Must get a non-empty sequence
  private def copyChunks(seq: Seq[ByteVector \/ Headers]): (ByteVector, Headers) = {

    @tailrec
    def go(acc: ByteVector, seq: Seq[ByteVector \/ Headers]): (ByteVector, Headers) = seq.head match {
      case -\/(c) =>
        val cc = acc ++ c
        if (!seq.tail.isEmpty) go(cc, seq.tail)
        else (c, null)

      case \/-(c) => (acc, c)
    }

    if (seq.tail.isEmpty) seq.head match {
      case -\/(c) => (c, null)
      case \/-(c) => (ByteVector.empty, c)
    } else go(ByteVector.empty, seq)
  }

  private def completionListener(t: Try[_], cb: CBType): Unit = t match {
    case Success(_) =>  cb(\/-())
    case Failure(t) =>  cb(-\/(t))
  }
}