package org.http4s
package headers

import java.time.Instant

import org.http4s.util.{Renderer, Writer}
import org.http4s.parser.HttpHeaderParser

import scala.concurrent.duration.FiniteDuration

object `Retry-After` extends HeaderKey.Internal[`Retry-After`] with HeaderKey.Singleton {
  // Define the renderer locally. we may not want to render to seconds in all cases
  implicit val finiteDurationRenderer: Renderer[FiniteDuration] = new Renderer[FiniteDuration] {
    override def render(writer: Writer, d: FiniteDuration): writer.type =
      writer << d.toSeconds.toString
  }

  override def parse(s: String): ParseResult[`Retry-After`] =
    HttpHeaderParser.RETRY_AFTER(s)
}

/**
  * Constructs a `Retry-After` header.
  *
  * The value of this field can be either an HTTP-date or an integer number of seconds (in decimal) after the time of the response.
  *
  * @param retry Either the date of expiration or seconds until expiration
  */
final case class `Retry-After`(retry: Either[Instant, FiniteDuration]) extends Header.Parsed {
  import `Retry-After`._

  val key = `Retry-After`
  override val value = Renderer.renderString(retry)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

