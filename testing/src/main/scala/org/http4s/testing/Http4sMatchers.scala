package org.http4s
package testing

import cats.syntax.flatMap._
import cats.data.EitherT
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString
import org.specs2.matcher._

/** This might be useful in a testkit spinoff.  Let's see what they do for us. */
// TODO these akas might be all wrong.
trait Http4sMatchers[F[_]] extends Matchers with RunTimedMatchers[F] {
  def haveStatus(expected: Status): Matcher[Response[F]] =
    be_===(expected) ^^ { r: Response[F] =>
      r.status.aka("the response status")
    }

  def returnStatus(s: Status): Matcher[F[Response[F]]] =
    haveStatus(s) ^^ { r: F[Response[F]] =>
      runAwait(r).aka("the returned")
    }

  def haveBody[A: EntityDecoder[F, ?]](a: ValueCheck[A]): Matcher[Message[F]] =
    returnValue(a) ^^ { m: Message[F] =>
      m.as[A].aka("the message body")
    }

  def returnBody[A: EntityDecoder[F, ?]](a: ValueCheck[A]): Matcher[F[Message[F]]] =
    returnValue(a) ^^ { m: F[Message[F]] =>
      m.flatMap(_.as[A]).aka("the returned message body")
    }

  def haveHeaders(a: Headers): Matcher[Message[F]] =
    be_===(a) ^^ { m: Message[F] =>
      m.headers.aka("the headers")
    }

  def containsHeader(h: Header): Matcher[Message[F]] =
    beSome(h.value) ^^ { m: Message[F] =>
      m.headers.get(h.name).map(_.value).aka("the particular header")
    }

  def doesntContainHeader(h: CaseInsensitiveString): Matcher[Message[F]] =
    beNone ^^ { m: Message[F] =>
      m.headers.get(h).aka("the particular header")
    }

  def haveMediaType(mt: MediaType): Matcher[Message[F]] =
    beSome(mt) ^^ { m: Message[F] =>
      m.headers.get(`Content-Type`).map(_.mediaType).aka("the media type header")
    }

  def haveContentCoding(c: ContentCoding): Matcher[Message[F]] =
    beSome(c) ^^ { m: Message[F] =>
      m.headers.get(`Content-Encoding`).map(_.contentCoding).aka("the content encoding header")
    }

  def returnRight[A, B](m: ValueCheck[B]): Matcher[EitherT[F, A, B]] =
    beRight(m) ^^ { et: EitherT[F, A, B] =>
      runAwait(et.value).aka("the either task")
    }

  def returnLeft[A, B](m: ValueCheck[A]): Matcher[EitherT[F, A, B]] =
    beLeft(m) ^^ { et: EitherT[F, A, B] =>
      runAwait(et.value).aka("the either task")
    }
}
