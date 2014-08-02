package org.http4s.blaze.util

import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process._
import scalaz.{-\/, \/, \/-}

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
  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit]

  /** Write the ending chunk and, in chunked encoding, a trailer to the wire.
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue
    */
  protected def writeEnd(chunk: ByteVector): Future[Unit]

  def requireClose(): Boolean = false

  /** Called in the event of an Await failure to alert the pipeline to cleanup */
  protected def exceptionFlush(): Future[Unit] = Future.successful(())

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
        val buff = seq.reduce(_ ++ _)

        if (!tail.isInstanceOf[Halt]) writeBodyChunk(buff, false).onComplete {
          case Success(_) => go(tail, cb)
          case Failure(t) => tail.killBy(t).run.runAsync(cb)
        }
        else { // Tail is a Halt state
          if (tail.asInstanceOf[Halt].cause eq End) {  // Tail is normal termination
            writeEnd(buff).onComplete(completionListener(_, cb))
          } else {   // Tail is exception
            val e = tail.asInstanceOf[Halt].cause
            writeEnd(buff).onComplete {
              case Success(_) => cb(-\/(e))
              case Failure(t) => cb(-\/(new CausedBy(t, e)))
            }
          }
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

    case Halt(End) => writeEnd(ByteVector.empty).onComplete(completionListener(_, cb))

    case Halt(error) => cb(-\/(error))
  }

  private def completionListener(t: Try[_], cb: CBType): Unit = t match {
    case Success(_) =>  cb(\/-(()))
    case Failure(t) =>  cb(-\/(t))
  }
}