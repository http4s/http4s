package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import cats.data._
import cats.effect.concurrent._
import org.http4s._
import org.http4s.implicits._
import org.http4s.util.threads.{newBlockingPool, newDaemonPool, threadFactory}
import org.specs2.mutable.Specification
import org.specs2.execute.{AsResult, Failure, Result}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}

class MaxActiveRequestsSpec extends Specification {

  implicit def testExecutionContext: ExecutionContext = Http4sSpec.TestExecutionContext
  val testBlockingExecutionContext: ExecutionContext = Http4sSpec.TestBlockingExecutionContext
  implicit val contextShift: ContextShift[IO] = Http4sSpec.TestContextShift
  implicit val timer: Timer[IO] = Http4sSpec.TestTimer
  def scheduler: ScheduledExecutorService = Http4sSpec.TestScheduler

  val req = Request[IO]()

  protected val Timeout = 10.seconds

  def routes(deferred: Deferred[IO, Unit]) = Kleisli { req: Request[IO] =>
    req match {
      case other if other.method == Method.PUT => OptionT.none[IO, Response[IO]]
      case _ => OptionT.liftF(deferred.get >> Response[IO](Status.Ok).pure[IO])
    }
  }

  implicit def ioAsResult[R](implicit R: AsResult[R]): AsResult[IO[R]] = new AsResult[IO[R]] {
    def asResult(t: => IO[R]): Result =
      t.unsafeRunTimed(Timeout)
        .map(R.asResult(_))
        .getOrElse(Failure(s"expectation timed out after $Timeout"))
  }

  "httpApp" should {
    "allow a request when allowed" in {
      for {
        deferred <- Deferred[IO, Unit]
        _ <- deferred.complete(())
        middle <- MaxActiveRequests.httpApp[IO](1)
        httpApp = middle(routes(deferred).orNotFound)
        out <- httpApp.run(req)
      } yield out.status must_=== Status.Ok
    }

    "not allow a request if max active" in {
      for {
        deferred <- Deferred[IO, Unit]
        middle <- MaxActiveRequests.httpApp[IO](1)
        httpApp = middle(routes(deferred).orNotFound)
        f <- httpApp.run(req).start
        out <- httpApp.run(req)
        _ <- f.cancel
      } yield out.status must_=== Status.ServiceUnavailable
    }
  }

  "httpRoutes" should {
    "allow a request when allowed" in {
      for {
        deferred <- Deferred[IO, Unit]
        _ <- deferred.complete(())
        middle <- MaxActiveRequests.httpRoutes[IO](1)
        httpApp = middle(routes(deferred)).orNotFound
        out <- httpApp.run(req)
      } yield out.status must_=== Status.Ok
    }

    "not allow a request if max active" in {
      for {
        deferred <- Deferred[IO, Unit]
        middle <- MaxActiveRequests.httpRoutes[IO](1)
        httpApp = middle(routes(deferred)).orNotFound
        f <- httpApp.run(req).start
        out <- httpApp.run(req)
        _ <- f.cancel
      } yield out.status must_=== Status.ServiceUnavailable
    }

    "release resource on None" in {
      for {
        deferred <- Deferred[IO, Unit]
        middle <- MaxActiveRequests.httpRoutes[IO](1)
        httpApp = middle(routes(deferred)).orNotFound

        out1 <- httpApp.run(Request(Method.PUT))
        _ <- deferred.complete(())
        out2 <- httpApp.run(req)
      } yield (out1.status, out2.status) must_=== ((Status.NotFound, Status.Ok))
    }
  }
}

object MaxActiveRequestsSpec {
  val TestExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(newDaemonPool("http4s-spec", timeout = true))

  val TestBlocker: Blocker =
    Blocker.liftExecutorService(newBlockingPool("http4s-spec-blocking"))

  val TestContextShift: ContextShift[IO] =
    IO.contextShift(TestExecutionContext)

  val TestScheduler: ScheduledExecutorService = {
    val s =
      new ScheduledThreadPoolExecutor(2, threadFactory(i => s"http4s-test-scheduler-$i", true))
    s.setKeepAliveTime(10L, TimeUnit.SECONDS)
    s.allowCoreThreadTimeOut(true)
    s
  }

  val TestTimer: Timer[IO] =
    IO.timer(TestExecutionContext, TestScheduler)
}
