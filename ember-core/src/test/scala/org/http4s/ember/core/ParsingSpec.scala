package org.http4s.ember.core

import org.specs2.mutable.Specification
import cats.effect.{IO, Sync}
import org.http4s._
import fs2.Stream

class ParsingSpec extends Specification {
  private object Helpers {

    def stripLines(s: String): String = s.replace("\r\n", "\n")
    def httpifyString(s: String): String = s.replace("\n", "\r\n")

    // Only for Use with Text Requests
    def parseRequestRig[F[_]: Sync](s: String): F[Request[F]] = {
      val byteStream: Stream[F, Byte] = Stream
        .emit(s)
        .covary[F]
        .map(httpifyString)
        .through(fs2.text.utf8Encode[F])

      Parser.Request.parser[F](Int.MaxValue)(byteStream)
    }

    def parseResponseRig[F[_]: Sync](s: String): F[Response[F]] = {
      val byteStream: Stream[F, Byte] = Stream
        .emit(s)
        .covary[F]
        .map(httpifyString)
        .through(fs2.text.utf8Encode[F])

      Parser.Response.parser[F](Int.MaxValue)(byteStream)
    }
  }
  "Parser.Request.parser" should {
    "Parse a request with no body correctly" in {
      val raw =
        """GET / HTTP/1.1
      |Host: www.google.com
      |
      |""".stripMargin
      val expected = Request[IO](Method.GET, Uri.unsafeFromString("www.google.com"))

      val result = Helpers.parseRequestRig[IO](raw).unsafeRunSync

      result.method must_=== expected.method
      result.uri.scheme must_=== expected.uri.scheme
      // result.uri.authority must_=== expected.uri.authority
      // result.uri.path must_=== expected.uri.path
      // result.uri.query must_=== expected.uri.query
      result.uri.fragment must_=== expected.uri.fragment
      result.headers must_=== expected.headers
      result.body.compile.toVector.unsafeRunSync must_=== expected.body.compile.toVector.unsafeRunSync
    }

    "Parse a request with a body correctly" in {
      val raw =
        """POST /foo HTTP/1.1
      |Content-Type: text/plain; charset=UTF-8
      |Content-Length: 11
      |
      |Entity Here""".stripMargin
      val expected = Request[IO](Method.POST, Uri.unsafeFromString("/foo"))
        .withEntity("Entity Here")

      val result = Helpers.parseRequestRig[IO](raw).unsafeRunSync

      result.method must_=== expected.method
      result.uri.scheme must_=== expected.uri.scheme
      // result.uri.authority must_=== expected.uri.authority
      // result.uri.path must_=== expected.uri.path
      // result.uri.query must_=== expected.uri.query
      result.uri.fragment must_=== expected.uri.fragment
      result.headers must_=== expected.headers
      result.body.compile.toVector.unsafeRunSync must_=== expected.body.compile.toVector.unsafeRunSync
    }
  }

}
