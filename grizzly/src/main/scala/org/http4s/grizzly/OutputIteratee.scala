package org.http4s.grizzly

import play.api.libs.iteratee.{Step, Done, Input, Iteratee}
import org.http4s._
import org.glassfish.grizzly.WriteHandler
import concurrent.{ExecutionContext, Future, Promise}
import org.glassfish.grizzly.http.server.io.NIOOutputStream
import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Bryce Anderson
 * Created on 2/11/13 at 8:44 AM
 */
class OutputIteratee(os: NIOOutputStream, isChunked: Boolean)(implicit executionContext: ExecutionContext) extends Iteratee[HttpChunk,Unit]
  with Logging
{

  private[this] var osFuture: Future[Unit] = Future.successful()

  private[this] def writeBytes(bytes: Array[Byte]): Unit = {
    val promise: Promise[Unit] = Promise()

    val asyncWriter = new  WriteHandler {
      override def onError(t: Throwable) {
        promise.failure(t)
        sys.error(s"Error on write listener: ${t.getStackTraceString}")
      }
      override def onWritePossible() = promise.success{os.write(bytes); if(isChunked) os.flush() }
    }

    osFuture = osFuture.flatMap{ _ => os.notifyCanWrite(asyncWriter, bytes.length); promise.future }
  }

  // synchronized so that enumerators that work in different threads cant totally mess it up.
  private[this] def push(in: Input[HttpChunk]): Iteratee[HttpChunk,Unit] = synchronized {
    in match {
      case Input.El(chunk: BodyChunk) =>
        writeBytes(chunk.toArray)
        this

      case Input.El(chunk: TrailerChunk) =>
        logger.warn("Grizzly backend does not support trailers. Silently dropped.")
        Done(Input.Empty) // TODO return it as unconsumed?  This applies to all backends.

      case Input.EOF => Iteratee.flatten(osFuture.map(Done(_)))
      case Input.Empty => this
    }
  }

  def fold[B](folder: (Step[HttpChunk, Unit]) => Future[B]) = folder(Step.Cont(push))
}
