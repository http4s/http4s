package org.http4s
package client

import cats.effect._
import fs2.Stream
import scala.concurrent.duration._

class PoolManagerSpec(name: String) extends Http4sSpec {
  val key = RequestKey(Uri.Scheme.http, Uri.Authority(host = Uri.IPv4("127.0.0.1")))
  class TestConnection extends Connection[IO] {
    def runRequest(req: Request[IO]) = IO.never
    def isClosed = false
    def isRecyclable = true
    def requestKey = key
    def shutdown() = ()
  }

  def mkPool(
      maxTotal: Int = 1,
      maxWaitQueueLimit: Int = 2,
      requestTimeout: Duration = Duration.Inf
  ) =
    IO(
      ConnectionManager.pool(
        builder = _ => IO(new TestConnection()),
        maxTotal = maxTotal,
        maxWaitQueueLimit = maxWaitQueueLimit,
        maxConnectionsPerRequestKey = _ => 5,
        responseHeaderTimeout = Duration.Inf,
        requestTimeout = requestTimeout,
        executionContext = testExecutionContext
      ))

  "A pool manager" should {
    "wait up to maxWaitQueueLimit" in {
      (for {
        pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 2)
        _ <- pool.borrow(key)
        att <- Stream(Stream.eval(pool.borrow(key))).repeat
          .take(2)
          .covary[IO]
          .joinUnbounded
          .compile
          .toList
          .attempt
      } yield att).unsafeRunTimed(2.seconds) must_== None
    }

    "throw at maxWaitQueueLimit" in {
      (for {
        pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 2)
        _ <- pool.borrow(key)
        att <- Stream(Stream.eval(pool.borrow(key))).repeat
          .take(3)
          .covary[IO]
          .joinUnbounded
          .compile
          .toList
          .attempt
      } yield att).unsafeRunTimed(2.seconds) must_== Some(Left(WaitQueueFullFailure()))
    }

    "wake up a waiting connection on release" in {
      (for {
        pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 1)
        conn <- pool.borrow(key)
        fiber <- pool.borrow(key).start // Should be one waiting
        _ <- pool.release(conn.connection)
        _ <- fiber.join
      } yield ()).unsafeRunTimed(2.seconds) must_== Some(())
    }

    "wake up a waiting connection on invalidate" in {
      (for {
        pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 1)
        conn <- pool.borrow(key)
        fiber <- pool.borrow(key).start // Should be one waiting
        _ <- pool.invalidate(conn.connection)
        _ <- fiber.join
      } yield ()).unsafeRunTimed(2.seconds) must_== Some(())
    }

    // this is a regression test for https://github.com/http4s/http4s/issues/2962
    "fail expired connections and then wake up a non-expired waiting connection on release" in {
      val timeout = 50.milliseconds
      (for {
        pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 3, requestTimeout = timeout)
        conn <- pool.borrow(key)
        waiting1 <- pool.borrow(key).start
        waiting2 <- pool.borrow(key).start
        _ <- IO.sleep(timeout + 20.milliseconds)
        waiting3 <- pool.borrow(key).start
        _ <- pool.release(conn.connection)
        result1 <- waiting1.join.void.attempt
        result2 <- waiting2.join.void.attempt
        result3 <- waiting3.join.void.attempt
      } yield (result1, result2, result3)).unsafeRunTimed(2.seconds) must beSome.like {
        case (result1, result2, result3) =>
          result1 must_== Left(WaitQueueTimeoutException)
          result2 must_== Left(WaitQueueTimeoutException)
          result3 must beRight
      }
    }
  }
}
