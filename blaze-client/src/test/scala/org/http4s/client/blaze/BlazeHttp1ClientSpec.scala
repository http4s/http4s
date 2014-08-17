package org.http4s.client.blaze

import scalaz.concurrent.Task

import org.http4s.Method._
import org.http4s._
import org.http4s.client.ClientSyntax
import org.specs2.mutable.After
import org.specs2.time.NoTimeConversions

import scala.concurrent.duration._

class BlazeHttp1ClientSpec extends Http4sSpec with NoTimeConversions with After {

  "Blaze Simple Http1 Client" should {
    implicit def client = SimpleHttp1Client

    "Make simple http requests" in {
      val resp = Request(GET, uri("http://www.google.com/")).on(Status.Ok)(EntityDecoder.text).run
//      println(resp.copy(body = halt))

      resp.status.code must be_==(200)
    }

    "Make simple https requests" in {
      val resp = Request(GET, uri("https://www.google.com/")).on(Status.Ok)(EntityDecoder.text).run
//      println(resp.copy(body = halt))
//      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.status.code must be_==(200)
    }
  }

  implicit val client = new PooledHttp1Client()

  "RecyclingHttp1Client" should {

    "Mate simple http requests" in {
      val resp = Request(GET, uri("http://www.google.com/")).on(Status.Ok)(EntityDecoder.text).run
      //      println(resp.copy(body = halt))

      resp.status.code must be_==(200)
    }

    "Repeat a simple http request" in {
      val f = (0 until 10).map(_ => Task.fork {
        val req = Request(GET, uri("http://www.google.com/"))
        val resp = req.on(Status.Ok)(EntityDecoder.text)
        resp.map(_.status)
      })
      foreach(Task.gatherUnordered(f).run) { status =>
        status.code must_== 200
      }
    }

    "Make simple https requests" in {
      val resp = Request(GET, uri("https://www.google.com/")).on(Status.Ok)(EntityDecoder.text).run
      //      println(resp.copy(body = halt))
      //      println("Body -------------------------\n" + gatherBody(resp.body) + "\n--------------------------")
      resp.status.code must be_==(200)
    }
  }

  override def after = client.shutdown()
}
