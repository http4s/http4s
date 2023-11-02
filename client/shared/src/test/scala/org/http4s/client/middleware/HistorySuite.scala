package org.http4s.client.middleware

import cats.Applicative
import cats.effect.{Clock, IO, Ref}
import org.http4s.client.Client
import org.http4s.client.middleware.History.HistoryEntry
import org.http4s.{Http4sSuite, HttpDate, HttpRoutes, Request}
import org.http4s.dsl.io._
import org.http4s.headers.Date
import org.http4s.implicits.http4sLiteralsSyntax

import java.time.{Duration, Instant}
import scala.concurrent.duration.FiniteDuration


// see if history matches?
// test 1 history blank if no sitesvisited
// test 2 history shows last NUM sites visited
// test 3 history cuts off oldest sitesvisited after max reached
// test 4 when max is exceeded, shoudl the newest places visited
// test 5 what happens when sending requests w/o date - what goes into history?
// or coudl use loop and loop 3 times and history should show3 visits
class HistorySuite extends Http4sSuite {

  private val app = HttpRoutes
    .of[IO] {
      case req @ _ -> Root / "site1" =>
        req.as[String].flatMap {
          case "OK" => Ok()
          case "" => InternalServerError()
        }
      case req @ _ -> Root / "site2" =>
        req.as[String].flatMap {
          case "OK" => Ok()
          case "" => InternalServerError()
        }
      case _ =>
        Ok()
    }
    .orNotFound

  private val defaultClient = Client.fromHttpApp(app)

  private val date1 = Date(HttpDate.unsafeFromInstant(Instant.now()))
  private val date2 = Date(HttpDate.unsafeFromInstant(Instant.now().plus(Duration.ofMinutes(1L))))
  private val date3 = Date(HttpDate.unsafeFromInstant(Instant.now().plus(Duration.ofMinutes(2L))))

  private val req1 = Request[IO](uri = uri"/request").putHeaders(date1)
  private val req2 = Request[IO](uri = uri"/request").putHeaders(date2)
  private val req3 = Request[IO](uri = uri"/request").putHeaders(date3)

  test("History middeware should return empty history if no sites have been visited") {
    val expected: Vector[HistoryEntry] = Vector.empty

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      History.apply(defaultClient, ref, 3)
      ref.get.assertEquals(expected)
    }
  }

  test("History middeware should return 1 history item if 1 site has been visited") {
    val date = Date(HttpDate.unsafeFromInstant(Instant.now()))
    val req = Request[IO](uri = uri"/request").putHeaders(date)
    val expected = Vector(HistoryEntry(req.headers.get[Date].get.date, req.method, req.uri))

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      History.apply(defaultClient, ref, 3).run(req).use(_ => IO.unit) >> ref.get.assertEquals(expected)
    }
  }

    test("History middeware should return visits in order of most recent to oldest"){

      val expected: Vector[HistoryEntry] = Vector(HistoryEntry(req1.headers.get[Date].get.date, req1.method, req1.uri)).prepended(HistoryEntry(req2.headers.get[Date].get.date, req2.method, req2.uri)).prepended(HistoryEntry(req3.headers.get[Date].get.date, req3.method, req3.uri))

      Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
        History(defaultClient, ref, 3).run(req1).use_ >>
          History(defaultClient, ref, 3).run(req2).use_ >>
          History(defaultClient, ref, 3).run(req3).use_ >>
          ref.get.assertEquals(expected)
      }
  }

  // the last history middleware run
  test("History middeware should return max number of visits if visits exceeds maxSize"){

    val expected: Vector[HistoryEntry] = Vector(HistoryEntry(req2.headers.get[Date].get.date, req2.method, req2.uri)).prepended(HistoryEntry(req3.headers.get[Date].get.date, req3.method, req3.uri))

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      val historyClient = History(defaultClient, ref, 2)

      historyClient.run(req1).use_ >>
        historyClient.run(req2).use_ >>
        historyClient.run(req3).use_ >>
        ref.get.assertEquals(expected)
    }
  }

  // test 5 what happens when sending requests w/o date - what goes into history?
  // what am I expecting to come back? a date, any date?
  test("History middeware should return max number of visits if visits exceeds maxSize"){

    val sallysClock = new Clock[IO] {
      override def applicative: Applicative[IO] = Applicative[IO] // IO.asyncForIO

      override def monotonic: IO[FiniteDuration] = IO.pure(FiniteDuration(0L, scala.concurrent.duration.HOURS ))

      override def realTime: IO[FiniteDuration] = IO.pure(FiniteDuration(0L, scala.concurrent.duration.HOURS ))
    }

    val expected: Vector[HistoryEntry] = Vector(HistoryEntry(req2.headers.get[Date].get.date, req2.method, req2.uri)).prepended(HistoryEntry(req3.headers.get[Date].get.date, req3.method, req3.uri))

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      val historyClient = History(defaultClient, ref, 2)

      historyClient.run(req1).use_ >>
        historyClient.run(req2).use_ >>
        historyClient.run(req3).use_ >>
        ref.get.assertEquals(expected)
    }
  }

}
