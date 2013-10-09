package org.http4s.grizzly

import play.api.libs.iteratee._
import org.http4s._
import concurrent.{ExecutionContext, Future}
import org.glassfish.grizzly.http.server.io.NIOOutputStream
import com.typesafe.scalalogging.slf4j.Logging
import java.io.IOException
import org.http4s.TrailerChunk

/**
 * @author Bryce Anderson
 * Created on 2/11/13 at 8:44 AM
 */
class OutputIteratee(os: NIOOutputStream, isChunked: Boolean)(implicit executionContext: ExecutionContext) extends Iteratee[HttpChunk,Unit]
  with Logging
{

  private[this] def push(in: Input[HttpChunk]): Iteratee[HttpChunk,Unit] =  {
    in match {
      case Input.El(chunk: BodyChunk) =>
        try {
          os.write(chunk.toArray)
          if(isChunked) os.flush()
          this
        } catch {
          case e: IOException =>
            logger.error("OutputIteratee hit a snag on write attempt.", e)
            Error(e.getMessage, in)
        }

      case Input.El(chunk: TrailerChunk) =>
        logger.warn("Grizzly backend does not support trailers. Silently dropped.")
        Done(()) // TODO return it as unconsumed?  This applies to all backends.

      case Input.EOF => Done(())
      case Input.Empty => this
    }
  }

  // TODO: should the ec be utilized?
  def fold[B](folder: (Step[HttpChunk, Unit]) => Future[B])(implicit ec: ExecutionContext) = folder(Step.Cont(push))
}
