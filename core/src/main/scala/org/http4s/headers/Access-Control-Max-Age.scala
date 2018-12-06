package org.http4s
package headers
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try

object `Access-Control-Max-Age` extends HeaderKey.Internal[`Access-Control-Max-Age`] with HeaderKey.Singleton {
  private class AccessControlMaxAgeImpl(age: Long) extends `Access-Control-Max-Age`(age)

  def fromLong(age: Long): ParseResult[`Access-Control-Max-Age`] =
    if (age >= 0) {
      ParseResult.success(new AccessControlMaxAgeImpl(age))
    } else {
      ParseResult.fail("Invalid age value", s"Age param $age must be more or equal to 0 seconds")
    }

  def unsafeFromDuration(age: FiniteDuration): `Access-Control-Max-Age` =
    fromLong(age.toSeconds).fold(throw _, identity)

  def unsafeFromLong(age: Long): `Access-Control-Max-Age` =
    fromLong(age).fold(throw _, identity)

  override def parse(s: String): ParseResult[`Access-Control-Max-Age`] =
    HttpHeaderParser.ACCESS_CONTROL_MAX_AGE(s)

}
/**
  * Constructs a AccessControlMaxAge header.
  *
  * The value of this field is the maximum number of seconds the results can be cached.
  *
  * @param age age of the response
  */
sealed abstract case class `Access-Control-Max-Age`(age: Long) extends Header.Parsed {

  val key = `Access-Control-Max-Age`

  override val value = Renderer.renderString(age)

  override def renderValue(writer: Writer): writer.type = writer.append(value)

  def duration: Option[FiniteDuration] = Try(age.seconds).toOption

  def unsafeDuration: FiniteDuration = age.seconds

}