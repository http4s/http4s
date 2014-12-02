package org.http4s.client.blaze

import scalaz.concurrent.Task

import org.http4s._
import org.http4s.client._
import org.specs2.mutable.After
import org.specs2.time.NoTimeConversions

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec with NoTimeConversions with After {

  "Blaze Simple Http1 Client" should {
    implicit def client = SimpleHttp1Client

    "Make simple http requests" in {
      val resp = Request(GET, uri("https://github.com/")).on(Status.Ok)(EntityDecoder.text).run
//      println(resp.copy(body = halt))

      resp.status.code must be_==(200)
    }

    "Make simple https requests" in {
      val resp = Request(GET, uri("https://github.com/")).on(Status.Ok)(EntityDecoder.text).run
//      println(resp.copy(body = halt))
//      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.status.code must be_==(200)
    }
  }

  implicit val client = new PooledHttp1Client()

  "RecyclingHttp1Client" should {

    "Make simple http requests" in {
      val resp = Request(GET, uri("https://github.com/")).on(Status.Ok)(EntityDecoder.text).run
      //      println(resp.copy(body = halt))

      resp.status.code must_==(200)
    }

    "Repeat a simple http request" in {
      val f = (0 until 10).map(_ => Task.fork {
        val req = Request(GET, uri("https://github.com/"))
        val resp = req.on(Status.Ok)(EntityDecoder.text)
        resp.map(_.status)
      })
      foreach(Task.gatherUnordered(f).run) { status =>
        status.code must_== 200
      }
    }

    "Make simple https requests" in {
      val resp = Request(GET, uri("https://github.com/")).on(Status.Ok)(EntityDecoder.text).run
      //      println(resp.copy(body = halt))
      //      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.status.code must be_==(200)
    }
  }

  override def after = client.shutdown()
}
