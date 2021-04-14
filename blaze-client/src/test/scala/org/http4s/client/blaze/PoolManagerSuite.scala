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

package org.http4s
package client
package blaze

import cats.effect._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.syntax.AllSyntax
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class PoolManagerSuite extends Http4sSuite with AllSyntax {
  val key = RequestKey(Uri.Scheme.http, Uri.Authority(host = Uri.Ipv4Address(ipv4"127.0.0.1")))
  val otherKey = RequestKey(Uri.Scheme.http, Uri.Authority(host = Uri.RegName("localhost")))

  class TestConnection extends Connection[IO] {
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
    ConnectionManager.pool(
      builder = _ => IO(new TestConnection()),
      maxTotal = maxTotal,
      maxWaitQueueLimit = maxWaitQueueLimit,
      maxConnectionsPerRequestKey = _ => 5,
      responseHeaderTimeout = Duration.Inf,
      requestTimeout = requestTimeout,
      executionContext = ExecutionContext.Implicits.global
    )

  test("A pool manager should wait up to maxWaitQueueLimit") {
    (for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 2)
      _ <- pool.borrow(key)
      _ <-
        Stream(Stream.eval(pool.borrow(key))).repeat
          .take(2)
          .covary[IO]
          .parJoinUnbounded
          .compile
          .toList
          .attempt
    } yield fail("Should have triggered timeout")).timeoutTo(2.seconds, IO.unit)
  }

  test("A pool manager should throw at maxWaitQueueLimit") {
    for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 2)
      _ <- pool.borrow(key)
      att <-
        Stream(Stream.eval(pool.borrow(key))).repeat
          .take(3)
          .covary[IO]
          .parJoinUnbounded
          .compile
          .toList
          .attempt
    } yield assert(att == Left(WaitQueueFullFailure()))
  }

  test("A pool manager should wake up a waiting connection on release") {
    for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 1)
      conn <- pool.borrow(key)
      fiber <- pool.borrow(key).start // Should be one waiting
      _ <- pool.release(conn.connection)
      _ <- fiber.join
    } yield ()
  }

  // this is a regression test for https://github.com/http4s/http4s/issues/2962
  test(
    "A pool manager should fail expired connections and then wake up a non-expired waiting connection on release") {
    val timeout = 50.milliseconds
    for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 3, requestTimeout = timeout)
      conn <- pool.borrow(key)
      waiting1 <- pool.borrow(key).void.start
      waiting2 <- pool.borrow(key).void.start
      _ <- IO.sleep(timeout + 20.milliseconds)
      waiting3 <- pool.borrow(key).void.start
      _ <- pool.release(conn.connection)
      result1 <- waiting1.join.void.attempt
      result2 <- waiting2.join.void.attempt
      result3 <- waiting3.join.void.attempt
    } yield {
      assert(result1 == Left(WaitQueueTimeoutException))
      assert(result2 == Left(WaitQueueTimeoutException))
      assert(result3.isRight)
    }
  }

  test("A pool manager should wake up a waiting connection on invalidate") {
    for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 1)
      conn <- pool.borrow(key)
      fiber <- pool.borrow(key).start // Should be one waiting
      _ <- pool.invalidate(conn.connection)
      _ <- fiber.join
    } yield ()
  }

  test("A pool manager should close an idle connection when at max total connections") {
    for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 1)
      conn <- pool.borrow(key)
      _ <- pool.release(conn.connection)
      fiber <- pool.borrow(otherKey).start
      _ <- fiber.join
    } yield ()
  }

  test(
    "A pool manager should wake up a waiting connection for a different request key on release") {
    for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 1)
      conn <- pool.borrow(key)
      fiber <- pool.borrow(otherKey).start
      _ <- pool.release(conn.connection)
      _ <- fiber.join
    } yield ()
  }

  test("A WaitQueueFullFailure should render message properly") {
    assert((new WaitQueueFullFailure).toString.contains("Wait queue is full"))
  }
}
