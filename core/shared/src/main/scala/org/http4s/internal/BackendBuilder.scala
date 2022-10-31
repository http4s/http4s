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

import cats.effect.MonadCancelThrow
import cats.effect.Resource
import fs2.Stream

private[http4s] trait BackendBuilder[F[_], A] {
  implicit protected def F: MonadCancelThrow[F]

  /** Returns the backend as a resource.  Resource acquire waits
    * until the backend is ready to process requests.
    */
  def resource: Resource[F, A]

  /** Returns the backend as a single-element stream.  The stream
    * does not emit until the backend is ready to process requests.
    * The backend is shut down when the stream is finalized.
    */
  def stream: Stream[F, A] = Stream.resource(resource)
}
