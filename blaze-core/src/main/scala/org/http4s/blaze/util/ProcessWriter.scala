package org.http4s.blaze.util

import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task
import scalaz.stream.{Cause, Process}
import scalaz.stream.Process._
import scalaz.stream.Cause._
import scalaz.{-\/, \/, \/-}

trait ProcessWriter {

  implicit protected def ec: ExecutionContext

  type CBType = Throwable \/ Unit => Unit

  /** write a ByteVector to the wire
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
    *
    * @param p Process[Task, ByteVector] to write out
    * @return the Task which when run will unwind the Process
    */
  def writeProcess(p: Process[Task, ByteVector]): Task[Unit] = Task.async(go(p, Vector.empty, _))

  final private def go(p: Process[Task, ByteVector], stack: Vector[Cause => Trampoline[Process[Task,ByteVector]]], cb: CBType): Unit = p match {
    case Emit(seq) if seq.isEmpty =>
      if (stack.isEmpty) writeEnd(ByteVector.empty).onComplete(completionListener(_, cb))
      else go(Try(stack.head.apply(End).run), stack.tail, cb)

    case Emit(seq) =>
      val buff = seq.reduce(_ ++ _)
      if (stack.isEmpty) writeEnd(buff).onComplete(completionListener(_, cb))
      else writeBodyChunk(buff, false).onComplete {
        case Success(_) => go(Try(stack.head(End).run), stack.tail, cb)
        case Failure(t) => go(Try(stack.head(Cause.Error(t)).run), stack.tail, cb)
      }

    case Await(t, f) => t.runAsync {  // Wait for it to finish, then continue to unwind
      case r@ \/-(_) => go(Try(f(r).run), stack, cb)
      case -\/(t)    => go(Try(f(-\/(Error(t))).run), stack, cb)
    }

    case Append(head, tail) =>
      if (stack.nonEmpty) go(head, tail ++ stack, cb)
      else go(head, tail, cb)

    case Halt(cause) if stack.nonEmpty => go(Try(stack.head(cause).run), stack.tail, cb)

    // Rest are terminal cases
    case Halt(End) => writeEnd(ByteVector.empty).onComplete(completionListener(_, cb))

    case Halt(Kill) => writeEnd(ByteVector.empty)
                         .flatMap(_ => exceptionFlush())
                         .onComplete(completionListener(_, cb))

    case Halt(Error(Terminated(cause))) => go(Halt(cause), stack, cb)

    case Halt(Error(t)) => exceptionFlush().onComplete {
      case Success(_) => cb(-\/(t))
      case Failure(_) => cb(-\/(t))
    }
  }

  private def completionListener(t: Try[_], cb: CBType): Unit = t match {
    case Success(_) =>  cb(\/-(()))
    case Failure(t) =>  cb(-\/(t))
  }

  @inline
  private def Try(p: => Process[Task, ByteVector]): Process[Task, ByteVector] = {
    try p
    catch { case t: Throwable => Process.fail(t) }
  }
}