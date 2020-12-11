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

package org.http4s
package bench

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
class HttpHeadersBench {
  @Benchmark
  def apply(in: HeadersInput) =
    Headers(in.headerSeq.toList)

  @Benchmark
  def add(in: HeadersInput) = {
    var target: Headers = Headers.empty
    for (header <- in.headers)
      target = target.put(header)
  }

  @Benchmark
  def replace(in: HeadersInput) =
    in.headers.put(in.replacement)
}

@State(Scope.Thread)
class HeadersInput {
  @Param(Array("2", "4", "8", "16", "32", "64"))
  var size: Int = _

  var headerSeq: Seq[Header] = _
  var headers: Headers = _
  var replacement: Header = _

  @Setup
  def setup(): Unit = {
    headerSeq = (0 until size).map { i =>
      Header(s"X-Headers-Benchmark-$i", i.toString)
    }
    headers = Headers(headerSeq.toList)
    replacement = Header(s"X-Headers-Benchmark-${headers.size / 2}", "replacement")
  }
}
