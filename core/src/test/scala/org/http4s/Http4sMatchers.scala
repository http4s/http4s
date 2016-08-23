package org.http4s

import cats.data._
import fs2._
import org.http4s.headers._
import org.specs2.matcher._

/** This might be useful in a testkit spinoff.  Let's see what they do for us. */
trait Http4sMatchers extends Matchers with TaskMatchers {
  def haveStatus(expected: Status): Matcher[Response] =
    be_===(expected) ^^ { r: Response =>
      r.status aka "the response status"
    }

  def haveBody[A: EntityDecoder](expected: A): Matcher[Message] =
    returnValue(expected) ^^ { m: Message =>
      m.as[A] aka "the message body"
    }

  def haveMediaType(mt: MediaType): Matcher[Message] =
    beSome(mt) ^^ { m: Message =>
      m.headers.get(`Content-Type`).map(_.mediaType) aka "the media type header"
    }

  def beFallthrough[A](implicit F: Fallthrough[A]): Matcher[A] = { a: A => (
    F.isFallthrough(a),
    s"$a is the fallthrough",
    s"$a is not the fallthrough"
  )}

  def returnRight[A, B](m: ValueCheck[B]): Matcher[EitherT[Task, A, B]] =
    beRight(m) ^^ { et: EitherT[Task, A, B] =>
      et.value.unsafeRun aka "the either task"
    }

  def returnLeft[A, B](m: ValueCheck[A]): Matcher[EitherT[Task, A, B]] =
    beLeft(m) ^^ { et: EitherT[Task, A, B] =>
      et.value.unsafeRun aka "the either task"
    }
}
