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
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import fs2.Stream
import io.circe._
import org.http4s.circe._
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

// sbt "bench/jmh:run -i 10 -wi 10 -f 2 -t 1 org.http4s.bench.CirceJsonStreamBench"
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class CirceJsonStreamBench {
  import CirceJsonStreamBench._

  @Benchmark
  def encode_stream(in: BenchState): Unit =
    streamJsonArrayEncoder.toEntity(in.stream).body.compile.drain.unsafeRunSync()

  @Benchmark
  def encode_buffer_one_chunk(in: BenchState): Unit =
    in.stream.compile.toVector
      .flatMap(v => jsonEncoder[IO].toEntity(Json.fromValues(v)).body.compile.drain)
      .unsafeRunSync()
}

object CirceJsonStreamBench {
  val obj: Json = Json.obj("foo" -> Json.obj("foo2" -> Json.fromString("bar")))

  @State(Scope.Benchmark)
  class BenchState {
    @Param(Array("100", "1000", "10000"))
    var elems: Int = _
    @Param(Array("50", "500"))
    var elemsPerChunk: Int = _
    var stream: Stream[IO, Json] = _

    @Setup(Level.Trial)
    def setup(): Unit = {
      val chunk = Chunk.vector(Vector.fill(Math.min(elemsPerChunk, elems))(obj))
      stream = Stream.chunk(chunk).repeatN((elems / chunk.size).toLong)
    }
  }
}
