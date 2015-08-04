package org.http4s.client.blaze

import scalaz.concurrent.Task

import org.http4s._

import org.specs2.mutable.After

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec with After {

  "Blaze Simple Http1 Client" should {
    def client = defaultClient

    "Make simple http requests" in {
      val resp = client(uri("https://github.com/")).as[String].run
//      println(resp.copy(body = halt))

      resp.length mustNotEqual 0
    }

    "Make simple https requests" in {
      val resp = client(uri("https://github.com/")).as[String].run
//      println(resp.copy(body = halt))
//      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.length mustNotEqual 0
    }
  }

  val client = PooledHttp1Client()

  "RecyclingHttp1Client" should {

    "Make simple http requests" in {
      val resp = client(uri("https://github.com/")).as[String].run
      //      println(resp.copy(body = halt))

      resp.length mustNotEqual 0
    }

    "Repeat a simple http request" in {
      val f = (0 until 10).map(_ => Task.fork {
        val req = uri("https://github.com/")
        val resp = client(req).as[String]
        resp.map(_.length)
      })

      foreach(Task.gatherUnordered(f).run) { length =>
        length mustNotEqual 0
      }
    }

    "Make simple https requests" in {
      val resp = client(uri("https://github.com/")).as[String].run
      //      println(resp.copy(body = halt))
      //      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.length mustNotEqual 0
    }
  }

  override def after = client.shutdown()
}
