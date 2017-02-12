package org.http4s.client.blaze

import scala.concurrent.duration._
import fs2._

import org.http4s._

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec {
  private val timeout = 30.seconds

  implicit val strategy = Strategy.fromExecutionContext(scala.concurrent.ExecutionContext.global)

  private val simpleClient = SimpleHttp1Client()

  "Blaze Simple Http1 Client" should {
    "Make simple https requests" in {
      val resp = simpleClient.expect[String](uri("https://github.com/")).unsafeRunFor(timeout)
      resp.length mustNotEqual 0
    }
  }

  step {
    simpleClient.shutdown.unsafeRun()
  }

  private val pooledClient = PooledHttp1Client()

  "RecyclingHttp1Client" should {
    def fetchBody = pooledClient.toService(_.as[String]).local { uri: Uri => Request(uri = uri) }

    "Make simple https requests" in {
      val resp = fetchBody.run(uri("https://github.com/")).unsafeRunFor(timeout)
      resp.length mustNotEqual 0
    }

    "Repeat a simple https request" in {
      val amtRequests = 10
      val f: Seq[Task[Int]] = List.fill(amtRequests)(()).map { _ =>
        val resp = fetchBody.run(uri("https://github.com/"))
        resp.map(_.length)
      }
      foreach(f){ _.unsafeRunFor(timeout) mustNotEqual 0 }
    }
  }

  step {
    pooledClient.shutdown.unsafeRun()
  }
}
