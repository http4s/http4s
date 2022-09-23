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

import cats.effect.IO
import org.http4s.headers._
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

// sbt "bench/jmh:run -i 5 -wi 5 -f 1 -t 1 org.http4s.bench.HeadersBench"
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
class HeadersBench {
  val baseMessage: Response[IO] = Response[IO]()

  val cachedEncoding: `Content-Encoding` = `Content-Encoding`(ContentCoding.gzip)
  val cachedServer: Server = Server(ProductId("Benchmark"))

  @Benchmark
  def putHeaders: Response[IO] =
    baseMessage
      .putHeaders(`Content-Length`(1000L))
      .putHeaders(`Content-Encoding`(ContentCoding.gzip))
      .putHeaders(Server(ProductId("Benchmark")))

  @Benchmark
  def putHeaderRaw: Response[IO] =
    baseMessage
      .putHeader(`Content-Length`(1000L))
      .putHeader(cachedEncoding)
      .putHeader(cachedServer)
}
