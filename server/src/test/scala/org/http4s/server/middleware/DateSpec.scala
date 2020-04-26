package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import cats.data.OptionT
import org.http4s._
import org.http4s.headers.{Date => HDate}
import cats.effect.testing.specs2.CatsIO

class DateSpec extends Http4sSpec with CatsIO {
  override implicit val timer: Timer[IO] = Http4sSpec.TestTimer

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
        now <- HttpDate.current[IO]
      } yield {
        out.flatMap(_.headers.get(HDate)) must beSome.like {
          case date =>
            val diff = now.epochSecond - date.date.epochSecond
            diff must be_<=(2L)
        }
      }
    }

    "always be very shortly before the current time httpApp" >> {
      for {
        out <- testApp(req)
        now <- HttpDate.current[IO]
      } yield {
        out.headers.get(HDate) must beSome.like {
          case date =>
            val diff = now.epochSecond - date.date.epochSecond
            diff must be_<=(2L)
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
        nowD <- HttpDate.current[IO]
      } yield {
        out.headers.get(HDate) must beSome.like {
          case date =>
            val now = nowD.epochSecond
            val diff = now - date.date.epochSecond
            now must_=== diff
        }
      }
    }

    "be created via httpRoutes constructor" in {
      val httpRoute = Date.httpRoutes(service)

      for {
        response <- httpRoute(req).value
      } yield {
        response.flatMap(_.headers.get(HDate)) must beSome
      }
    }

    "be created via httpApp constructor" in {
      val httpApp = Date.httpApp(service.orNotFound)

      for {
        response <- httpApp(req)
      } yield {
        response.headers.get(HDate) must beSome
      }
    }
  }
}
