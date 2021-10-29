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

import org.http4s.EntityEncoder
import play.api.libs.json.Writes

/** Derive [[EntityEncoder]] if implicit [[play.api.libs.json.Writes]] is in
  * the scope without need to explicitly call `jsonEncoderOf`.
  */
trait PlayEntityEncoder {
  implicit def playEntityEncoder[F[_], A: Writes]: EntityEncoder[F, A] =
    jsonEncoderOf[F, A]
}

object PlayEntityEncoder extends PlayEntityEncoder
