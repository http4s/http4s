/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/RangeUnit.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s

import org.http4s.util.{Renderable, Writer}

object RangeUnit {
  val Bytes = RangeUnit("bytes") // The only range-unit defined in rfc7233
  val None = new RangeUnit("none")
}

final case class RangeUnit(value: String) extends Renderable {
  override def toString: String = value
  def render(writer: Writer): writer.type = writer.append(value)
}
