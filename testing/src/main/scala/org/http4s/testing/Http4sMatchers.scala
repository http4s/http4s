package org.http4s
package testing

import cats.data.EitherT
import cats.effect.IO
import org.http4s.headers._
import org.specs2.matcher._
import org.http4s.Message.messSyntax._

/** This might be useful in a testkit spinoff.  Let's see what they do for us. */
// TODO these akas might be all wrong.
trait Http4sMatchers extends Matchers with IOMatchers {
  def haveStatus(expected: Status): Matcher[Response[IO]] =
    be_===(expected) ^^ { r: Response[IO] =>
      r.status.aka("the response status")
    }

  def returnStatus(s: Status): Matcher[IO[Response[IO]]] =
    haveStatus(s) ^^ { r: IO[Response[IO]] =>
      r.unsafeRunSync.aka("the returned")
    }

  def haveBody[M[_[_]], A: EntityDecoder[M, IO, ?]](a: ValueCheck[A])(implicit M: Message[M, IO]): Matcher[M[IO]] =
    returnValue(a) ^^ { m: M[IO] =>
      m.as[A].aka("the message body")
    }

  def returnBody[M[_[_]], A: EntityDecoder[M, IO, ?]](a: ValueCheck[A])(implicit M: Message[M, IO]): Matcher[IO[M[IO]]] =
    returnValue(a) ^^ { m: IO[M[IO]] =>
      m.flatMap(_.as[A]).aka("the returned message body")
    }

  def haveHeaders[M[_[_]]](a: Headers)(implicit M: Message[M, IO]): Matcher[M[IO]] =
    be(a) ^^ { m: M[IO] =>
      m.headers.aka("the headers")
    }

  def haveMediaType[M[_[_]]](mt: MediaType)(implicit M: Message[M, IO]): Matcher[M[IO]] =
    beSome(mt) ^^ { m: M[IO] =>
      m.headers.get(`Content-Type`).map(_.mediaType).aka("the media type header")
    }

  def haveContentCoding[M[_[_]]](c: ContentCoding)(implicit M: Message[M, IO]): Matcher[M[IO]] =
    beSome(c) ^^ { m: M[IO] =>
      m.headers.get(`Content-Encoding`).map(_.contentCoding).aka("the content encoding header")
    }

  def returnRight[A, B](m: ValueCheck[B]): Matcher[EitherT[IO, A, B]] =
    beRight(m) ^^ { et: EitherT[IO, A, B] =>
      et.value.unsafeRunSync.aka("the either task")
    }

  def returnLeft[A, B](m: ValueCheck[A]): Matcher[EitherT[IO, A, B]] =
    beLeft(m) ^^ { et: EitherT[IO, A, B] =>
      et.value.unsafeRunSync.aka("the either task")
    }
}
