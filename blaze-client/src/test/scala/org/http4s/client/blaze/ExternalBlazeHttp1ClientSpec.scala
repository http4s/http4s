package org.http4s.client.blaze

import scalaz.concurrent.Task

import org.http4s._
import org.http4s.client._
import org.http4s.Method._
import org.http4s.Status._
import org.specs2.mutable.After
import org.specs2.time.NoTimeConversions

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec with NoTimeConversions with After {

  "Blaze Simple Http1 Client" should {
    implicit def client = SimpleHttp1Client

    "Make simple http requests" in {
      val resp = uri("https://github.com/").on(Ok).as[String]
        .run
//      println(resp.copy(body = halt))

      resp.length mustNotEqual 0
    }

    "Make simple https requests" in {
      val resp = uri("https://github.com/").as[String]
        .run
//      println(resp.copy(body = halt))
//      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.length mustNotEqual 0
    }
  }

  implicit val client = new PooledHttp1Client()

  "RecyclingHttp1Client" should {

    "Make simple http requests" in {
      val resp = uri("https://github.com/").on(Ok).as[String]
        .run
      //      println(resp.copy(body = halt))

      resp.length mustNotEqual 0
    }

    "Repeat a simple http request" in {
      val f = (0 until 10).map(_ => Task.fork {
        val req = uri("https://github.com/")
        val resp = req.on(Status.Ok).as[String]
        resp.map(_.length)
      })

      foreach(Task.gatherUnordered(f).run) { length =>
        length mustNotEqual 0
      }
    }

    "Make simple https requests" in {
      val resp = uri("https://github.com/").as[String]
        .run
      //      println(resp.copy(body = halt))
      //      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.length mustNotEqual 0
    }
  }

  override def after = client.shutdown()
}
