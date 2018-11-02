package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Ref
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.duration.TimeUnit

class ResponseTimingSpec extends Http4sSpec {
  import Sys.clock

  private val artificialDelay = 10

  private val thisService = HttpApp[IO] {
    case GET -> Root / "request" =>
      List.fill(artificialDelay)(Sys.tick()).sequence_ *>
        Ok("request response")
  }

  "ResponseTiming middleware" should {
    "add a custom header with timing info" in {
      val req = Request[IO](uri = Uri.uri("/request"))
      val app = ResponseTiming(thisService)
      val res = app(req)

      val header = res
        .map(_.headers.find(_.name == CaseInsensitiveString("X-Response-Time")))
        .unsafeRunSync()

      header.nonEmpty must_== true
      header.get.value.toInt must_== artificialDelay
    }
  }

}

object Sys {

  private val currentTime: Ref[IO, Long] = Ref.unsafe[IO, Long](System.currentTimeMillis())

  def tick(): IO[Long] = currentTime.modify(l => (l + 1L, l))

  implicit val clock: Clock[IO] = new Clock[IO] {

    override def realTime(unit: TimeUnit): IO[Long] = currentTime.get

    override def monotonic(unit: TimeUnit): IO[Long] = currentTime.get
  }
}
