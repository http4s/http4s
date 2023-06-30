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

import cats.data._
import cats.effect._
import fs2.Chunk
import fs2.Stream
import org.http4s._
import org.http4s.server._
import org.http4s.server.middleware.BracketRequestResponse.ReleaseError
import scodec.bits.ByteVector

final class BracketRequestResponseSuite extends Http4sSuite {

  test(
    "When no errors occur, acquire, release, or the middleware should yield the expected values"
  ) {
    for {
      acquireRef <- Ref.of[IO, Long](0L)
      releaseRef <- Ref.of[IO, Long](0L)
      middleware =
        BracketRequestResponse.bracketRequestResponseCaseRoutes[IO, Long](
          acquireRef.updateAndGet(_ + 1L)
        ) { case (_, oc) =>
          IO(assert(oc.isSuccess)) *> releaseRef.update(_ + 1L)
        }
      routes = middleware(
        Kleisli((contextRequest: ContextRequest[IO, Long]) =>
          OptionT.liftF(
            IO(Response(status = Status.Ok).withEntity(contextRequest.context.toString))
          )
        )
      )
      response <- routes.run(Request[IO]()).getOrElseF(IO(fail("Got None for response")))
      acquireCount <- acquireRef.get
      responseBody <- response.as[String]
      releaseCount <- releaseRef.get
      responseValue <- IO(responseBody.toLong)
    } yield {
      assertEquals(acquireCount, 1L)
      assertEquals(releaseCount, 1L)
      assertEquals(responseValue, 1L)
    }
  }

  test("When an error occurs acquire/release should still all execute correctly") {
    for {
      acquireRef <- Ref.of[IO, Long](0L)
      releaseRef <- Ref.of[IO, Long](0L)
      error = new RuntimeException
      middleware =
        BracketRequestResponse.bracketRequestResponseCaseRoutes[IO, Long](
          acquireRef.updateAndGet(_ + 1L)
        ) { case (_, oc) =>
          IO(assert(oc.isError)) *> releaseRef.update(_ + 1L)
        }
      routes = middleware(Kleisli(Function.const(OptionT.liftF(IO.raiseError(error)))))
      response <- routes.run(Request[IO]()).value.attempt
      acquireCount <- acquireRef.get
      releaseCount <- releaseRef.get
    } yield {
      assertEquals(acquireCount, 1L)
      assertEquals(releaseCount, 1L)
      assertEquals(response, Left(error))
    }
  }

  test("When no response is given, acquire/release should still all execute correctly") {
    for {
      acquireRef <- Ref.of[IO, Long](0L)
      releaseRef <- Ref.of[IO, Long](0L)
      middleware =
        BracketRequestResponse.bracketRequestResponseCaseRoutes[IO, Long](
          acquireRef.updateAndGet(_ + 1L)
        ) { case (_, oc) =>
          IO(assert(oc.isSuccess)) *> releaseRef.update(_ + 1L)
        }
      routes = middleware(Kleisli(Function.const(OptionT.none)))
      response <- routes.run(Request[IO]()).value
      acquireCount <- acquireRef.get
      releaseCount <- releaseRef.get
    } yield {
      assertEquals(acquireCount, 1L)
      assertEquals(releaseCount, 1L)
      assertEquals(response, None)
    }
  }

  private def testConcurrentRequestsRunning(
      mkEntity: Long => Entity[IO],
      isStreamedEntity: Boolean,
  ): Unit = {
    val testNamePostfix =
      if (isStreamedEntity)
        "for Streamed response body"
      else
        "for non-Streamed response body"

    test(
      s"When more than one request is running at a time, release Refs should reflect the current state $testNamePostfix"
    ) {
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        middleware =
          BracketRequestResponse.bracketRequestResponseCaseRoutes[IO, Long](
            acquireRef.updateAndGet(_ + 1L)
          ) { case (_, oc) =>
            IO(assert(oc.isSuccess)) *> releaseRef.update(_ + 1L)
          }
        routes = middleware(
          Kleisli((contextRequest: ContextRequest[IO, Long]) =>
            OptionT.liftF(
              IO(
                Response(
                  status = Status.Ok,
                  entity = mkEntity(contextRequest.context),
                )
              )
            )
          )
        ).orNotFound
        // T0
        acquireCount0 <- acquireRef.get
        releaseCount0 <- releaseRef.get
        // T1
        response1 <- routes.run(Request[IO]())
        acquireCount1 <- acquireRef.get
        releaseCount1 <- releaseRef.get
        // T2
        response2 <- routes.run(Request[IO]())
        acquireCount2 <- acquireRef.get
        releaseCount2 <- releaseRef.get
        // T3
        responseBody3 <- response1.as[String]
        responseValue3 <- IO(responseBody3.toLong)
        acquireCount3 <- acquireRef.get
        releaseCount3 <- releaseRef.get
        // T4
        responseBody4 <- response2.as[String]
        responseValue4 <- IO(responseBody4.toLong)
        acquireCount4 <- acquireRef.get
        releaseCount4 <- releaseRef.get
      } yield {
        // T0
        assertEquals(acquireCount0, 0L)
        assertEquals(releaseCount0, 0L)
        // T1
        assertEquals(response1.status, Status.Ok)
        assertEquals(acquireCount1, 1L)
        assertEquals(releaseCount1, if (isStreamedEntity) 0L else 1L)
        // T2
        assertEquals(response2.status, Status.Ok)
        assertEquals(acquireCount2, 2L)
        assertEquals(releaseCount2, if (isStreamedEntity) 0L else 2L)
        // T3
        assertEquals(responseValue3, 1L)
        assertEquals(acquireCount3, 2L)
        assertEquals(releaseCount3, if (isStreamedEntity) 1L else 2L)
        // T4
        assertEquals(responseValue4, 2L)
        assertEquals(acquireCount4, 2L)
        assertEquals(releaseCount4, 2L)
      }
    }
  }

  testConcurrentRequestsRunning(
    mkEntity = context =>
      Entity.Streamed(
        fs2.Stream
          .eval(IO.delay(Chunk.array(context.toString.getBytes)))
          .unchunks,
        None,
      ),
    isStreamedEntity = true,
  )

  testConcurrentRequestsRunning(
    mkEntity = context => Entity.Strict(ByteVector.view(context.toString.getBytes)),
    isStreamedEntity = false,
  )

  test(
    "When an error occurs during processing of the Response body, acquire/release still execute"
  ) {
    for {
      acquireRef <- Ref.of[IO, Long](0L)
      releaseRef <- Ref.of[IO, Long](0L)
      error = new AssertionError
      middleware =
        BracketRequestResponse.bracketRequestResponseCaseRoutes[IO, Long](
          acquireRef.updateAndGet(_ + 1L)
        ) { case (_, oc) =>
          IO(assert(oc.isError)) *> releaseRef.update(_ + 1L)
        }
      entity = Entity.stream(Stream.raiseError[IO](error))
      routes = middleware(
        Kleisli(
          Function.const(
            OptionT.liftF(
              IO(Response(status = Status.Ok).withEntity(entity))
            )
          )
        )
      ).orNotFound
      response <- routes.run(Request[IO]())
      acquireCount <- acquireRef.get
      responseBody <- response.as[String].attempt
      releaseCount <- releaseRef.get
    } yield {
      assertEquals(acquireCount, 1L)
      assertEquals(releaseCount, 1L)
      assertEquals(responseBody, Left(error))
    }
  }

  test("When an error occurs during acquire, release should not run") {
    val error: Throwable = new AssertionError
    val middleware: ContextMiddleware[IO, Unit] =
      BracketRequestResponse.bracketRequestResponseCaseRoutes[IO, Unit](
        IO.raiseError[Unit](error)
      ) { case _ =>
        IO(fail("Release should not execute")).void
      }
    val routes: HttpRoutes[IO] = middleware(Kleisli(Function.const(OptionT.none)))
    for {
      response <- routes.run(Request[IO]()).value.attempt
    } yield assertEquals(response, Left(error))
  }

  private def testReleaseCountAttempts(
      mkEntity: Long => Entity[IO],
      isStreamedEntity: Boolean,
  ): Unit = {
    val testNamePostfix =
      if (isStreamedEntity)
        "for Streamed response body"
      else
        "for non-Streamed response body"

    test(
      s"When an error occurs during release, acquire should execute correctly and release should only be attempted once $testNamePostfix"
    ) {
      val error: Throwable = new AssertionError
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        middleware =
          BracketRequestResponse.bracketRequestResponseCaseRoutes[IO, Long](
            acquireRef.updateAndGet(_ + 1L)
          ) { case (_, oc) =>
            IO(assert(oc.isSuccess)) *>
              releaseRef
                .update(_ + 1L) *> IO
                .raiseError[Unit](error)
          }
        routes = middleware(
          Kleisli((contextRequest: ContextRequest[IO, Long]) =>
            OptionT.liftF(
              IO(
                Response(
                  status = Status.Ok,
                  entity = mkEntity(contextRequest.context),
                )
              )
            )
          )
        ).orNotFound
        response <- routes.run(Request[IO]()).attempt
        acquireCount <- acquireRef.get
        responseBody <- response.fold(err => IO(Left(err)), _.as[String].attempt)
        releaseCount <- releaseRef.get
      } yield {
        assertEquals(acquireCount, 1L)
        assertEquals(releaseCount, 1L)
        assertEquals(
          responseBody,
          if (isStreamedEntity) Left(error) else Left(ReleaseError(error)),
        )
      }
    }
  }

  testReleaseCountAttempts(
    mkEntity = context =>
      Entity.Streamed(
        fs2.Stream
          .eval(IO.delay(Chunk.array(context.toString.getBytes)))
          .unchunks,
        None,
      ),
    isStreamedEntity = true,
  )

  testReleaseCountAttempts(
    mkEntity = context => Entity.Strict(ByteVector.view(context.toString.getBytes)),
    isStreamedEntity = false,
  )
}
