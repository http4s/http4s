package org.http4s.client
package middleware

import org.http4s.{Uri, Status, Http4sSpec, Response}
import org.http4s.Status._
import org.http4s.Method._
import org.http4s.headers.Location
import org.http4s.server.HttpService

import scalaz.concurrent.Task

import scala.language.postfixOps
import scala.concurrent.duration._

class RetrySpec extends Http4sSpec {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/ok" => Response(Ok).withBody("hello")
    case r if r.method == GET && r.pathInfo == "/boom" => Task.now(Response(BadRequest))
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  val defaultClient = new MockClient(route)

  "Retry Client" should {
    "Retry bad requests" in {
      val max = 2
      var attempts = 1
      val policy = (attmpts: Int) => {
        if (attempts >= max) None
        else {
          attempts = attempts + 1
          Some(10.milliseconds)
        }
      }
      val client = Retry(policy)(defaultClient)
      val resp = client(getUri(s"http://localhost/boom")).run
      attempts must_== 2
    }

    "Not retry successful responses" in {
      val max = 2
      var attempts = 1
      val policy = (attmpts: Int) => {
        if (attempts >= max) None
        else {
          attempts = attempts + 1
          Some(10.milliseconds)
        }
      }
      val client = Retry(policy)(defaultClient)
      val resp = client(getUri(s"http://localhost/ok")).run
      attempts must_==1
    }
  }

  def getUri(s: String): Uri = Uri.fromString(s).getOrElse(sys.error("Bad uri."))
}
