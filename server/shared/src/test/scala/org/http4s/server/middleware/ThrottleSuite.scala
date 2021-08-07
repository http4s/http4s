/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.server.middleware

import cats.effect.IO
import cats.implicits._
import org.http4s.{Http4sSuite, HttpApp, Request, Status}
import org.http4s.syntax.all._
import org.http4s.dsl.io._
import org.http4s.server.middleware.Throttle._
import scala.concurrent.duration._
import cats.effect.testkit.TestContext

class ThrottleSuite extends Http4sSuite {
  test("LocalTokenBucket should contain initial number of tokens equal to specified capacity") {
    // val ctx = TestContext()

    val someRefillTime = 1234.milliseconds
    val capacity = 5
    val createBucket =
      TokenBucket.local[IO](capacity, someRefillTime)

    createBucket.flatMap { testee =>
      val takeFiveTokens: IO[List[TokenAvailability]] =
        (1 to 5).toList.traverse(_ => testee.takeToken)
      val checkTokensUpToCapacity =
        takeFiveTokens.map(tokens => tokens.exists(_ == TokenAvailable))
      (checkTokensUpToCapacity, testee.takeToken.map(_.isInstanceOf[TokenUnavailable]))
        .mapN(_ && _)
    }.assert
  }

  test("LocalTokenBucket should add another token at specified interval when not at capacity") {
    val ctx = TestContext()

    val capacity = 1
    val createBucket =
      TokenBucket.local[IO](capacity, 100.milliseconds)

    val takeTokenAfterRefill = createBucket.flatMap { testee =>
      testee.takeToken *> IO.sleep(101.milliseconds) *>
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
      TokenBucket.local[IO](capacity, 100.milliseconds)

    val takeExtraToken = createBucket.flatMap { testee =>
      val takeFiveTokens: IO[List[TokenAvailability]] = (1 to 5).toList.traverse { _ =>
        testee.takeToken
      }
      IO.sleep(300.milliseconds) >> takeFiveTokens >> testee.takeToken
    }

    takeExtraToken
      .map { result =>
        ctx.tick(300.milliseconds)
        result
      }
      .map(_.isInstanceOf[TokenUnavailable])
      .assert
  }

  test(
    "LocalTokenBucket should only return a single token when only one token available and there are multiple concurrent requests") {
    val capacity = 1
    val createBucket =
      TokenBucket.local[IO](capacity, 100.milliseconds)

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
    "LocalTokenBucket should return the time until the next token is available when no token is available".flaky) {
    val ctx = TestContext()
    val capacity = 1
    val createBucket =
      TokenBucket.local[IO](capacity, 100.milliseconds)

    val takeTwoTokens = createBucket.flatMap { testee =>
      testee.takeToken *> IO.sleep(75.milliseconds) *> testee.takeToken
    }

    takeTwoTokens.map { result =>
      ctx.tick(75.milliseconds)
      result match {
        case TokenUnavailable(t) => t.exists(_ <= 25.milliseconds)
        case _ => false
      }
    }.assert
  }
  val alwaysOkApp = HttpApp[IO] { _ =>
    Ok()
  }

  test("Throttle / should allow a request to proceed when the rate limit has not been reached") {
    val limitNotReachedBucket = new TokenBucket[IO] {
      override def takeToken: IO[TokenAvailability] = TokenAvailable.pure[IO]
    }

    val testee = Throttle(limitNotReachedBucket, defaultResponse[IO] _)(alwaysOkApp)
    val req = Request[IO](uri = uri"/")

    testee(req).map(_.status === Status.Ok).assert
  }

  test(" Throttle / should deny a request when the rate limit had been reached") {
    val limitReachedBucket = new TokenBucket[IO] {
      override def takeToken: IO[TokenAvailability] = TokenUnavailable(None).pure[IO]
    }

    val testee = Throttle(limitReachedBucket, defaultResponse[IO] _)(alwaysOkApp)
    val req = Request[IO](uri = uri"/")

    testee(req).map(_.status === Status.TooManyRequests).assert
  }
}
