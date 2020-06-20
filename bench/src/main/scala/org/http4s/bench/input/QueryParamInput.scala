/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.bench.input

import org.openjdk.jmh.annotations.{Param, Scope, Setup, State}
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
