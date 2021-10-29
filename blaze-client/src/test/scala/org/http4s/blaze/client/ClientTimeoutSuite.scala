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
import cats.effect.concurrent.Deferred
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.IdleTimeoutStage
import org.http4s.blazecore.QueueTestHead
import org.http4s.blazecore.SeqTestHead
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

  def tickWheelFixture = ResourceFixture(
    Resource.make(IO(new TickWheelExecutor(tick = 50.millis)))(tickWheel =>
      IO(tickWheel.shutdown())))

  val www_foo_com = uri"http://www.foo.com"
  val FooRequest = Request[IO](uri = www_foo_com)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  private def mkConnection(
      requestKey: RequestKey,
      tickWheel: TickWheelExecutor,
      idleTimeout: Duration = Duration.Inf): Http1Connection[IO] = {
    val idleTimeoutStage = makeIdleTimeoutStage(idleTimeout, tickWheel)

    val connection = new Http1Connection[IO](
      requestKey = requestKey,
      executionContext = Http4sSuite.TestExecutionContext,
      maxResponseLineSize = 4 * 1024,
      maxHeaderLength = 40 * 1024,
      maxChunkSize = Int.MaxValue,
      chunkBufferMaxSize = 1024 * 1024,
      parserMode = ParserMode.Strict,
      userAgent = None,
      idleTimeoutStage = idleTimeoutStage
    )

    val builder = LeafBuilder(connection)
    idleTimeoutStage.fold(builder)(builder.prepend(_))
    connection
  }

  private def makeIdleTimeoutStage(
      idleTimeout: Duration,
      tickWheel: TickWheelExecutor): Option[IdleTimeoutStage[ByteBuffer]] =
    idleTimeout match {
      case d: FiniteDuration =>
        Some(new IdleTimeoutStage[ByteBuffer](d, tickWheel, Http4sSuite.TestExecutionContext))
      case _ => None
    }

  private def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  private def mkClient(
      head: => HeadStage[ByteBuffer],
      tail: => BlazeConnection[IO],
      tickWheel: TickWheelExecutor)(
      responseHeaderTimeout: Duration = Duration.Inf,
      requestTimeout: Duration = Duration.Inf): Client[IO] = {
    val manager = MockClientBuilder.manager(head, tail)
    BlazeClient.makeClient(
      manager = manager,
      responseHeaderTimeout = responseHeaderTimeout,
      requestTimeout = requestTimeout,
      scheduler = tickWheel,
      ec = Http4sSuite.TestExecutionContext
    )
  }

  tickWheelFixture.test("Idle timeout on slow response") { tickWheel =>
    val tail = mkConnection(FooRequestKey, tickWheel, idleTimeout = 1.second)
    val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds, tickWheel)
    val c = mkClient(h, tail, tickWheel)()

    c.fetchAs[String](FooRequest).intercept[TimeoutException]
  }

  tickWheelFixture.test("Request timeout on slow response") { tickWheel =>
    val tail = mkConnection(FooRequestKey, tickWheel)
    val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds, tickWheel)
    val c = mkClient(h, tail, tickWheel)(requestTimeout = 1.second)

    c.fetchAs[String](FooRequest).intercept[TimeoutException]
  }

  tickWheelFixture.test("Idle timeout on slow POST body") { tickWheel =>
    (for {
      d <- Deferred[IO, Unit]
      body =
        Stream
          .awakeEvery[IO](2.seconds)
          .map(_ => "1".toByte)
          .take(4)
          .onFinalizeWeak[IO](d.complete(()).void)
      req = Request(method = Method.POST, uri = www_foo_com, body = body)
      tail = mkConnection(RequestKey.fromRequest(req), tickWheel, idleTimeout = 1.second)
      q <- Queue.unbounded[IO, Option[ByteBuffer]]
      h = new QueueTestHead(q)
      (f, b) = resp.splitAt(resp.length - 1)
      _ <- (q.enqueue1(Some(mkBuffer(f))) >> d.get >> q.enqueue1(Some(mkBuffer(b)))).start
      c = mkClient(h, tail, tickWheel)()
      s <- c.fetchAs[String](req)
    } yield s).intercept[TimeoutException]
  }

  tickWheelFixture.test("Not timeout on only marginally slow POST body".flaky) { tickWheel =>
    def dataStream(n: Int): EntityBody[IO] = {
      val interval = 100.millis
      Stream
        .awakeEvery[IO](interval)
        .map(_ => "1".toByte)
        .take(n.toLong)
    }

    val req = Request[IO](method = Method.POST, uri = www_foo_com, body = dataStream(4))

    val tail = mkConnection(RequestKey.fromRequest(req), tickWheel, idleTimeout = 10.second)
    val (f, b) = resp.splitAt(resp.length - 1)
    val h = new SeqTestHead(Seq(f, b).map(mkBuffer))
    val c = mkClient(h, tail, tickWheel)(requestTimeout = 30.seconds)

    c.fetchAs[String](req).assertEquals("done")
  }

  tickWheelFixture.test("Request timeout on slow response body".flaky) { tickWheel =>
    val tail = mkConnection(FooRequestKey, tickWheel, idleTimeout = 10.second)
    val (f, b) = resp.splitAt(resp.length - 1)
    val h = new SlowTestHead(Seq(f, b).map(mkBuffer), 1500.millis, tickWheel)
    val c = mkClient(h, tail, tickWheel)(requestTimeout = 1.second)

    c.fetchAs[String](FooRequest).intercept[TimeoutException]
  }

  tickWheelFixture.test("Idle timeout on slow response body") { tickWheel =>
    val tail = mkConnection(FooRequestKey, tickWheel, idleTimeout = 500.millis)
    val (f, b) = resp.splitAt(resp.length - 1)
    (for {
      q <- Queue.unbounded[IO, Option[ByteBuffer]]
      _ <- q.enqueue1(Some(mkBuffer(f)))
      _ <- (IO.sleep(1500.millis) >> q.enqueue1(Some(mkBuffer(b)))).start
      h = new QueueTestHead(q)
      c = mkClient(h, tail, tickWheel)()
      s <- c.fetchAs[String](FooRequest)
    } yield s).intercept[TimeoutException]
  }

  tickWheelFixture.test("Response head timeout on slow header") { tickWheel =>
    val tail = mkConnection(FooRequestKey, tickWheel)
    (for {
      q <- Queue.unbounded[IO, Option[ByteBuffer]]
      _ <- (IO.sleep(10.seconds) >> q.enqueue1(Some(mkBuffer(resp)))).start
      h = new QueueTestHead(q)
      c = mkClient(h, tail, tickWheel)(responseHeaderTimeout = 500.millis)
      s <- c.fetchAs[String](FooRequest)
    } yield s).intercept[TimeoutException]
  }

  tickWheelFixture.test("No Response head timeout on fast header".flaky) { tickWheel =>
    val tail = mkConnection(FooRequestKey, tickWheel)
    val (f, b) = resp.splitAt(resp.indexOf("\r\n\r\n" + 4))
    val h = new SlowTestHead(Seq(f, b).map(mkBuffer), 125.millis, tickWheel)
    // header is split into two chunks, we wait for 10x
    val c = mkClient(h, tail, tickWheel)(responseHeaderTimeout = 1250.millis)

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
      ec = munitExecutionContext
    )

    // if the unsafeRunTimed timeout is hit, it's a NoSuchElementException,
    // if the requestTimeout is hit then it's a TimeoutException
    // if establishing connection fails first then it's an IOException

    // The expected behaviour is that the requestTimeout will happen first, but fetchAs will additionally wait for the IO.sleep(1000.millis) to complete.
    c.fetchAs[String](FooRequest).timeout(1500.millis).intercept[TimeoutException]
  }
}
