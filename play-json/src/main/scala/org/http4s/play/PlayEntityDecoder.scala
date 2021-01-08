/*
 * Copyright 2018 http4s.org
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

package org.http4s.play

import cats.effect.Concurrent
import org.http4s.EntityDecoder
import play.api.libs.json.Reads

/** Derive [[EntityDecoder]] if implicit [[play.api.libs.json.Reads]] is in
  * the scope without need to explicitly call `jsonOf`.
  */
trait PlayEntityDecoder {
  implicit def playEntityDecoder[F[_]: Concurrent, A: Reads]: EntityDecoder[F, A] = jsonOf[F, A]
}

object PlayEntityDecoder extends PlayEntityDecoder
