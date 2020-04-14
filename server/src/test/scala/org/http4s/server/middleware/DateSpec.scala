package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import cats.data.OptionT
import org.http4s._
import org.http4s.headers.{Date => HDate}

class DateSpec extends Http4sSpec {
  override implicit val timer: Timer[IO] = Http4sSpec.TestTimer

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ => Response[IO](Status.Ok).pure[IO]
  }

  val req = Request[IO]()

  "Date" should {
    "be created via httpRoutes constructor" in {
      val httpRoute = Date.httpRoutes(service)

      for {
        response <- httpRoute(req).value
      } yield {
        response.flatMap(_.headers.get(HDate)) must beSome
      }
    }.unsafeRunSync()

    "be created via httpApp constructor" in {
      val httpApp = Date.httpApp(service.orNotFound)

      for {
        response <- httpApp(req)
      } yield {
        response.headers.get(HDate) must beSome
      }
    }.unsafeRunSync()
  }
}
