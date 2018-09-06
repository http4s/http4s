package org.http4s
package blazecore

import fs2._
import scala.concurrent.Future

package object util {

  /** Used as a terminator for streams built from repeatEval */
  private[http4s] val End = Right(None)

  private[http4s] def unNoneTerminateChunks[F[_], I]: Pipe[F, Option[Chunk[I]], I] =
    _.unNoneTerminate.repeatPull {
      _.uncons1.flatMap {
        case Some((hd, tl)) => Pull.output(hd).as(Some(tl))
        case None => Pull.done.as(None)
      }
    }

  private[http4s] val FutureUnit =
    Future.successful(())
}
