package org.http4s
package headers

import org.http4s.util.{Renderer, Writer}
import org.http4s.util.Renderable._
import org.http4s.parser.HttpHeaderParser

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object Age extends HeaderKey.Internal[Age] with HeaderKey.Singleton {
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
final case class Age(age: FiniteDuration) extends Header.Parsed {
  require(age >= 0.seconds)
  import Age._

  val key = Age
  override val value = Renderer.renderString(age)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

