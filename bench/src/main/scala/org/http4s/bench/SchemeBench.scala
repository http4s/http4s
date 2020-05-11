/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package bench

import org.openjdk.jmh.annotations._

@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
class SchemeBench {
  @Benchmark
  def parseHttp =
    Uri.Scheme.fromString("http")

  @Benchmark
  def parseHttps =
    Uri.Scheme.fromString("https")

  @Benchmark
  def parseMailto =
    Uri.Scheme.fromString("mailto")
}
