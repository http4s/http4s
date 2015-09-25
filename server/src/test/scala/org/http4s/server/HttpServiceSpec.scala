package org.http4s
package server

import syntax.ServiceOps

class HttpServiceSpec extends Http4sSpec {

  val notFound = HttpService.notFound.run.as[String].run

  val srvc1 = HttpService.lift { _ =>
    Response(Status.NotFound).withBody("srvc1")
  }

 val srvc2 = HttpService {
   case req if req.pathInfo == "/match" =>
    Response(Status.Ok).withBody("match")

   case _ => Response(Status.NotFound).withBody("srvc2")
  }

  val srvc3 = HttpService {
    case _ if false => ???
  }

  val aggregate1 = srvc1 orElse srvc2

  val aggregate2 = srvc2 orElse srvc1

  val aggregate3 = srvc1 || srvc3

  val aggregate4 = srvc3 || srvc1

  "HttpService" should {
    "Fall through to a second service on NotFound" in {
      aggregate1.apply(Request(uri = uri("/match"))).run.as[String].run must equal ("match")
    }

    "Use the first Fallthrough (NotFound) when they appear in a chain" in {
      aggregate1.apply(Request(uri = uri("/wontMatch"))).run.as[String].run must equal ("srvc1")
      aggregate2.apply(Request(uri = uri("/wontMatch"))).run.as[String].run must equal ("srvc2")
    }

    "Safely skip undefined behavior in either position" in {
      aggregate3.apply(Request(uri = uri("/wontMatch"))).run.as[String].run must equal ("srvc1")
      aggregate4.apply(Request(uri = uri("/wontMatch"))).run.as[String].run must equal (notFound)
    }
  }
}
