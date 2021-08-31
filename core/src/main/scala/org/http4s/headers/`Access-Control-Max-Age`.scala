package org.http4s.headers

import org.http4s.parser.AdditionalRules
import org.http4s.{Header, ParseResult}
import org.typelevel.ci.CIStringSyntax

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Try

/** Constructs an `Access-Control-Max-Age` header.
  *
  * The value of this field indicates how long the results of a preflight request (that is the information contained in the Access-Control-Allow-Methods and [[`Access-Control-Allow-Headers`]] headers) can be cached. A value of -1 will disable caching.
  *
  * @param age age of the response (in seconds)
  */
trait `Access-Control-Max-Age` {
  def duration: Option[FiniteDuration]
  def unsafeDuration: FiniteDuration
}

object `Access-Control-Max-Age` {

  final case object NoCaching extends `Access-Control-Max-Age` {
    override def duration: Option[FiniteDuration] = Option.empty

    override def unsafeDuration: FiniteDuration = throw new UnsupportedOperationException(
      "It's no possible to get cache duration if caching is disable")
  }

  final case class Cache private (age: Long) extends `Access-Control-Max-Age` {
    def duration: Option[FiniteDuration] = Try(age.seconds).toOption
    def unsafeDuration: FiniteDuration = age.seconds
  }

  def fromLong(age: Long): ParseResult[`Access-Control-Max-Age`] =
    if (age >= 0)
      ParseResult.success(Cache.apply(age))
    else if (age == -1) {
      ParseResult.success(NoCaching)
    } else {
      ParseResult.fail(
        "Invalid age value",
        s"Access-Control-Max-Age param $age must be greater or equal to 0 seconds or -1")
    }

  def unsafeFromDuration(age: FiniteDuration): `Access-Control-Max-Age` =
    fromLong(age.toSeconds).fold(throw _, identity)

  def unsafeFromLong(age: Long): `Access-Control-Max-Age` =
    fromLong(age).fold(throw _, identity)

  def parse(s: String): ParseResult[`Access-Control-Max-Age`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Max-Age header")(s)

  private[http4s] val parser = AdditionalRules.NonNegativeLong.map(unsafeFromLong)

  implicit val headerInstance: Header[`Access-Control-Max-Age`, Header.Single] =
    Header.createRendered(
      ci"Access-Control-Max-Age",
      {
        case Cache(age) => age
        case NoCaching => -1
      },
      parse
    )
}
