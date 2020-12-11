/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core

class TraversalSpecItsNotYouItsMe

/* FIXME Restore after #3935 is worked out
import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import cats.syntax.all._
import cats.effect.IO
import org.http4s._
// import _root_.io.chrisdavenport.log4cats.testing.TestingLogger
import org.http4s.laws.discipline.ArbitraryInstances._
import scala.concurrent.ExecutionContext

class TraversalSpec extends Specification with ScalaCheck {
  implicit val CS = IO.contextShift(ExecutionContext.global)
  "Request Encoder/Parser" should {
    "preserve existing headers" >> skipOnCI {
      prop { (req: Request[IO]) =>
        val end = Parser.Request
          .parser[IO](Int.MaxValue)(
            Encoder.reqToBytes[IO](req)
          ) //(logger)
          .unsafeRunSync()

        end.headers.toList must containAllOf(req.headers.toList)
      }
    }

    "preserve method with known uri" >> prop { (req: Request[IO]) =>
      // val logger = TestingLogger.impl[IO]()
      val newReq = req
        .withUri(Uri.unsafeFromString("http://www.google.com"))

      val end = Parser.Request
        .parser[IO](Int.MaxValue)(
          Encoder.reqToBytes[IO](newReq)
        ) //(logger)
        .unsafeRunSync()

      end.method must_=== req.method
    }

    "preserve uri.scheme" >> prop { (req: Request[IO]) =>
      // val logger = TestingLogger.impl[IO]()
      val end = Parser.Request
        .parser[IO](Int.MaxValue)(
          Encoder.reqToBytes[IO](req)
        ) //(logger)
        .unsafeRunSync()

      end.uri.scheme must_=== req.uri.scheme
    }

    "preserve body with a known uri" >> prop { (req: Request[IO], s: String) =>
      // val logger = TestingLogger.impl[IO]()
      val newReq = req
        .withUri(Uri.unsafeFromString("http://www.google.com"))
        .withEntity(s)
      val end = Parser.Request
        .parser[IO](Int.MaxValue)(
          Encoder.reqToBytes[IO](newReq)
        ) //(logger)
        .unsafeRunSync()

      end.body.through(fs2.text.utf8Decode).compile.foldMonoid.unsafeRunSync() must_=== s
    }
  }
}
 */
