package org.http4s
package server
package middleware

import java.nio.charset.StandardCharsets

import org.http4s.server.HttpService
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge
import org.specs2.mutable.Specification
import scodec.bits.ByteVector
import scalaz.stream.Process.emit
import Method._
import Status._

class EntityLimiterSpec extends Specification {
  import Http4s._

  val s: HttpService = Service {
    case r: Request if r.uri.path == "/echo" => EntityDecoder.text(r).flatMap(ResponseBuilder(Ok, _))
    case r => ResponseBuilder.notFound(r)
  }

  val b = emit(ByteVector.view("hello".getBytes(StandardCharsets.UTF_8)))

  "EntityLimiter" should {

    "Allow reasonable entities" in {
      EntityLimiter(s, 100).apply(Request(POST, uri("/echo"), body = b))
        .map(_ => -1)
        .run must be_==(-1)
    }

    "Limit the maximum size of an EntityBody" in {
      EntityLimiter(s, 3).apply(Request(POST, uri("/echo"), body = b))
        .map(_ => -1)
        .handle { case EntityTooLarge(i) => i }
        .run must be_==(3)
    }

    "Chain correctly with other HttpServices" in {
      val s2: HttpService = Service {
        case r: Request if r.uri.path == "/echo2" => EntityDecoder.text(r).flatMap(ResponseBuilder(Ok, _))
        case r => ResponseBuilder.notFound(r)
      }

      val st: HttpService = EntityLimiter(s, 3) orElse s2
      (st.apply(Request(POST, uri("/echo2"), body = b))
        .map(_ => -1)
        .run must be_==(-1)) &&
        (st.apply(Request(POST, uri("/echo"), body = b))
          .map(_ => -1)
          .handle { case EntityTooLarge(i) => i }
          .run must be_==(3))
    }
  }

}
