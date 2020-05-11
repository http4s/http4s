/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package bench

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

// sbt "bench/jmh:run -i 10 -wi 10 -f 2 -t 1 org.http4s.bench.EncodeHexBench"
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
class EncodeHexBench {
  val bytes = {
    val r = new scala.util.Random(2597)
    val bs = new Array[Byte](8192)
    r.nextBytes(bs)
    bs
  }

  @Benchmark
  def encodeHex: Array[Char] =
    org.http4s.internal.encodeHex(bytes)
}
