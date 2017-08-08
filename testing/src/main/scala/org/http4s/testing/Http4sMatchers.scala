package org.http4s
package testing

import cats.data.EitherT
import cats.effect.IO
import org.http4s.headers._
import org.specs2.matcher._

/** This might be useful in a testkit spinoff.  Let's see what they do for us. */
// TODO these akas might be all wrong.
trait Http4sMatchers extends Matchers with IOMatchers {
  def haveStatus(expected: Status): Matcher[Response[IO]] =
    be_===(expected) ^^ { r: Response[IO] =>
      r.status aka "the response status"
    }

  def returnStatus(s: Status): Matcher[IO[Response[IO]]] =
    haveStatus(s) ^^ { r: IO[Response[IO]] =>
      r.unsafeRunSync aka "the returned"
    }

  def haveBody[A: EntityDecoder[IO, ?]](a: ValueCheck[A]): Matcher[Message[IO]] =
    returnValue(a) ^^ { m: Message[IO] =>
      m.as[A] aka "the message body"
    }

  def returnBody[A: EntityDecoder[IO, ?]](a: ValueCheck[A]): Matcher[IO[Message[IO]]] =
    returnValue(a) ^^ { m: IO[Message[IO]] =>
      m.flatMap(_.as[A]) aka "the returned message body"
    }

  def haveHeaders(a: Headers): Matcher[Message[IO]] =
    be(a) ^^ { m: Message[IO] =>
      m.headers aka "the headers"
    }

  def haveMediaType(mt: MediaType): Matcher[Message[IO]] =
    beSome(mt) ^^ { m: Message[IO] =>
      m.headers.get(`Content-Type`).map(_.mediaType) aka "the media type header"
    }

  def returnRight[A, B](m: ValueCheck[B]): Matcher[EitherT[IO, A, B]] =
    beRight(m) ^^ { et: EitherT[IO, A, B] =>
      et.value.unsafeRunSync aka "the either task"
    }

  def returnLeft[A, B](m: ValueCheck[A]): Matcher[EitherT[IO, A, B]] =
    beLeft(m) ^^ { et: EitherT[IO, A, B] =>
      et.value.unsafeRunSync aka "the either task"
    }

  def haveContentCoding[F[_]](c: ContentCoding): Matcher[Message[F]] =
    beSome(c) ^^ { m: Message[F] =>
      m.headers.get(`Content-Encoding`).map(_.contentCoding) aka "the content encoding header"
    }
}
