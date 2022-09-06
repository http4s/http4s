/*
 * Copyright 2014 http4s.org
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
package blaze
package client

import cats.effect._
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.IdleTimeoutStage
import org.http4s.blazecore.QueueTestHead
import org.http4s.blazecore.SlowTestHead
import org.http4s.client.Client
import org.http4s.client.RequestKey
import org.http4s.syntax.all._

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class ClientTimeoutSuite extends Http4sSuite {

  override def munitTimeout: Duration = 5.seconds

  private def tickWheelFixture = ResourceFixture(
    Resource.make(IO(new TickWheelExecutor(tick = 50.millis)))(tickWheel =>
      IO(tickWheel.shutdown())
    )
  )

  private val www_foo_com = uri"http://www.foo.com"
  private val FooRequest = Request[IO](uri = www_foo_com)
  private val FooRequestKey = RequestKey.fromRequest(FooRequest)
  private val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"
  private val chunkBufferMaxSize = 1024 * 1024

  private def makeIdleTimeoutStage(
      idleTimeout: Duration,
      tickWheel: TickWheelExecutor,
  ): Option[IdleTimeoutStage[ByteBuffer]] =
    idleTimeout match {
      case d: FiniteDuration =>
        Some(new IdleTimeoutStage[ByteBuffer](d, tickWheel, Http4sSuite.TestExecutionContext))
      case _ => None
    }

  private def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  private def mkClient(
      head: => HeadStage[ByteBuffer],
      tickWheel: TickWheelExecutor,
  )(
      responseHeaderTimeout: Duration = Duration.Inf,
      requestTimeout: Duration = Duration.Inf,
      idleTimeout: Duration = Duration.Inf,
      retries: Int = 0,
  ): Client[IO] = {
    val manager = ConnectionManager.basic[IO, Http1Connection[IO]]((_: RequestKey) =>
      IO {
        val idleTimeoutStage = makeIdleTimeoutStage(idleTimeout, tickWheel)
        val connection = mkConnection(idleTimeoutStage)
        val builder = LeafBuilder(connection)
        idleTimeoutStage
          .fold(builder)(builder.prepend(_))
          .base(head)
        connection
      }
    )
    BlazeClient.makeClient(
      manager = manager,
      responseHeaderTimeout = responseHeaderTimeout,
      requestTimeout = requestTimeout,
      scheduler = tickWheel,
      ec = Http4sSuite.TestExecutionContext,
      retries = retries,
    )
  }

  private def mkConnection(
      idleTimeoutStage: Option[IdleTimeoutStage[ByteBuffer]]
  ): Http1Connection[IO] =
    new Http1Connection[IO](
      requestKey = FooRequestKey,
      executionContext = Http4sSuite.TestExecutionContext,
      maxResponseLineSize = 4 * 1024,
      maxHeaderLength = 40 * 1024,
      maxChunkSize = Int.MaxValue,
      chunkBufferMaxSize = chunkBufferMaxSize,
      parserMode = ParserMode.Strict,
      userAgent = None,
      idleTimeoutStage = idleTimeoutStage,
    )

  tickWheelFixture.test("Idle timeout on slow response") { tickWheel =>
    val h = new SlowTestHead(List(mkBuffer(resp)), 60.seconds, tickWheel)
    val c = mkClient(h, tickWheel)(idleTimeout = 1.second)

    c.fetchAs[String](FooRequest).intercept[TimeoutException]
  }

  tickWheelFixture.test("Request timeout on slow response") { tickWheel =>
    val h = new SlowTestHead(List(mkBuffer(resp)), 60.seconds, tickWheel)
    val c = mkClient(h, tickWheel)(requestTimeout = 1.second)

    c.fetchAs[String](FooRequest).intercept[TimeoutException]
  }

  tickWheelFixture.test("Idle timeout on slow request body before receiving response") {
    tickWheel =>
      // Sending request body hangs so the idle timeout will kick-in after 1s and interrupt the request
      val body = Stream.emit[IO, Byte](1.toByte) ++ Stream.never[IO]
      val req = Request(method = Method.POST, uri = www_foo_com, body = body)
      val h = new SlowTestHead(Seq(mkBuffer(resp)), 3.seconds, tickWheel)
      val c = mkClient(h, tickWheel)(idleTimeout = 1.second)

      c.fetchAs[String](req).intercept[TimeoutException]
  }

  tickWheelFixture.test("Idle timeout on slow request body while receiving response body") {
    tickWheel =>
      // Sending request body hangs so the idle timeout will kick-in after 1s and interrupt the request.
      // But with current implementation the cancellation of the request hangs (waits for the request body).
      (for {
        _ <- IO.unit
        body = Stream.emit[IO, Byte](1.toByte) ++ Stream.never[IO]
        req = Request(method = Method.POST, uri = www_foo_com, body = body)
        q <- Queue.unbounded[IO, Option[ByteBuffer]]
        h = new QueueTestHead(q)
        (f, b) = resp.splitAt(resp.length - 1)
        _ <- (q.enqueue1(Some(mkBuffer(f))) >> IO.sleep(3.seconds) >> q.enqueue1(
          Some(mkBuffer(b))
        )).start
        c = mkClient(h, tickWheel)(idleTimeout = 1.second)
        s <- c.fetchAs[String](req)
      } yield s).intercept[TimeoutException]
  }

  tickWheelFixture.test("Not timeout on only marginally slow request body".flaky) { tickWheel =>
    // Sending request body will take 1500ms. But there will be some activity every 500ms.
    // If the idle timeout wasn't reset every time something is sent, it would kick-in after 1 second.
    // The chunks need to be larger than the buffer in CachingChunkWriter
    val body = Stream
      .fixedRate[IO](500.millis)
      .take(3)
      .mapChunks(_ => Chunk.array(Array.fill(chunkBufferMaxSize + 1)(1.toByte)))
    val req = Request(method = Method.POST, uri = www_foo_com, body = body)
    val h = new SlowTestHead(Seq(mkBuffer(resp)), 2000.millis, tickWheel)
    val c = mkClient(h, tickWheel)(idleTimeout = 1.second)

    c.fetchAs[String](req)
  }

  tickWheelFixture.test("Request timeout on slow response body".flaky) { tickWheel =>
    val h = new SlowTestHead(Seq(mkBuffer(resp)), 1500.millis, tickWheel)
    val c = mkClient(h, tickWheel)(requestTimeout = 1.second, idleTimeout = 10.second)

    c.fetchAs[String](FooRequest).intercept[TimeoutException]
  }

  tickWheelFixture.test("Idle timeout on slow response body") { tickWheel =>
    val (f, b) = resp.splitAt(resp.length - 1)
    (for {
      q <- Queue.unbounded[IO, Option[ByteBuffer]]
      _ <- q.enqueue1(Some(mkBuffer(f)))
      _ <- (IO.sleep(1500.millis) >> q.enqueue1(Some(mkBuffer(b)))).start
      h = new QueueTestHead(q)
      c = mkClient(h, tickWheel)(idleTimeout = 500.millis)
      s <- c.fetchAs[String](FooRequest)
    } yield s).intercept[TimeoutException]
  }

  tickWheelFixture.test("Response head timeout on slow header") { tickWheel =>
    val h = new SlowTestHead(Seq(mkBuffer(resp)), 10.seconds, tickWheel)
    val c = mkClient(h, tickWheel)(responseHeaderTimeout = 500.millis)
    c.fetchAs[String](FooRequest).intercept[TimeoutException]
  }

  tickWheelFixture.test("No Response head timeout on fast header".flaky) { tickWheel =>
    val (f, b) = resp.splitAt(resp.indexOf("\r\n\r\n" + 4))
    val h = new SlowTestHead(Seq(f, b).map(mkBuffer), 125.millis, tickWheel)
    // header is split into two chunks, we wait for 10x
    val c = mkClient(h, tickWheel)(responseHeaderTimeout = 1250.millis)

    c.fetchAs[String](FooRequest).assertEquals("done")
  }

  // Regression test for: https://github.com/http4s/http4s/issues/2386
  // and https://github.com/http4s/http4s/issues/2338
  tickWheelFixture.test("Eventually timeout on connect timeout") { tickWheel =>
    val manager = ConnectionManager.basic[IO, BlazeConnection[IO]] { _ =>
      // In a real use case this timeout is under OS's control (AsynchronousSocketChannel.connect)
      IO.sleep(1000.millis) *> IO.raiseError[BlazeConnection[IO]](new IOException())
    }
    val c = BlazeClient.makeClient(
      manager = manager,
      responseHeaderTimeout = Duration.Inf,
      requestTimeout = 50.millis,
      scheduler = tickWheel,
      ec = munitExecutionContext,
      retries = 0,
    )

    // if the .timeout(1500.millis) is hit, it's a TimeoutException,
    // if the requestTimeout is hit then it's a TimeoutException
    // if establishing connection fails first then it's an IOException

    // The expected behaviour is that the requestTimeout will happen first,
    // but will not be considered as long as BlazeClient is busy trying to obtain the connection.
    // Obtaining the connection will fail after 1000 millis and that error will be propagated.
    c.fetchAs[String](FooRequest).timeout(1500.millis).intercept[IOException]
  }
}
