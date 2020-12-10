/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.effect.IO.ioEffect
import cats.effect.laws.util.TestContext
import cats.effect.{IO, Timer}
import cats.implicits._
import org.http4s.{Http4sSuite, HttpApp, Request, Status}
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.server.middleware.Throttle._
import scala.concurrent.duration._

class ThrottleSuite extends Http4sSuite {
  test("LocalTokenBucket should contain initial number of tokens equal to specified capacity") {
    val ctx = TestContext()
    val munitTimer: Timer[IO] = ctx.timer[IO]

    val someRefillTime = 1234.milliseconds
    val capacity = 5
    val createBucket =
      TokenBucket.local[IO](capacity, someRefillTime)(ioEffect, munitTimer.clock)

    createBucket
      .flatMap { testee =>
        val takeFiveTokens: IO[List[TokenAvailability]] =
          (1 to 5).toList.traverse(_ => testee.takeToken)
        val checkTokensUpToCapacity =
          takeFiveTokens.map(tokens => tokens.exists(_ == TokenAvailable))
        (checkTokensUpToCapacity, testee.takeToken.map(_.isInstanceOf[TokenUnavailable]))
          .mapN(_ && _)
      }
      .assertEquals(true)
  }

  test("LocalTokenBucket should add another token at specified interval when not at capacity") {
    val ctx = TestContext()

    val capacity = 1
    val createBucket =
      TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, munitTimer.clock)

    val takeTokenAfterRefill = createBucket.flatMap { testee =>
      testee.takeToken *> munitTimer.sleep(101.milliseconds) *>
        testee.takeToken
    }

    takeTokenAfterRefill
      .map { result =>
        ctx.tick(101.milliseconds)
        result
      }
      .assertEquals(TokenAvailable)
  }

  test("LocalTokenBucket should not add another token at specified interval when at capacity") {
    val ctx = TestContext()
    val capacity = 5
    val createBucket =
      TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, munitTimer.clock)

    val takeExtraToken = createBucket.flatMap { testee =>
      val takeFiveTokens: IO[List[TokenAvailability]] = (1 to 5).toList.traverse { _ =>
        testee.takeToken
      }
      munitTimer.sleep(300.milliseconds) >> takeFiveTokens >> testee.takeToken
    }

    takeExtraToken
      .map { result =>
        ctx.tick(300.milliseconds)
        result
      }
      .map(_.isInstanceOf[TokenUnavailable])
      .assertEquals(true)
  }

  test(
    "LocalTokenBucket should only return a single token when only one token available and there are multiple concurrent requests") {
    val capacity = 1
    val createBucket =
      TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, munitTimer.clock)

    val takeTokensSimultaneously = createBucket.flatMap { testee =>
      (1 to 5).toList.parTraverse(_ => testee.takeToken)
    }

    takeTokensSimultaneously
      .map { result =>
        result.count(_ == TokenAvailable)
      }
      .assertEquals(1)
  }

  test(
    "LocalTokenBucket should return the time until the next token is available when no token is available") {
    val ctx = TestContext()
    val capacity = 1
    val createBucket =
      TokenBucket.local[IO](capacity, 100.milliseconds)(ioEffect, munitTimer.clock)

    val takeTwoTokens = createBucket.flatMap { testee =>
      testee.takeToken *> munitTimer.sleep(75.milliseconds) *> testee.takeToken
    }

    takeTwoTokens
      .map { result =>
        ctx.tick(75.milliseconds)
        result match {
          case TokenUnavailable(t) => t.exists(_ <= 25.milliseconds)
          case _ => false
        }
      }
      .assertEquals(true)
  }
  val alwaysOkApp = HttpApp[IO] { _ =>
    Ok()
  }

  test("Throttle / should allow a request to proceed when the rate limit has not been reached") {
    val limitNotReachedBucket = new TokenBucket[IO] {
      override def takeToken: IO[TokenAvailability] = TokenAvailable.pure[IO]
    }

    val testee = Throttle(limitNotReachedBucket, defaultResponse[IO] _)(alwaysOkApp)
    val req = Request[IO](uri = uri("/"))

    testee(req).map(_.status === Status.Ok).assertEquals(true)
  }

  test(" Throttle / should deny a request when the rate limit had been reached") {
    val limitReachedBucket = new TokenBucket[IO] {
      override def takeToken: IO[TokenAvailability] = TokenUnavailable(None).pure[IO]
    }

    val testee = Throttle(limitReachedBucket, defaultResponse[IO] _)(alwaysOkApp)
    val req = Request[IO](uri = uri("/"))

    testee(req).map(_.status === Status.TooManyRequests).assertEquals(true)
  }
}
