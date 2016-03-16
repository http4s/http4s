package org.http4s
package blaze
package util

import scodec.bits.ByteVector

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task
import scalaz.stream.{Cause, Process}
import scalaz.stream.Process._
import scalaz.stream.Cause._
import scalaz.{-\/, \/, \/-}


trait ProcessWriter {

  private type StackElem = Cause => Trampoline[Process[Task,ByteVector]]

  /** The `ExecutionContext` on which to run computations, assumed to be stack safe. */
  implicit protected def ec: ExecutionContext

  /** Write a ByteVector to the wire.
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue
    */
  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit]

  /** Write the ending chunk and, in chunked encoding, a trailer to the
    * wire.  If a request is cancelled, or the stream is closed this
    * method should return a failed Future with Cancelled as the
    * exception, or a Future with a Boolean to indicate whether the
    * connection is to be closed or not.
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue (if `false`) or
    * to close the connection (if `true`)
    */
  protected def writeEnd(chunk: ByteVector): Future[Boolean]

  /** Called in the event of an Await failure to alert the pipeline to cleanup */
  protected def exceptionFlush(): Future[Unit] = Future.successful(())

  /** Creates a Task that writes the contents the Process to the output.
    * Cancelled exceptions fall through to the Task cb
    *
    * @param p Process[Task, ByteVector] to write out
    * @return the Task which when run will unwind the Process
    */
  def writeProcess(p: Process[Task, ByteVector]): Task[Boolean] = Task.async(go(p, Nil, _))

  /** Helper to allow `go` to be tail recursive. Non recursive calls can 'bounce' through
    * this function but must be properly trampolined or we risk stack overflows */
  final private def bounce(p: Process[Task, ByteVector], stack: List[StackElem], cb: Callback[Boolean]): Unit =
    go(p, stack, cb)

  @tailrec
  final private def go(p: Process[Task, ByteVector], stack: List[StackElem], cb: Callback[Boolean]): Unit = p match {
    case Emit(seq) if seq.isEmpty =>
      if (stack.isEmpty) writeEnd(ByteVector.empty).onComplete(completionListener(_, cb))
      else go(Try(stack.head.apply(End).run), stack.tail, cb)

    case Emit(seq) =>
      val buff = seq.reduce(_ ++ _)
      if (stack.isEmpty) writeEnd(buff).onComplete(completionListener(_, cb))
      else writeBodyChunk(buff, false).onComplete {
        case Success(_) => bounce(Try(stack.head(End).run), stack.tail, cb)
        case Failure(t) => bounce(Try(stack.head(Cause.Error(t)).run), stack.tail, cb)
      }

    case Await(t, f, _) => ec.execute(
      new Runnable {
        override def run(): Unit = t.runAsync { // Wait for it to finish, then continue to unwind
          case r@ \/-(_) => bounce(Try(f(r).run), stack, cb)
          case    -\/(e) => bounce(Try(f(-\/(Error(e))).run), stack, cb)
        }
      })

    case Append(head, tail) =>
     @tailrec   // avoid as many intermediates as possible
     def prepend(i: Int, stack: List[StackElem]): List[StackElem] = {
       if (i >= 0) prepend(i - 1, tail(i)::stack)
       else stack
     }

     go(head, prepend(tail.length - 1, stack), cb)

    case Halt(cause) if stack.nonEmpty => go(Try(stack.head(cause).run), stack.tail, cb)

    // Rest are terminal cases
    case Halt(End) => writeEnd(ByteVector.empty).onComplete(completionListener(_, cb))

    case Halt(Kill) => writeEnd(ByteVector.empty)
                         .flatMap(requireClose => exceptionFlush().map(_ => requireClose))
                         .onComplete(completionListener(_, cb))

    case Halt(Error(Terminated(cause))) => go(Halt(cause), stack, cb)

    case Halt(Error(t)) => exceptionFlush().onComplete {
      case Success(_) => cb(-\/(t))
      case Failure(_) => cb(-\/(t))
    }
  }

  private def completionListener(t: Try[Boolean], cb: Callback[Boolean]): Unit = t match {
    case Success(requireClose) =>  cb(\/-(requireClose))
    case Failure(t) =>  cb(-\/(t))
  }

  @inline
  private def Try(p: => Process[Task, ByteVector]): Process[Task, ByteVector] = {
    try p
    catch { case t: Throwable => Process.fail(t) }
  }
}
