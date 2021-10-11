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

import cats.implicits._
import fs2.Chunk
import java.nio.charset.StandardCharsets
import org.openjdk.jmh.annotations._
import org.http4s.util._

@Threads(1)
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
class RenderBench {
  import RenderBench._

  @Benchmark
  def imperativeString = {
    val sw = new StringWriter(256)
    sw << resp.httpVersion << " " << resp.status << "\r\n"
    resp.headers.foreach { h =>
      sw << h << "\r\n"
    }
    sw << "\r\n"
    Chunk.array(sw.result.getBytes(StandardCharsets.US_ASCII)).toByteBuffer
  }

  @Benchmark
  def http1Encoder = ResponsePrelude.http1Codec.encode(resp).toByteBuffer
}

object RenderBench {
  val resp = ResponsePrelude(
    httpVersion = HttpVersion.`HTTP/1.1`,
    status = Status.Ok,
    headers = Headers(
      "Access-Control-Allow-Credentials" -> "true",
      "Access-Control-Allow-Origin" -> "*",
      "Connection" -> "keep-alive",
      "Content-Length" -> "9593",
      "Content-Type" -> "text/html; charset=utf-8",
      "Date" -> "Sun, 10 Oct 2021 03:34:47 GMT",
      "Server" -> "gunicorn/19.9.0"
    )
  )
}
