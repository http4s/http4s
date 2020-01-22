package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try

object Age extends HeaderKey.Internal[Age] with HeaderKey.Singleton {
  private class AgeImpl(age: Long) extends Age(age)

  def fromLong(age: Long): ParseResult[Age] =
    if (age >= 0) {
      ParseResult.success(new AgeImpl(age))
    } else {
      ParseResult.fail("Invalid age value", s"Age param $age must be more or equal to 0 seconds")
    }

  def unsafeFromDuration(age: FiniteDuration): Age =
    fromLong(age.toSeconds).fold(throw _, identity)

  def unsafeFromLong(age: Long): Age =
    fromLong(age).fold(throw _, identity)

  override def parse(s: String): ParseResult[Age] =
    HttpHeaderParser.AGE(s)
}

/**
  * Constructs an Age header.
  *
  * The value of this field is a positive number of seconds (in decimal) with an estimate of the amount of time since the response
  *
  * @param age age of the response
  */
sealed abstract case class Age(age: Long) extends Header.Parsed {
  val key = Age

  override val value = Renderer.renderString(age)

  override def renderValue(writer: Writer): writer.type = writer.append(value)

  def duration: Option[FiniteDuration] = Try(age.seconds).toOption

  def unsafeDuration: FiniteDuration = age.seconds
}
