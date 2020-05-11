/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import cats.implicits._
import cats.effect.IO
import org.http4s._
import _root_.io.chrisdavenport.log4cats.testing.TestingLogger
import org.http4s.laws.discipline.ArbitraryInstances._

class TraversalSpec extends Specification with ScalaCheck {
  "Request Encoder/Parser" should {
    "preserve headers" >> prop { (req: Request[IO]) =>
      val logger = TestingLogger.impl[IO]()
      val end = Parser.Request
        .parser[IO](Int.MaxValue)(
          Encoder.reqToBytes[IO](req)
        )(logger)
        .unsafeRunSync

      end.headers must_=== req.headers
    }.pendingUntilFixed

    "preserve method with known uri" >> prop { (req: Request[IO]) =>
      val logger = TestingLogger.impl[IO]()
      val newReq = req
        .withUri(Uri.unsafeFromString("http://www.google.com"))

      val end = Parser.Request
        .parser[IO](Int.MaxValue)(
          Encoder.reqToBytes[IO](newReq)
        )(logger)
        .unsafeRunSync

      end.method must_=== req.method
    }

    "preserve uri.scheme" >> prop { (req: Request[IO]) =>
      val logger = TestingLogger.impl[IO]()
      val end = Parser.Request
        .parser[IO](Int.MaxValue)(
          Encoder.reqToBytes[IO](req)
        )(logger)
        .unsafeRunSync

      end.uri.scheme must_=== req.uri.scheme
    }.pendingUntilFixed

    "preserve body with a known uri" >> prop { (req: Request[IO], s: String) =>
      val logger = TestingLogger.impl[IO]()
      val newReq = req
        .withUri(Uri.unsafeFromString("http://www.google.com"))
        .withEntity(s)
      val end = Parser.Request
        .parser[IO](Int.MaxValue)(
          Encoder.reqToBytes[IO](newReq)
        )(logger)
        .unsafeRunSync

      end.body.through(fs2.text.utf8Decode).compile.foldMonoid.unsafeRunSync must_=== s
    }
  }
}
