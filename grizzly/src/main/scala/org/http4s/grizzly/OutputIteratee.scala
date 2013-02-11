package org.http4s.grizzly

import play.api.libs.iteratee.{Step, Done, Input, Iteratee}
import org.http4s._
import org.glassfish.grizzly.WriteHandler
import concurrent.{ExecutionContext, Future, Promise}
import org.glassfish.grizzly.http.server.io.NIOOutputStream

/**
 * @author Bryce Anderson
 * Created on 2/11/13 at 8:44 AM
 */
class OutputIteratee(os: NIOOutputStream, chunkSize: Int)(implicit executionContext: ExecutionContext) extends Iteratee[Chunk,Unit] {

  // Keep a persistent listener. No need to make more objects than we have too
   private[this] val writeWatcher = new WriteHandler {
    var promise: Promise[Unit] = _

    // Makes a new promise and registers the callback to complete it
    def registerAndListen() : Future[Unit] = {
      promise = Promise()
      os.notifyCanWrite(this,chunkSize)
      promise.future
    }

    def onError(t: Throwable) {
      promise.failure(t)
      sys.error(s"Error on write listener: ${t.getStackTraceString}")
    }

    def onWritePossible() = promise.success(Unit)
  }

  // Complains about reflective if I try to use an anonymous class

  def push(in: Input[Chunk]): Iteratee[Chunk,Unit] = {
    in match {
      case Input.Empty => this
      case Input.EOF => Done(Unit)
      case Input.El(chunk) =>
        os.write(chunk)
        this
    }
  }

  def fold[B](folder: (Step[Chunk, Unit]) => Future[B]): Future[B] = {
    writeWatcher.registerAndListen().flatMap{ _ => folder(Step.Cont(push))}
  }
}
