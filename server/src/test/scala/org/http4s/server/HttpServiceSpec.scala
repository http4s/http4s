package org.http4s
package server


class HttpServiceSpec extends Http4sSpec {

  val srvc1 = HttpService.lift { _ =>
    Response(Status.NotFound).withBody("srvc1")
  }

 val srvc2 = HttpService.liftPF {
   case req if req.pathInfo == "/match" =>
    Response(Status.Ok).withBody("match")

   case _ => Response(Status.NotFound).withBody("srvc2")
  }

  val srvc3 = HttpService.liftPF {
    case _ if false => ???
  }

  val aggregate1 = srvc1 orElse srvc2

  val aggregate2 = srvc3 orElse srvc1

  "HttpService" should {
    "Fall through to a second service on NotFound" in {
      aggregate1(Request(uri = uri("/match"))).run.as[String].run must equal ("match")
    }

    "Honor the first custom NotFound" in {
      aggregate1(Request(uri = uri("/wontMatch"))).run.as[String].run must equal ("srvc1")
      aggregate2(Request(uri = uri("/wontMatch"))).run.as[String].run must equal ("srvc1")
    }
  }
}
