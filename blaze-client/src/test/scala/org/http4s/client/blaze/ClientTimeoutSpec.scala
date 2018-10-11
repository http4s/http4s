package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Queue
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{QueueTestHead, SlowTestHead}
import org.specs2.specification.core.Fragments
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class ClientTimeoutSpec extends Http4sSpec {

  val tickWheel = new TickWheelExecutor

  /** the map method allows to "post-process" the fragments after their creation */
  override def map(fs: => Fragments) = super.map(fs) ^ step(tickWheel.shutdown())

  val www_foo_com = Uri.uri("http://www.foo.com")
  val FooRequest = Request[IO](uri = www_foo_com)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  private def mkConnection(requestKey: RequestKey): Http1Connection[IO] =
    new Http1Connection(
      requestKey = requestKey,
      executionContext = testExecutionContext,
      maxResponseLineSize = 4 * 1024,
      maxHeaderLength = 40 * 1024,
      maxChunkSize = Int.MaxValue,
      parserMode = ParserMode.Strict,
      userAgent = None
    )

  private def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  private def mkClient(head: => HeadStage[ByteBuffer], tail: => BlazeConnection[IO])(
      responseHeaderTimeout: Duration = Duration.Inf,
      idleTimeout: Duration = Duration.Inf,
      requestTimeout: Duration = Duration.Inf): Client[IO] = {
    val manager = MockClientBuilder.manager(head, tail)
    BlazeClient.makeClient(
      manager = manager,
      responseHeaderTimeout = responseHeaderTimeout,
      idleTimeout = idleTimeout,
      requestTimeout = requestTimeout
    )
  }

  "Http1ClientStage responses" should {
    "Timeout immediately with an idle timeout of 0 seconds" in {
      val c = mkClient(
        new SlowTestHead(List(mkBuffer(resp)), 0.seconds, tickWheel),
        mkConnection(FooRequestKey))(idleTimeout = Duration.Zero)

      c.fetchAs[String](FooRequest).unsafeRunSync() must throwA[TimeoutException]
    }

    "Timeout immediately with a request timeout of 0 seconds" in {
      val tail = mkConnection(FooRequestKey)
      val h = new SlowTestHead(List(mkBuffer(resp)), 0.seconds, tickWheel)
      val c = mkClient(h, tail)(requestTimeout = 0.milli)

      c.fetchAs[String](FooRequest).unsafeRunSync() must throwA[TimeoutException]
    }

    "Idle timeout on slow response" in {
      val tail = mkConnection(FooRequestKey)
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds, tickWheel)
      val c = mkClient(h, tail)(idleTimeout = 1.second)

      c.fetchAs[String](FooRequest).unsafeRunSync() must throwA[TimeoutException]
    }

    "Request timeout on slow response" in {
      val tail = mkConnection(FooRequestKey)
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds, tickWheel)
      val c = mkClient(h, tail)(requestTimeout = 1.second)

      c.fetchAs[String](FooRequest).unsafeRunSync() must throwA[TimeoutException]
    }

    "Request timeout on slow POST body" in {
      Queue
        .unbounded[IO, Option[ByteBuffer]]
        .flatMap { q =>
          for {
            _ <- q.enqueue1(Some(mkBuffer("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n")))
            req = Request[IO](method = Method.POST, uri = www_foo_com, body = Stream.never[IO])
            tail = mkConnection(RequestKey.fromRequest(req))
            h = new QueueTestHead(q)
            c = mkClient(h, tail)(idleTimeout = 1.second)
            s <- c.fetchAs[String](req)
          } yield s
        }
        .attempt
        .unsafeRunTimed(5.seconds) must beSome(beLeft(anInstanceOf[TimeoutException]))
    }

    "Idle timeout on slow POST body" in {
      Queue
        .unbounded[IO, Option[ByteBuffer]]
        .flatMap { q =>
          for {
            _ <- q.enqueue1(Some(mkBuffer("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n")))
            req = Request[IO](method = Method.POST, uri = www_foo_com, body = Stream.never[IO])
            tail = mkConnection(RequestKey.fromRequest(req))
            h = new QueueTestHead(q)
            c = mkClient(h, tail)(idleTimeout = 1.second)
            s <- c.fetchAs[String](req)
          } yield s
        }
        .attempt
        .unsafeRunTimed(5.seconds) must beSome(beLeft(anInstanceOf[TimeoutException]))
    }

    "Not timeout on only marginally slow POST body" in {
      Queue
        .unbounded[IO, Option[ByteBuffer]]
        .flatMap { q =>
          for {
            _ <- q.enqueue1(Some(mkBuffer("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n")))
            body = Stream
              .emits("done".toList)
              .evalMap(c => timer.sleep(100.millis).as(c.toByte))
              .evalTap(b => q.enqueue1(Some(ByteBuffer.wrap(Array(b)))))
              .onFinalize(q.enqueue1(None))
            req = Request[IO](method = Method.POST, uri = www_foo_com, body = body)
            tail = mkConnection(RequestKey.fromRequest(req))
            h = new QueueTestHead(q)
            c = mkClient(h, tail)(idleTimeout = 1.second)
            s <- c.fetchAs[String](req)
          } yield s
        }
        .unsafeRunTimed(5.seconds) must_== Some("done")
    }

    "Request timeout on slow response body" in {
      val tail = mkConnection(FooRequestKey)
      val (f, b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f, b).map(mkBuffer), 1500.millis, tickWheel)
      val c = mkClient(h, tail)(requestTimeout = 1.second)

      tail.runRequest(FooRequest).flatMap(_.as[String])
      c.fetchAs[String](FooRequest).unsafeRunSync() must throwA[TimeoutException]
    }

    "Idle timeout on slow response body" in {
      val tail = mkConnection(FooRequestKey)
      val (f, b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f, b).map(mkBuffer), 1500.millis, tickWheel)
      val c = mkClient(h, tail)(idleTimeout = 1.second)

      tail.runRequest(FooRequest).flatMap(_.as[String])
      c.fetchAs[String](FooRequest).unsafeRunSync() must throwA[TimeoutException]
    }

    "Response head timeout on slow header" in {
      val tail = mkConnection(FooRequestKey)
      val (f, b) = resp.splitAt(resp.indexOf("\r\n\r\n"))
      val h = new SlowTestHead(Seq(f, b).map(mkBuffer), 500.millis, tickWheel)
      // header is split into two chunks, we wait for 1.5x
      val c = mkClient(h, tail)(responseHeaderTimeout = 750.millis)

      c.fetchAs[String](FooRequest).unsafeRunSync must throwA[TimeoutException]
    }

    "No Response head timeout on fast header" in {
      val tail = mkConnection(FooRequestKey)
      val (f, b) = resp.splitAt(resp.indexOf("\r\n\r\n" + 4))
      val h = new SlowTestHead(Seq(f, b).map(mkBuffer), 125.millis, tickWheel)
      // header is split into two chunks, we wait for 10x
      val c = mkClient(h, tail)(responseHeaderTimeout = 1250.millis)

      tail.runRequest(FooRequest).flatMap(_.as[String])
      c.fetchAs[String](FooRequest).unsafeRunSync must_== "done"
    }
  }
}
