package org.http4s
package blazecore

import scala.concurrent.Future
import fs2._

package object util {
  /** Used as a terminator for streams built from repeatEval */
  private[http4s] val End = Right(None)

  private[http4s] val FutureUnit =
    Future.successful(())

  private[http4s] def unNoneTerminateChunks[F[_],I]: Stream[F,Option[Chunk[I]]] => Stream[F,I] =
    pipe.unNoneTerminate(_) repeatPull { _ receive1 {
      case (hd, tl) => Pull.output(hd) as tl
    }}
}
