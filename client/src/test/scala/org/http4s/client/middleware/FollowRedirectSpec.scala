package org.http4s
package client
package middleware

import java.util.concurrent.atomic._

import org.http4s.dsl._
import org.http4s.headers._
import scalaz.concurrent._

class FollowRedirectSpec extends Http4sSpec {

  private val loopCounter = new AtomicInteger(0)

  val service = HttpService {
    case GET -> Root / "ok" =>
      Response(Ok).withBody("hello")
    case GET -> Root / "redirect" =>
      Response(MovedPermanently).replaceAllHeaders(Location(uri("/ok"))).withBody("Go there.")
    case GET -> Root / "loop" =>
      Response(MovedPermanently).replaceAllHeaders(Location(uri("/loop"))).withBody(loopCounter.incrementAndGet.toString)
    case POST -> Root / "303" => 
      Response(SeeOther).replaceAllHeaders(Location(uri("/ok"))).withBody("Go to /ok")
  }


  val defaultClient = MockClient(service)
  val client = FollowRedirect(3)(defaultClient)
  
  "FollowRedirect" should {
    "Honor redirect" in {
      client.expect[String](GET(uri("http://localhost/redirect"))).run must_== "hello"
    }

    "Not redirect more than 'maxRedirects' iterations" in {
      client.fetch(GET(uri("http://localhost/loop"))) {
        case MovedPermanently(resp) =>
          println("Reading")
          resp.as[String].map(_.toInt)
        case _ => Task.now(-1)
      }.run must_==(4)
    }

    "Use a GET method on redirect with 303 response code" in {
      client.expect[String](POST(uri("http://localhost/303"))).run must_== "hello"
    }
  }
}
