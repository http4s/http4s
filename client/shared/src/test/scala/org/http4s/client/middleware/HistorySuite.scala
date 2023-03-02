package org.http4s.client.middleware

import cats.effect.{IO, Ref}
import cats.syntax.all._
import org.http4s.client.Client
import org.http4s.client.middleware.History.HistoryEntry
import org.http4s.{Http4sSuite, HttpDate, HttpRoutes, Method, Request, Response, Status, Uri}
import org.http4s.dsl.io._
import org.http4s.headers.Date
import org.http4s.implicits.http4sLiteralsSyntax


// uses munit
// create a history object
// make some calls
// see if history matches?
// test 1 history blank if no sitesvisited
// test 2 history shows last NUM sites visited
// test 3 history cuts off oldest sitesvisited after max reached
// test 4 when max is exceeded, shoudl the newest places visited
// test 5 what happens when sending requests w/o date - what goes into history?
// or coudl use loop and loop 3 times and history should show3 visits
class HistorySuite extends Http4sSuite {

  // should i return a status? or is it enough that it was an OK?
  // get rid of this complication
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
      case _ -> Root / status =>
        IO.pure(Response(Status.fromInt(status.toInt).valueOr(throw _)))
    }
    .orNotFound

  private val defaultClient = Client.fromHttpApp(app)

  //    val req = Request[IO](uri = uri"/request")

  // old way was bad!
  //    emptyHistoryRef.unsafeRunSync() == Ref.of[IO, Vector[HistoryEntry]](Vector.empty).unsafeRunSync()
  test("History middeware should return empty history if no sites have been visited"){
    val expected: Vector[HistoryEntry] = Vector.empty

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      History.apply(defaultClient, ref, 3)
      ref.get.assertEquals(expected)
    }
  }

  // creating a LOT of Refs... what Refs are being tested
//  test("History middeware should return 1 history item if 1 sites have been visited/req run"){
//    val historyRef = Ref.of[IO, Vector[HistoryEntry]](Vector.empty)
//    val clientWrapped: Client[IO] = historyRef.map(h => History.apply(
//      defaultClient, h, 3)).unsafeRunSync()
//    // can play with a specific date
//    val req = Request[IO](uri = uri"/request").putHeaders(Date(HttpDate.current[IO].unsafeRunSync()))
//    clientWrapped.run(req)
//
////    val date2 = Date(HttpDate.current[IO].unsafeRunSync())
//    val expectedEntry = HistoryEntry(req.headers.get[Date].get.date, req.method, req.uri)
//
//    historyRef.unsafeRunSync() == Ref.of[IO, Vector[HistoryEntry]](Vector(expectedEntry)).unsafeRunSync()
//  }

  // retry w/o creating so many darn Refs...
  // this isn't working :(
//  test("History middeware should return max number of visits if visits exceeds maxSize"){
//    val historyRef = Ref.of[IO, Vector[HistoryEntry]](Vector.empty).unsafeRunSync()
//    val clientWithHistory = History.apply(defaultClient, historyRef, 2)
//
//    val req1 = Request[IO](uri = uri"/request").putHeaders(Date(HttpDate.current[IO].unsafeRunSync()))
//    val req2 = Request[IO](uri = uri"/request").putHeaders(Date(HttpDate.current[IO].unsafeRunSync()))
//    val req3 = Request[IO](uri = uri"/request").putHeaders(Date(HttpDate.current[IO].unsafeRunSync()))
//
//    clientWithHistory.run(req1)
//    clientWithHistory.run(req2)
//    clientWithHistory.run(req3)
//
//    val expectedHistoryEntry1 = HistoryEntry(req1.headers.get[Date].get.date, req1.method, req1.uri)
//    val expectedHistoryEntry2 = HistoryEntry(req2.headers.get[Date].get.date, req2.method, req2.uri)
//    val expectedHistoryEntry3 = HistoryEntry(req3.headers.get[Date].get.date, req3.method, req3.uri)
//
//    // don't use eq .get
//    // use assert
//    // use === where you can
////    historyRef.get.unsafeRunSync().assert === Vector(expectedHistoryEntry3)
//    assert(historyRef.get.unsafeRunSync())
//  }

}
