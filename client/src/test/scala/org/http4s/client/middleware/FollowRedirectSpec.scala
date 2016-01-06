package org.http4s
package client
package middleware

import org.http4s.Status._
import org.http4s.Method._
import org.http4s.headers.Location


class FollowRedirectSpec extends Http4sSpec {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/ok"       => Response(Ok).withBody("hello")
    case r if r.method == GET && r.pathInfo == "/redirect" => Response(MovedPermanently).replaceAllHeaders(Location(uri("/ok"))).withBody("Go there.")
    case r if r.method == GET && r.pathInfo == "/loop"     => Response(MovedPermanently).replaceAllHeaders(Location(uri("/loop"))).withBody("Go there.")
    case r if r.method == POST && r.pathInfo == "/303"      => 
      Response(SeeOther).replaceAllHeaders(Location(uri("/ok"))).withBody("Go to /ok")

    case r => sys.error("Path not found: " + r.pathInfo)
  }


  val defaultClient = MockClient(route)
  val client = FollowRedirect(1)(defaultClient)
  val fetchBody = client.toService(_.as[String])
  
  "FollowRedirect" should {
    "Honor redirect" in {
      fetchBody =<< GET(uri("http://localhost/redirect")) must returnValue("hello")
    }

    "Not redirect more than 'maxRedirects' iterations" in {
      fetchBody =<< GET(uri("http://localhost/loop")) must returnValue("Go there.")
    }

    "Use a GET method on redirect with 303 response code" in {
      fetchBody =<< POST(uri("http://localhost/303")) must returnValue("hello")
    }
  }
}
