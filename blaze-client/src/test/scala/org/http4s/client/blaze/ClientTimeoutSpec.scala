package org.http4s
package client
package blaze

import cats.effect._
import fs2.Stream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{SeqTestHead, SlowTestHead}
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

      def dataStream(n: Int): EntityBody[IO] = {
        val interval = 1000.millis
        Stream
          .awakeEvery[IO](interval)
          .map(_ => "1".toByte)
          .take(n.toLong)
      }

      val req = Request[IO](method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = mkConnection(requestKey = RequestKey.fromRequest(req))
      val (f, b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f, b).map(mkBuffer))
      val c = mkClient(h, tail)(requestTimeout = 1.second)

      c.fetchAs[String](req).unsafeRunSync() must throwA[TimeoutException]
    }

    "Idle timeout on slow POST body" in {

      def dataStream(n: Int): EntityBody[IO] = {
        val interval = 2.seconds
        Stream
          .awakeEvery[IO](interval)
          .map(_ => "1".toByte)
          .take(n.toLong)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = mkConnection(RequestKey.fromRequest(req))
      val (f, b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f, b).map(mkBuffer))
      val c = mkClient(h, tail)(idleTimeout = 1.second)

      c.fetchAs[String](req).unsafeRunSync() must throwA[TimeoutException]
    }

    "Not timeout on only marginally slow POST body" in {

      def dataStream(n: Int): EntityBody[IO] = {
        val interval = 100.millis
        Stream
          .awakeEvery[IO](interval)
          .map(_ => "1".toByte)
          .take(n.toLong)
      }

      val req = Request[IO](method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = mkConnection(RequestKey.fromRequest(req))
      val (f, b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f, b).map(mkBuffer))
      val c = mkClient(h, tail)(idleTimeout = 10.second, requestTimeout = 30.seconds)

      c.fetchAs[String](req).unsafeRunSync() must_== "done"
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
