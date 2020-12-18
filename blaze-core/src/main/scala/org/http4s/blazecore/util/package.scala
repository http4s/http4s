/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
