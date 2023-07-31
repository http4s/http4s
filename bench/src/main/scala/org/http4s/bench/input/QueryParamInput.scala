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

package org.http4s.bench.input

import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

import scala.util.Random

@State(Scope.Thread)
class QueryParamInput {
  @Param(Array("10", "100", "1000"))
  var size: Int = _

  var queryParams: Map[String, String] = _

  private def genString(): String =
    Random.nextString(Random.nextInt(50))

  @Setup
  def setup(): Unit =
    queryParams = (1 to size).map(_ => genString() -> genString()).toMap
}
