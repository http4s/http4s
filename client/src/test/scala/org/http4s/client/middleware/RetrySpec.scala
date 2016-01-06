package org.http4s
package client
package middleware

import org.http4s.Status._
import org.http4s.Method._
import org.http4s.headers.Location

import scalaz.concurrent.Task

import scala.language.postfixOps
import scala.concurrent.duration._

class RetrySpec extends Http4sSpec {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/ok" => Response(Ok).withBody("hello")
    case r if r.method == GET && r.pathInfo == "/boom" => Task.now(Response(BadRequest))
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  val defaultClient = MockClient(route)

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
      val resp = client.getAs[String](uri("http://localhost/boom")).run
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
      val resp = client.getAs[String](uri("http://localhost/ok")).run
      attempts must_==1
    }
  }
}
