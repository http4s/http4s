package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import cats.data.OptionT
import org.http4s._
import org.http4s.headers.{Date => HDate}
import cats.effect.testing.specs2.CatsIO

class DateSpec extends Http4sSpec with CatsIO {

  implicit val T = IO.timer(scala.concurrent.ExecutionContext.global)

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ => Response[IO](Status.Ok).pure[IO]
  }

  // Hack for https://github.com/typelevel/cats-effect/pull/682
  val testService = Date(service)(Sync[OptionT[IO, *]], Clock.deriveOptionT[IO])
  val testApp = Date(service.orNotFound)

  val req = Request[IO]()

  "Date" should {
    "always be very shortly before the current time httpRoutes" >> {
      for {
        out <- testService(req).value
      } yield {
        out.flatMap(_.headers.get(HDate)) must beSome.like {
          case date =>
            val diff = date.date.epochSecond - HttpDate.now.epochSecond
            val test = diff <= 2
            test must beTrue
        }
      }
    }

    "always be very shortly before the current time httpApp" >> {
      for {
        out <- testApp(req)
      } yield {
        out.headers.get(HDate) must beSome.like {
          case date =>
            val diff = date.date.epochSecond - HttpDate.now.epochSecond
            val test = diff <= 2
            test must beTrue
        }
      }
    }

    "not override a set date header" in {
      val service = HttpRoutes
        .of[IO] {
          case _ =>
            Response[IO](Status.Ok)
              .putHeaders(HDate(HttpDate.Epoch))
              .pure[IO]
        }
        .orNotFound
      val test = Date(service)

      for {
        out <- test(req)
      } yield {
        out.headers.get(HDate) must beSome.like {
          case date =>
            val now = HttpDate.now.epochSecond
            val diff = date.date.epochSecond - now
            now must_=== Math.abs(diff)
        }
      }
    }
  }
}
