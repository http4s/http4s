package org.http4s.ember.core

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import cats.implicits._
import cats.effect.IO
import org.http4s._

import org.http4s.testing.ArbitraryInstances._

class TraversalSpec extends Specification with ScalaCheck {
  "Request Encoder/Parser" should {
    "preserve headers" >> prop { req: Request[IO] =>
      val end = Parser.Request.parser[IO](Int.MaxValue)(
        Encoder.reqToBytes[IO](req)
      ).unsafeRunSync

      end.headers must_=== req.headers
    }.pendingUntilFixed

    "preserve method with known uri" >> prop { req: Request[IO] =>
      val newReq = req
        .withUri(Uri.unsafeFromString("http://www.google.com"))

      val end = Parser.Request.parser[IO](Int.MaxValue)(
        Encoder.reqToBytes[IO](newReq)
      ).unsafeRunSync

      end.method must_=== req.method
    }

    "preserve uri.scheme" >> prop { req: Request[IO] =>
      val end = Parser.Request.parser[IO](Int.MaxValue)(
        Encoder.reqToBytes[IO](req)
      ).unsafeRunSync

      end.uri.scheme must_=== req.uri.scheme
    }.pendingUntilFixed

    "preserve body with a known uri" >> prop {
      (req: Request[IO], s: String) =>
      val newReq = req
        .withUri(Uri.unsafeFromString("http://www.google.com"))
        .withEntity(s)
      val end = Parser.Request.parser[IO](Int.MaxValue)(
        Encoder.reqToBytes[IO](newReq)
      ).unsafeRunSync

      end.body.through(fs2.text.utf8Decode)
      .compile
      .foldMonoid
      .unsafeRunSync must_=== s
    }
  }
}