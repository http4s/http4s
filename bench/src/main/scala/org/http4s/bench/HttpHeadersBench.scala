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
    for (header <- in.headers) {
      target = target.put(header)
    }
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
