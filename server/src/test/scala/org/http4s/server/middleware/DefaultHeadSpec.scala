package org.http4s
package server
package middleware

import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process._
import org.http4s.Uri.uri
import org.http4s.dsl._

class DefaultHeadSpec extends Http4sSpec {
  val service = DefaultHead(HttpService {
    case req @ GET -> Root / "hello" =>
      Ok("hello")

    case GET -> Root / "special" =>
      Ok().putHeaders(Header("X-Handled-By", "GET"))

    case HEAD -> Root / "special" =>
      Ok().putHeaders(Header("X-Handled-By", "HEAD"))
  })

  "DefaultHead" should {
    "honor HEAD routes" in {
      val req = Request(Method.HEAD, uri = uri("/special"))
      service.orNotFound(req).map(_.headers.get("X-Handled-By".ci).map(_.value)) must returnValue(Some("HEAD"))
    }

    "return truncated body of corresponding GET on fallthrough" in {
      val req = Request(Method.HEAD, uri = uri("/hello"))
      service.orNotFound(req).flatMap(_.as[String]).unsafePerformSync must_== ""
    }

    "retain all headers of corresponding GET on fallthrough" in {
      val get = Request(Method.GET, uri = uri("/hello"))
      val head = get.withMethod(Method.HEAD)
      service.orNotFound(get).map(_.headers).unsafePerformSync must_== service.orNotFound(head).map(_.headers).unsafePerformSync
    }

    "allow GET body to clean up on fallthrough" in {
      var cleanedUp = false
      val service = DefaultHead(HttpService {
        case GET -> _ =>
          val body: EntityBody = halt.onComplete(eval_(Task.delay(cleanedUp = true)))
          Ok(body)
      })
      service.orNotFound(Request(Method.HEAD)).flatMap(_.as[String]).unsafePerformSync
      cleanedUp must beTrue
    }
  }
}
