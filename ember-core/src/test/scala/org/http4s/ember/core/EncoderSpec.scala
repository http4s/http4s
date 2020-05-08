/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import org.specs2.mutable.Specification
import cats.implicits._
import cats.effect.{IO, Sync}
import org.http4s._

class EncoderSpec extends Specification {
  private object Helpers {
    def stripLines(s: String): String = s.replace("\r\n", "\n")

    // Only for Use with Text Requests
    def encodeRequestRig[F[_]: Sync](req: Request[F]): F[String] =
      Encoder
        .reqToBytes(req)
        .through(fs2.text.utf8Decode[F])
        .compile
        .foldMonoid
        .map(stripLines)

    // Only for Use with Text Requests
    def encodeResponseRig[F[_]: Sync](resp: Response[F]): F[String] =
      Encoder
        .respToBytes(resp)
        .through(fs2.text.utf8Decode[F])
        .compile
        .foldMonoid
        .map(stripLines)
  }

  "Encoder.reqToBytes" should {
    "encode a no body request correctly" in {
      val req = Request[IO](Method.GET, Uri.unsafeFromString("http://www.google.com"))
      val expected =
        """GET http://www.google.com HTTP/1.1
      |Host: www.google.com
      |
      |""".stripMargin

      Helpers.encodeRequestRig(req).unsafeRunSync must_=== expected
    }

    "encode a request with a body correctly" in {
      val req = Request[IO](Method.POST, Uri.unsafeFromString("http://www.google.com"))
        .withEntity("Hello World!")
      val expected =
        """POST http://www.google.com HTTP/1.1
      |Host: www.google.com
      |Content-Length: 12
      |Content-Type: text/plain; charset=UTF-8
      |
      |Hello World!""".stripMargin

      Helpers.encodeRequestRig(req).unsafeRunSync must_=== expected
    }
  }

  "Encoder.respToBytes" should {
    "encode a no body response correctly" in {
      val resp = Response[IO](Status.Ok)

      val expected =
        """HTTP/1.1 200 OK
      |
      |""".stripMargin

      Helpers.encodeResponseRig(resp).unsafeRunSync must_=== expected
    }

    "encode a response with a body correctly" in {
      val resp = Response[IO](Status.NotFound)
        .withEntity("Not Found")

      val expected =
        """HTTP/1.1 404 Not Found
      |Content-Length: 9
      |Content-Type: text/plain; charset=UTF-8
      |
      |Not Found""".stripMargin

      Helpers.encodeResponseRig(resp).unsafeRunSync must_=== expected
    }
  }
}
