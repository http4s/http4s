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
class OutputIteratee(os: NIOOutputStream, chunkSize: Int)(implicit executionContext: ExecutionContext) extends Iteratee[HttpChunk,Unit] {

  // Keep a persistent listener. No need to make more objects than we have too
   private[this] object writeWatcher extends WriteHandler {
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

  def push(in: Input[HttpChunk]): Iteratee[HttpChunk,Unit] = {
    in match {
      case Input.Empty => this
      case Input.EOF => Done(Unit)
      case Input.El(chunk) => chunk match {
        case HttpEntity(bytes) => os.write(bytes)
        case _ => sys.error("Griz output Iteratee doesn't support your data type!")
      }
        // Need to sub pattern match?

        this
    }
  }

  def fold[B](folder: (Step[HttpChunk, Unit]) => Future[B]): Future[B] = {
    writeWatcher.registerAndListen().flatMap{ _ => folder(Step.Cont(push))}
  }
}
