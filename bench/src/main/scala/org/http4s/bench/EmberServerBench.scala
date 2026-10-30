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

package org.http4s.bench

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s._
import org.http4s._
import org.http4s.ember.server.EmberServerBuilder
import org.openjdk.jmh.annotations._

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

/** Keep-alive request/response round trips against an Ember server on loopback.
  *
  * Each JMH thread is one keep-alive connection, so use `-t` to set the number of
  * concurrent connections. Use `-prof gc` for the allocation per request, which includes
  * the allocation of the server.
  *
  * The `timeouts` parameter is either the default timeouts of Ember (60 s idle timeout,
  * 5 s request header receive timeout), or infinite ones.
  */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(8)
class EmberServerBench {
  import EmberServerBench._

  @Benchmark
  def roundTrip(connection: ConnectionState): Int =
    connection.roundTrip()
}

object EmberServerBench {

  @State(Scope.Benchmark)
  class ServerState {
    @Param(Array("default", "infinite"))
    var timeouts: String = _

    var port: Int = _
    private var shutdown: IO[Unit] = _

    @Setup(Level.Trial)
    def setup(): Unit = {
      val app = HttpApp[IO](_ => IO.pure(Response[IO]().withEntity("ok")))
      val builder = EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(app)
        .withShutdownTimeout(1.second)
      val withTimeouts = timeouts match {
        case "default" => builder
        case "infinite" =>
          builder.withIdleTimeout(Duration.Inf).withRequestHeaderReceiveTimeout(Duration.Inf)
        case other => sys.error(s"unknown timeouts: $other")
      }
      val (server, release) = withTimeouts.build.allocated.unsafeRunSync()
      port = server.addressIp4s.port.value
      shutdown = release
    }

    @TearDown(Level.Trial)
    def tearDown(): Unit =
      shutdown.unsafeRunSync()
  }

  @State(Scope.Thread)
  class ConnectionState {
    private val request =
      "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
    private val buffer = new Array[Byte](4096)
    private var socket: Socket = _
    private var in: InputStream = _
    private var out: OutputStream = _

    @Setup(Level.Trial)
    def setup(server: ServerState): Unit = {
      socket = new Socket("127.0.0.1", server.port)
      socket.setTcpNoDelay(true)
      in = socket.getInputStream
      out = socket.getOutputStream
    }

    @TearDown(Level.Trial)
    def tearDown(): Unit =
      socket.close()

    /** Sends a request and reads the whole response, returns the size of the response. */
    def roundTrip(): Int = {
      out.write(request)
      out.flush()
      var length = 0
      var headerEnd = -1
      var total = -1
      while (total < 0 || length < total) {
        val n = in.read(buffer, length, buffer.length - length)
        if (n < 0) throw new IllegalStateException("connection closed")
        length += n
        if (headerEnd < 0) {
          headerEnd = indexOfHeaderEnd(buffer, length)
          if (headerEnd >= 0) total = headerEnd + contentLength(buffer, headerEnd)
        }
      }
      length
    }
  }

  private def indexOfHeaderEnd(buffer: Array[Byte], length: Int): Int = {
    var i = 3
    var end = -1
    while (end < 0 && i < length) {
      if (
        buffer(i) == '\n' && buffer(i - 1) == '\r' && buffer(i - 2) == '\n' && buffer(i - 3) == '\r'
      ) end = i + 1
      i += 1
    }
    end
  }

  private def contentLength(buffer: Array[Byte], headerEnd: Int): Int = {
    val headers = new String(buffer, 0, headerEnd, StandardCharsets.US_ASCII).toLowerCase
    val start = headers.indexOf("content-length:") + "content-length:".length
    headers.substring(start, headers.indexOf('\r', start)).trim.toInt
  }
}
