/*
 * Copyright 2015 http4s.org
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

package org.http4s.circe

import cats.effect.Concurrent
import io.circe.Decoder
import org.http4s.EntityDecoder

/** Derive [[EntityDecoder]] if implicit [[io.circe.Decoder]] is in the scope
  * without need to explicitly call `jsonOf`.
  */
trait CirceEntityDecoder {
  implicit def circeEntityDecoder[F[_]: Concurrent, A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]
}

object CirceEntityDecoder extends CirceEntityDecoder
