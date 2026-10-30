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
import org.http4s.headers.`Accept-Encoding`
import org.http4s.server.middleware.GZip
import org.openjdk.jmh.annotations._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory
import scodec.bits.ByteVector

import java.util.concurrent.TimeUnit

// sbt "bench/Jmh/run -i 10 -wi 10 -f 2 -t 1 -prof gc org.http4s.bench.GZipBench"
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class GZipBench {
  import GZipBench._

  /** Runs the GZip middleware and drains the compressed body to a byte array. */
  @Benchmark
  def gzip(in: BenchState): Array[Byte] =
    in.app.run(in.req).flatMap(_.body.compile.to(Array)).unsafeRunSync()
}

object GZipBench {
  implicit private val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

  private val record =
    """{"id":"58fefc19a5eab74376285220","isActive":false,"balance":"$1,052.47","age":34,"name":"Calhoun Schneider","company":"GEEKMOSIS","email":"calhounschneider@geekmosis.com","tags":["laboris","ad","ullamco"]},"""

  @State(Scope.Benchmark)
  class BenchState {
    @Param(Array("1024", "4096", "65536"))
    var size: Int = _

    @Param(Array("strict", "streamed"))
    var entity: String = _

    var app: HttpApp[IO] = _
    val req: Request[IO] =
      Request[IO]().putHeaders(`Accept-Encoding`(ContentCoding.gzip))

    @Setup(Level.Trial)
    def setup(): Unit = {
      val json = ("[" + record * (size / record.length + 1)).take(size - 1) + "]"
      val bytes = ByteVector.view(json.getBytes("UTF-8"))
      val body: Entity[IO] = entity match {
        case "strict" => Entity.strict(bytes)
        case _ => Entity.stream(Stream.chunk(Chunk.byteVector(bytes)), Some(bytes.size))
      }
      val response = Response[IO](Status.Ok).withEntity(body)
      app = GZip(HttpApp[IO](_ => IO.pure(response)))
    }
  }
}
