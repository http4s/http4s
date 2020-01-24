package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import cats.data._
import cats.effect.concurrent._
import org.http4s._

class MaxActiveRequestsSpec extends Http4sSpec {
  val req = Request[IO]()

  def routes(startedGate: Deferred[IO, Unit], deferred: Deferred[IO, Unit]) = Kleisli {
    req: Request[IO] =>
      req match {
        case other if other.method == Method.PUT => OptionT.none[IO, Response[IO]]
        case _ =>
          OptionT.liftF(
            startedGate.complete(()) >> deferred.get >> Response[IO](Status.Ok).pure[IO])
      }
  }

  "httpApp" should {
    "allow a request when allowed" in {
      for {
        deferredStarted <- Deferred[IO, Unit]
        deferredWait <- Deferred[IO, Unit]
        _ <- deferredWait.complete(())
        middle <- MaxActiveRequests.httpApp[IO](1)
        httpApp = middle(routes(deferredStarted, deferredWait).orNotFound)
        out <- httpApp.run(req)
      } yield out.status must_=== Status.Ok
    }

    "not allow a request if max active" in {
      for {
        deferredStarted <- Deferred[IO, Unit]
        deferredWait <- Deferred[IO, Unit]
        middle <- MaxActiveRequests.httpApp[IO](1)
        httpApp = middle(routes(deferredStarted, deferredWait).orNotFound)
        f <- httpApp.run(req).start
        _ <- deferredStarted.get
        out <- httpApp.run(req)
        _ <- f.cancel
      } yield out.status must_=== Status.ServiceUnavailable
    }
  }

  "httpRoutes" should {
    "allow a request when allowed" in {
      for {
        deferredStarted <- Deferred[IO, Unit]
        deferredWait <- Deferred[IO, Unit]
        _ <- deferredWait.complete(())
        middle <- MaxActiveRequests.httpRoutes[IO](1)
        httpApp = middle(routes(deferredStarted, deferredWait)).orNotFound
        out <- httpApp.run(req)
      } yield out.status must_=== Status.Ok
    }

    "not allow a request if max active" in {
      for {
        deferredStarted <- Deferred[IO, Unit]
        deferredWait <- Deferred[IO, Unit]
        middle <- MaxActiveRequests.httpRoutes[IO](1)
        httpApp = middle(routes(deferredStarted, deferredWait)).orNotFound
        f <- httpApp.run(req).start
        _ <- deferredStarted.get
        out <- httpApp.run(req)
        _ <- f.cancel
      } yield out.status must_=== Status.ServiceUnavailable
    }

    "release resource on None" in {
      for {
        deferredStarted <- Deferred[IO, Unit]
        deferredWait <- Deferred[IO, Unit]
        middle <- MaxActiveRequests.httpRoutes[IO](1)
        httpApp = middle(routes(deferredStarted, deferredWait)).orNotFound
        out1 <- httpApp.run(Request(Method.PUT))
        _ <- deferredWait.complete(())
        out2 <- httpApp.run(req)
      } yield (out1.status, out2.status) must_=== ((Status.NotFound, Status.Ok))
    }
  }
}
