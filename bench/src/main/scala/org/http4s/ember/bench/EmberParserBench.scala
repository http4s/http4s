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

package org.http4s.ember.bench

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.http4s._
import org.http4s.ember.core.Parser
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

// sbt "bench/Jmh/run -i 5 -wi 5 -f 1 -t 1 org.http4s.ember.bench.EmberParserBench"
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class EmberParserBench {
  import EmberParserBench._

  @Benchmark
  def parseRequest(in: BenchState): Request[IO] =
    Parser.Request
      .parser(in.maxHeaderSize)(in.reqBytes, in.read)
      .unsafeRunSync()
      ._1

  @Benchmark
  def parseResponse(in: BenchState): Response[IO] =
    Parser.Response
      .parser(in.maxHeaderSize)(in.respBytes, in.read)
      .unsafeRunSync()
      ._1
}

/*
[info] EmberParserBench.parseRequest     avgt   10  1658.321 ± 74.716  ns/op
[info] EmberParserBench.parseResponse    avgt   10  1080.508 ± 96.766  ns/op
[info] EmberParserBench.parseRequestNew  avgt   10  1339.713 ± 147.635  ns/op
[info] EmberParserBench.parseResponseNew avgt   10   863.105 ±  28.273  ns/op
 */

object EmberParserBench {

  @State(Scope.Benchmark)
  class BenchState {
    val maxHeaderSize: Int = 256 * 1024
    var req: Request[IO] = _
    var resp: Response[IO] = _
    var reqBytes: Array[Byte] = _
    var respBytes: Array[Byte] = _
    val read: IO[Option[Chunk[Byte]]] =
      IO.raiseError[Option[fs2.Chunk[Byte]]](new Throwable("Should Not Read in Bench"))

    @Setup(Level.Trial)
    def setup(): Unit = {
      req = Request[IO]().withEntity("Hello Bench!")
      resp = Response[IO]().withEntity("Hello Bench!")
      reqBytes = org.http4s.ember.core.Encoder.reqToBytes(req).compile.to(Array).unsafeRunSync()
      respBytes = org.http4s.ember.core.Encoder.respToBytes(resp).compile.to(Array).unsafeRunSync()

      println(s"Content-Length: ${req.contentLength}")
    }
  }
}
