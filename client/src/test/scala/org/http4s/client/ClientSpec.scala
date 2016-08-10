package org.http4s
package client

import java.io.IOException

import org.http4s.Http4sSpec
import org.http4s.headers.Accept
import org.http4s.Status.InternalServerError

import scalaz.-\/
import scalaz.concurrent.Task
import scalaz.stream.Process

import org.http4s.Status.{Ok, NotFound, Created, BadRequest}
import org.http4s.Method._
import org.http4s.Uri.uri

class ClientSpec extends Http4sSpec {
  val service = HttpService {
    case r =>
      Response(Ok).withBody(r.body)
  }
  val client = Client.fromHttpService(service)

  "mock client" should {
    "read body before dispose" in {
      client.expect[String](Request(POST).withBody("foo")).run must_== "foo"
    }

    "fail to read body after dispose" in {
      Request(POST).withBody("foo").flatMap { req =>
        // This is bad.  Don't do this.
        client.fetch(req)(Task.now).flatMap(_.as[String])
      }.attemptRun must be_-\/.like {
        case e: IOException => e.getMessage == "response was disposed"
      }
    }

    "fail to read body after client shutdown" in {
      val client = Client.fromHttpService(service)
      client.shutdown.run
      client.expect[String](Request(POST).withBody("foo")).attemptRun must be_-\/.like {
        case e: IOException => e.getMessage == "client was shut down"
      }
    }
  }
}
