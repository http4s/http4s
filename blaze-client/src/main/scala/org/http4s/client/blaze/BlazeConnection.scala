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
package client
package blaze

import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import org.http4s.blaze.pipeline.TailStage

private trait BlazeConnection[F[_]] extends TailStage[ByteBuffer] with Connection[F] {
  def runRequest(req: Request[F], idleTimeout: F[TimeoutException]): F[Response[F]]
}
