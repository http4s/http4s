package org.http4s.client.blaze

import scalaz.concurrent.Task
import scalaz.stream.Process

import org.http4s._

import org.specs2.mutable.After
import org.specs2.time.NoTimeConversions

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec with NoTimeConversions with After {

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

    "Treat a streaming http request as if it were not streaming" in {
      val resp = client(uri("http://httpize.herokuapp.com/stream/10")).as[String].run


      resp.count('T' == _) mustEqual 10
    }

    "Make chunked http request" in {
      val resp = client(uri("http://httpize.herokuapp.com/stream/50")).as[Process[Task, String]].run

      val result = resp.runLog.run

      result.mkString.count('T' == _) mustEqual 50
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
