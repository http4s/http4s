package org.http4s.client
package middleware

import org.http4s.{Uri, Status, Http4sSpec, Response}
import org.http4s.Status._
import org.http4s.Method._
import org.http4s.headers.Location
import org.http4s.server.HttpService


class FollowRedirectSpec extends Http4sSpec {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/ok"       => Response(Ok).withBody("hello")
    case r if r.method == GET && r.pathInfo == "/redirect" => Response(MovedPermanently).replaceAllHeaders(Location(uri("/ok"))).withBody("Go there.")
    case r if r.method == GET && r.pathInfo == "/loop"     => Response(MovedPermanently).replaceAllHeaders(Location(uri("/loop"))).withBody("Go there.")
    case r => sys.error("Path not found: " + r.pathInfo)
  }


  val defaultClient = new MockClient(route)
  val client = FollowRedirect(1)(defaultClient)
  
  "FollowRedirect" should {
    "Honor redirect" in {
      val resp = client(getUri(s"http://localhost/redirect")).run
      resp.status must_== Status.Ok
    }

    "Terminate redirect loop" in {
      val resp = client(getUri(s"http://localhost/loop")).run
      resp.status must_== Status.MovedPermanently
    }

    "Not redirect more than 'maxRedirects' iterations" in {
      val resp = defaultClient(getUri(s"http://localhost/redirect")).run
      resp.status must_== Status.MovedPermanently
    }
  }

  def getUri(s: String): Uri = Uri.fromString(s).getOrElse(sys.error("Bad uri."))
}
