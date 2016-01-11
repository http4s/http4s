package org.http4s
package client
package blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.http4s.blaze.{SeqTestHead, SlowTestHead}
import org.http4s.blaze.pipeline.{HeadStage, LeafBuilder}
import scodec.bits.ByteVector

import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.concurrent.Strategy.DefaultTimeoutScheduler
import scalaz.stream.Process
import scalaz.stream.time

class ClientTimeoutSpec extends Http4sSpec {

  val ec = scala.concurrent.ExecutionContext.global
  val www_foo_com = Uri.uri("http://www.foo.com")
  val FooRequest = Request(uri = www_foo_com)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))
  
  def mkClient(head: => HeadStage[ByteBuffer], tail: => BlazeConnection)
              (idleTimeout: Duration, requestTimeout: Duration): Client = {
    val manager = MockClientBuilder.manager(head, tail)
    BlazeClient(manager, idleTimeout, requestTimeout)
  }

  "Http1ClientStage responses" should {
    "Timeout immediately with an idle timeout of 0 seconds" in {
      val c = mkClient(new SlowTestHead(List(mkBuffer(resp)), 0.seconds), 
                       new Http1Connection(FooRequestKey, None, ec))(0.milli, Duration.Inf)

      c.fetchAs[String](FooRequest).run must throwA[TimeoutException]
    }

    "Timeout immediately with a request timeout of 0 seconds" in {
      val tail = new Http1Connection(FooRequestKey, None, ec)
      val h = new SlowTestHead(List(mkBuffer(resp)), 0.seconds)
      val c = mkClient(h, tail)(Duration.Inf, 0.milli)

      c.fetchAs[String](FooRequest).run must throwA[TimeoutException]
    }

    "Idle timeout on slow response" in {
      val tail = new Http1Connection(FooRequestKey, None, ec)
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds)
      val c = mkClient(h, tail)(1.second, Duration.Inf)

      c.fetchAs[String](FooRequest).run must throwA[TimeoutException]
    }

    "Request timeout on slow response" in {
      val tail = new Http1Connection(FooRequestKey, None, ec)
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds)
      val c = mkClient(h, tail)(Duration.Inf, 1.second)

      c.fetchAs[String](FooRequest).run must throwA[TimeoutException]
    }

    "Request timeout on slow POST body" in {

      def dataStream(n: Int): Process[Task, ByteVector] = {
        implicit def defaultSecheduler = DefaultTimeoutScheduler
        val interval = 1000.millis
        time.awakeEvery(interval)
          .map(_ => ByteVector.empty)
          .take(n)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = new Http1Connection(RequestKey.fromRequest(req), None, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f,b).map(mkBuffer))
      val c = mkClient(h, tail)(Duration.Inf, 1.second)

      c.fetchAs[String](req).run must throwA[TimeoutException]
    }

    "Idle timeout on slow POST body" in {

      def dataStream(n: Int): Process[Task, ByteVector] = {
        implicit def defaultSecheduler = DefaultTimeoutScheduler
        val interval = 2.seconds
        time.awakeEvery(interval)
          .map(_ => ByteVector.empty)
          .take(n)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = new Http1Connection(RequestKey.fromRequest(req), None, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f,b).map(mkBuffer))
      val c = mkClient(h, tail)(1.second, Duration.Inf)

      c.fetchAs[String](req).run must throwA[TimeoutException]
    }

    "Not timeout on only marginally slow POST body" in {

      def dataStream(n: Int): Process[Task, ByteVector] = {
        implicit def defaultSecheduler = DefaultTimeoutScheduler
        val interval = 100.millis
        time.awakeEvery(interval)
          .map(_ => ByteVector.empty)
          .take(n)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = new Http1Connection(RequestKey.fromRequest(req), None, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f,b).map(mkBuffer))
      val c = mkClient(h, tail)(10.second, 30.seconds)

      c.fetchAs[String](req).run must_== ("done")
    }

    "Request timeout on slow response body" in {
      val tail = new Http1Connection(FooRequestKey, None, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f,b).map(mkBuffer), 1500.millis)
      val c = mkClient(h, tail)(Duration.Inf, 1.second)

      val result = tail.runRequest(FooRequest, false).as[String]

      c.fetchAs[String](FooRequest).run must throwA[TimeoutException]
    }

    "Idle timeout on slow response body" in {
      val tail = new Http1Connection(FooRequestKey, None, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f,b).map(mkBuffer), 1500.millis)
      val c = mkClient(h, tail)(1.second, Duration.Inf)

      val result = tail.runRequest(FooRequest, false).as[String]

      c.fetchAs[String](FooRequest).run must throwA[TimeoutException]
    }
  }
}
