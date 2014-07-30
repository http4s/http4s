package org.http4s
package server.middleware

import java.nio.charset.StandardCharsets

import org.http4s.server.HttpService
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge
import org.specs2.mutable.Specification
import scodec.bits.ByteVector
import scalaz.stream.Process.emit

class EntityLimiterSpec extends Specification {
  import Http4s._
  import Method._

  val s: HttpService = {
    case r: Request if r.requestUri.path == "/echo" => EntityDecoder.text(r).flatMap(Ok(_))
  }

  val b = emit(ByteVector.view("hello".getBytes(StandardCharsets.UTF_8)))

  "EntityLimiter" should {

    "Allow reasonable entities" in {
      EntityLimiter(s, 100).apply(Post("/echo").run.copy(body = b))
        .map(_ => -1)
        .run must be_==(-1)
    }

    "Limit the maximum size of an EntityBody" in {
      EntityLimiter(s, 3).apply(Post("/echo").run.copy(body = b))
        .map(_ => -1)
        .handle { case EntityTooLarge(i) => i }
        .run must be_==(3)
    }

    "Chain correctly with other HttpServices" in {
      val s2: HttpService = {
        case r: Request if r.requestUri.path == "/echo2" => EntityDecoder.text(r).flatMap(Ok(_))
      }

      val st = EntityLimiter(s, 3) orElse s2
      (st.apply(Post("/echo2").run.copy(body = b))
        .map(_ => -1)
        .run must be_==(-1)) &&
        (st.apply(Post("/echo").run.copy(body = b))
          .map(_ => -1)
          .handle { case EntityTooLarge(i) => i }
          .run must be_==(3))
    }
  }

}
