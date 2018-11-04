package org.http4s.server.middleware

import cats.effect.IO.ioEffect
import cats.effect.laws.util.TestContext
import cats.effect.{IO, Timer}
import cats.implicits._
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.server.middleware.Throttle._
import org.http4s.{Http4sSpec, HttpApp, Request, Status}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import scala.concurrent.duration._

class ThrottleSpec(implicit ee: ExecutionEnv) extends Http4sSpec with FutureMatchers {
  "LocalTokenBucket" should {

    "contain initial number of tokens equal to specified capacity" in {
      val ctx = TestContext()
      val testTimer: Timer[IO] = ctx.timer[IO]

      val someRefillTime = 1234.milliseconds
      val capacity = 5
      val createBucket =
        TokenBucket.local[IO](capacity, someRefillTime)(ioEffect, testTimer.clock)

      val takeExtraToken = createBucket
        .flatMap(testee => {
          val takeFiveTokens: IO[List[TokenAvailability]] =
            (1 to 5).toList.traverse(_ => testee.takeToken)
          val checkTokensUpToCapacity =
            takeFiveTokens.map(tokens =>
              tokens must contain(TokenAvailable: TokenAvailability).forall)
          checkTokensUpToCapacity *> testee.takeToken
        })

      val result = takeExtraToken.unsafeToFuture()

      result must haveClass[TokenUnavailable].await
    }

    "add another token at specified interval when not at capacity" in {
      val ctx = TestContext()
      val testTimer: Timer[IO] = ctx.timer[IO]

      val capacity = 1
      val createBucket =
        TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, testTimer.clock)

      val takeTokenAfterRefill = createBucket.flatMap(testee => {
        testee.takeToken *> testTimer.sleep(101.milliseconds) *> testee.takeToken
      })

      val result = takeTokenAfterRefill.unsafeToFuture()

      ctx.tick(101.milliseconds)

      result must beEqualTo(TokenAvailable).await
    }

    "not add another token at specified interval when at capacity" in {
      val ctx = TestContext()
      val testTimer: Timer[IO] = ctx.timer[IO]
      val capacity = 5
      val createBucket =
        TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, testTimer.clock)

      val takeExtraToken = createBucket.flatMap(testee => {
        val takeFiveTokens: IO[List[TokenAvailability]] = (1 to 5).toList.traverse(_ => {
          testee.takeToken
        })
        testTimer.sleep(300.milliseconds) >> takeFiveTokens >> testee.takeToken
      })

      val result = takeExtraToken.unsafeToFuture()

      ctx.tick(300.milliseconds)

      result must haveClass[TokenUnavailable].await
    }

    "only return a single token when only one token available and there are multiple concurrent requests" in {
      val ctx = TestContext()
      val testTimer: Timer[IO] = ctx.timer[IO]
      val capacity = 1
      val createBucket =
        TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, testTimer.clock)

      val takeTokensSimultaneously = createBucket.flatMap(testee => {
        (1 to 5).toList.parTraverse(_ => testee.takeToken)
      })

      val result = takeTokensSimultaneously.unsafeToFuture()

      result must contain(TokenAvailable: TokenAvailability).exactly(1.times).await
    }

    "return the time until the next token is available when no token is available" in {
      val ctx = TestContext()
      val testTimer: Timer[IO] = ctx.timer[IO]
      val capacity = 1
      val createBucket =
        TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, testTimer.clock)

      val takeTwoTokens = createBucket.flatMap(testee => {
        testee.takeToken *> testTimer.sleep(75.milliseconds) *> testee.takeToken
      })

      val result = takeTwoTokens.unsafeToFuture()

      ctx.tick(75.milliseconds)

      result must beEqualTo(TokenUnavailable(Some(25.milliseconds))).await
    }
  }

  "Throttle" should {
    val alwaysOkApp = HttpApp[IO] { _ =>
      Ok()
    }

    "allow a request to proceed when the rate limit has not been reached" in {
      val limitNotReachedBucket = new TokenBucket[IO] {
        override def takeToken: IO[TokenAvailability] = TokenAvailable.pure[IO]
      }

      val testee = Throttle(limitNotReachedBucket)(alwaysOkApp)
      val req = Request[IO](uri = uri("/"))

      testee(req) must returnStatus(Status.Ok)
    }

    "deny a request when the rate limit had been reached" in {
      val limitReachedBucket = new TokenBucket[IO] {
        override def takeToken: IO[TokenAvailability] = TokenUnavailable(None).pure[IO]
      }

      val testee = Throttle(limitReachedBucket)(alwaysOkApp)
      val req = Request[IO](uri = uri("/"))

      testee(req) must returnStatus(Status.TooManyRequests)
    }
  }
}
