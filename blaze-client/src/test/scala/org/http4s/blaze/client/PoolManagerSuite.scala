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
package blaze
package client

import cats.effect._
import cats.effect.std._
import cats.implicits._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.client.ConnectionFailure
import org.http4s.client.RequestKey
import org.http4s.syntax.AllSyntax

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class PoolManagerSuite extends Http4sSuite with AllSyntax {
  val key = RequestKey(Uri.Scheme.http, Uri.Authority(host = Uri.Ipv4Address(ipv4"127.0.0.1")))
  val otherKey = RequestKey(Uri.Scheme.http, Uri.Authority(host = Uri.RegName("localhost")))

  class TestConnection extends Connection[IO] {
    @volatile var isClosed = false
    def isRecyclable = true
    def requestKey = key
    def shutdown() =
      isClosed = true
  }

  private def mkPool(
      maxTotal: Int,
      maxWaitQueueLimit: Int = 10,
      requestTimeout: Duration = Duration.Inf,
      builder: ConnectionBuilder[IO, TestConnection] = _ => IO(new TestConnection()),
      maxIdleDuration: Duration = Duration.Inf,
  ) =
    ConnectionManager.pool(
      builder = builder,
      maxTotal = maxTotal,
      maxWaitQueueLimit = maxWaitQueueLimit,
      maxConnectionsPerRequestKey = _ => 5,
      responseHeaderTimeout = Duration.Inf,
      requestTimeout = requestTimeout,
      executionContext = ExecutionContext.Implicits.global,
      maxIdleDuration = maxIdleDuration,
    )

  test("A pool manager should wait up to maxWaitQueueLimit") {
    (for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 2)
      _ <- pool.borrow(key)
      _ <-
        Stream(Stream.eval(pool.borrow(key))).repeat
          .take(2)
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
          .parJoinUnbounded
          .compile
          .toList
          .attempt
    } yield assertEquals(att, Left(WaitQueueFullFailure()))
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
    "A pool manager should fail expired connections and then wake up a non-expired waiting connection on release"
  ) {
    val timeout = 50.milliseconds
    for {
      pool <- mkPool(maxTotal = 1, maxWaitQueueLimit = 3, requestTimeout = timeout)
      conn <- pool.borrow(key)
      waiting1 <- pool.borrow(key).void.start
      waiting2 <- pool.borrow(key).void.start
      _ <- IO.sleep(timeout + 20.milliseconds)
      waiting3 <- pool.borrow(key).void.start
      _ <- pool.release(conn.connection)
      result1 <- waiting1.join
      result2 <- waiting2.join
      result3 <- waiting3.join
    } yield {
      assertEquals(result1, Outcome.errored[IO, Throwable, Unit](WaitQueueTimeoutException))
      assertEquals(result2, Outcome.errored[IO, Throwable, Unit](WaitQueueTimeoutException))
      assertEquals(result3, Outcome.succeeded[IO, Throwable, Unit](IO.unit))
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
    "A pool manager should wake up a waiting connection for a different request key on release"
  ) {
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

  test("A pool manager should continue processing waitQueue after allocation failure".fail) {
    for {
      isEstablishingConnectionsPossible <- Ref[IO].of(true)
      connectionFailure = new ConnectionFailure(key, new InetSocketAddress(1234), new Exception())
      pool <- mkPool(
        maxTotal = 1,
        maxWaitQueueLimit = 10,
        builder = _ =>
          isEstablishingConnectionsPossible.get
            .ifM(IO(new TestConnection()), IO.raiseError(connectionFailure)),
      )
      conn1 <- pool.borrow(key)
      conn2Fiber <- pool.borrow(key).start
      conn3Fiber <- pool.borrow(key).start
      _ <- IO.sleep(50.millis) // Give the fibers some time to end up in the waitQueue
      _ <- isEstablishingConnectionsPossible.set(false)
      _ <- pool.invalidate(conn1.connection)
      _ <- conn2Fiber.join
        .as(false)
        .recover { case _: ConnectionFailure => true }
        .assert
        .timeout(200.millis)
      _ <- conn3Fiber.join
        .as(false)
        .recover { case _: ConnectionFailure => true }
        .assert
        .timeout(200.millis)
      // After failing to allocate conn2, the pool should attempt to allocate the conn3,
      // but it doesn't so we hit the timeoeut. Without the timeout it would be a deadlock.
    } yield ()
  }

  test(
    "A pool manager should not deadlock after an attempt to create a connection is canceled".fail
  ) {
    for {
      isEstablishingConnectionsHangs <- Ref[IO].of(true)
      connectionAttemptsStarted <- Semaphore[IO](0L)
      pool <- mkPool(
        maxTotal = 1,
        maxWaitQueueLimit = 10,
        builder = _ =>
          connectionAttemptsStarted.release >>
            isEstablishingConnectionsHangs.get.ifM(IO.never, IO(new TestConnection())),
      )
      conn1Fiber <- pool.borrow(key).start
      // wait for the first connection attempt to start before we cancel it
      _ <- connectionAttemptsStarted.acquire
      _ <- conn1Fiber.cancel
      _ <- isEstablishingConnectionsHangs.set(false)
      // The first connection attempt is canceled, so it should now be possible to acquire a new connection (but it's not because curAllocated==1==maxTotal)
      _ <- pool.borrow(key).timeout(200.millis)
    } yield ()
  }

  test("Should reissue recyclable connections with infinite maxIdleDuration") {
    for {
      pool <- mkPool(
        maxTotal = 1,
        maxIdleDuration = Duration.Inf,
      )
      conn1 <- pool.borrow(key)
      _ <- pool.release(conn1.connection)
      conn2 <- pool.borrow(key)
    } yield assertEquals(conn1.connection, conn2.connection)
  }

  test("Should not reissue recyclable connections before maxIdleDuration") {
    for {
      pool <- mkPool(
        maxTotal = 1,
        maxIdleDuration = 365.days,
      )
      conn1 <- pool.borrow(key)
      _ <- pool.release(conn1.connection)
      conn2 <- pool.borrow(key)
    } yield assertEquals(conn1.connection, conn2.connection)
  }

  test("Should not reissue recyclable connections beyond maxIdleDuration") {
    for {
      pool <- mkPool(
        maxTotal = 1,
        maxIdleDuration = Duration.Zero,
      )
      conn1 <- pool.borrow(key)
      _ <- pool.release(conn1.connection)
      conn2 <- pool.borrow(key)
    } yield assert(conn1.connection != conn2.connection)
  }

  test("Should close connections borrowed beyond maxIdleDuration") {
    for {
      pool <- mkPool(
        maxTotal = 1,
        maxIdleDuration = Duration.Zero,
      )
      conn1 <- pool.borrow(key)
      _ <- pool.release(conn1.connection)
      _ <- pool.borrow(key)
    } yield assert(conn1.connection.isClosed)
  }
}
