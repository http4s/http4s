/*
 * Copyright 2019 http4s.org
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

package io.chrisdavenport.keypool

/** Reusable is a Coproduct of the two states a Resource can be in at the
  * end of its lifetime.
  *
  * If it is Reuse then it will be attempted to place back in the pool,
  * if it is in DontReuse the resource will be shutdown.
  */
sealed trait Reusable
object Reusable {
  case object Reuse extends Reusable
  case object DontReuse extends Reusable
}
