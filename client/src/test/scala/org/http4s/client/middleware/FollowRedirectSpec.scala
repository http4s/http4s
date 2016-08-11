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
    case method -> Root / "ok" =>
      Ok(method.toString)
    case req @ _ -> Root / "301" =>
      MovedPermanently(uri("/ok"))
    case _ -> Root / "302" =>
      Found(uri("/ok"))
    case _ -> Root / "303" =>
      SeeOther(uri("/ok"))
    case _ -> Root / "307" =>
      TemporaryRedirect(uri("/ok"))
    case _ -> Root / "308" =>
      PermanentRedirect(uri("/ok"))
  }

  val defaultClient = MockClient(service)
  val client = FollowRedirect(3)(defaultClient)

  def expectString(method: Method, path: String): Task[String] = {
    val u = uri("http://localhost") / path
    val req = method match {
      case _: Method with Method.PermitsBody =>
        Request(method, u).withBody("foo")
      case _ =>
        Task.now(Request(method, u))
    }
    client.expect[String](req)
  }

  "FollowRedirect" should {
    "GET after 301 response to GET" in {
      expectString(GET, "301").run must_== "GET"
    }
    "GET after 301 response to POST" in {
      expectString(POST, "301").run must_== "GET"
    }
    "PUT after 301 response to PUT" in {
      expectString(PUT, "301").run must_== "PUT"
    }

    "GET after 302 response to GET" in {
      expectString(GET, "302").run must_== "GET"
    }
    "GET after 302 response to POST" in {
      expectString(POST, "302").run must_== "GET"
    }
    "PUT after 302 response to PUT" in {
      expectString(PUT, "302").run must_== "PUT"
    }

    "GET after 303 response to GET" in {
      expectString(GET, "302").run must_== "GET"
    }
    "GET after 303 response to POST" in {
      expectString(POST, "302").run must_== "GET"
    }
    "GET after 303 response to PUT" in {
      expectString(PUT, "302").run must_== "PUT"
    }
    "HEAD after 303 response to HEAD" in {
      expectString(HEAD, "303").run must_== "HEAD"
    }

    "GET after 307 response to GET" in {
      expectString(GET, "307").run must_== "GET"
    }
    "POST after 307 response to POST" in {
      expectString(POST, "307").run must_== "POST"
    }
    "PUT after 307 response to PUT" in {
      expectString(PUT, "307").run must_== "PUT"
    }

    "GET after 308 response to GET" in {
      expectString(GET, "308").run must_== "GET"
    }
    "POST after 308 response to POST" in {
      expectString(POST, "308").run must_== "POST"
    }
    "PUT after 308 response to PUT" in {
      expectString(PUT, "308").run must_== "PUT"
    }

    "Not redirect more than 'maxRedirects' iterations" in {
      val statefulService = HttpService {
        case GET -> Root / "loop" =>
          val body = loopCounter.incrementAndGet.toString
          MovedPermanently(uri("/loop")).withBody(body)
      }
      val client = FollowRedirect(3)(MockClient(statefulService))
      client.fetch(GET(uri("http://localhost/loop"))) {
        case MovedPermanently(resp) => resp.as[String].map(_.toInt)
        case _ => Task.now(-1)
      }.run must_==(4)
    }
  }
}
