/*
 * Copyright 2013 http4s.org
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

package org.http4s.internal

import cats.effect.kernel.Sync
import org.log4s

private[internal] trait InternalPlatform {

  @deprecated("log4s will be removed from http4s-core in 1.0", "0.23.15")
  private[http4s] def loggingAsyncCallback[F[_], A](
      logger: log4s.Logger
  )(attempt: Either[Throwable, A])(implicit F: Sync[F]): F[Unit] =
    attempt match {
      case Left(e) => F.delay(logger.error(e)("Error in asynchronous callback"))
      case Right(_) => F.unit
    }

}
