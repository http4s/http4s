package org.http4s.client.blaze

import scalaz.concurrent.Task

import org.http4s._

import org.specs2.mutable.After

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec {

  private val simpleClient = SimpleHttp1Client()

  "Blaze Simple Http1 Client" should {
    "Make simple https requests" in {
      val resp = simpleClient.expect[String](uri("https://github.com/")).run
      resp.length mustNotEqual 0
    }
  }

  step {
    simpleClient.shutdown.run
  }

  private val pooledClient = PooledHttp1Client()

  "RecyclingHttp1Client" should {
    def fetchBody = pooledClient.toService(_.as[String]).local { uri: Uri => Request(uri = uri) }

    "Make simple https requests" in {
      val resp = fetchBody.run(uri("https://github.com/")).run
      resp.length mustNotEqual 0
    }

    "Repeat a simple https request" in {
      val f = (0 until 10).map(_ => Task.fork {
        val resp = fetchBody.run(uri("https://github.com/"))
        resp.map(_.length)
      })

      foreach(Task.gatherUnordered(f).run) { length =>
        length mustNotEqual 0
      }
    }
  }

  step {
    pooledClient.shutdown.run
  }
}
