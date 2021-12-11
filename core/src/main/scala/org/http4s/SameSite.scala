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

package org.http4s

import org.http4s.SameSite._
import org.http4s.util.Renderable
import org.http4s.util.Writer

/** RFC6265 SameSite cookie attribute values.
  */
sealed trait SameSite extends Renderable {
  override def render(writer: Writer): writer.type = {
    val str = this match {
      case Strict => "Strict"
      case Lax => "Lax"
      case None => "None"
    }
    writer.append(str)
  }
}

object SameSite {
  case object Strict extends SameSite
  case object Lax extends SameSite
  case object None extends SameSite
}
