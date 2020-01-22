package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer
import scala.concurrent.duration.FiniteDuration

/**
  * Defined by http://tools.ietf.org/html/rfc6797
  */
object `Strict-Transport-Security`
    extends HeaderKey.Internal[`Strict-Transport-Security`]
    with HeaderKey.Singleton {
  private[headers] class StrictTransportSecurityImpl(
      maxAge: Long,
      includeSubDomains: Boolean,
      preload: Boolean)
      extends `Strict-Transport-Security`(maxAge, includeSubDomains, preload)

  def fromLong(
      maxAge: Long,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): ParseResult[`Strict-Transport-Security`] =
    if (maxAge >= 0) {
      ParseResult.success(new StrictTransportSecurityImpl(maxAge, includeSubDomains, preload))
    } else {
      ParseResult.fail(
        "Invalid maxAge value",
        s"Strict-Transport-Security param $maxAge must be more or equal to 0 seconds")
    }

  def unsafeFromDuration(
      maxAge: FiniteDuration,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): `Strict-Transport-Security` =
    fromLong(maxAge.toSeconds, includeSubDomains, preload).fold(throw _, identity)

  def unsafeFromLong(
      maxAge: Long,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): `Strict-Transport-Security` =
    fromLong(maxAge, includeSubDomains, preload).fold(throw _, identity)

  def parse(s: String): ParseResult[`Strict-Transport-Security`] =
    HttpHeaderParser.STRICT_TRANSPORT_SECURITY(s)
}

sealed abstract case class `Strict-Transport-Security`(
    maxAge: Long,
    includeSubDomains: Boolean = true,
    preload: Boolean = false)
    extends Header.Parsed {
  override def key: `Strict-Transport-Security`.type = `Strict-Transport-Security`

  override def renderValue(writer: Writer): writer.type = {
    writer << "max-age=" << maxAge
    if (includeSubDomains) writer << "; includeSubDomains"
    if (preload) writer << "; preload"
    writer
  }

  def withIncludeSubDomains(includeSubDomains: Boolean): `Strict-Transport-Security` =
    new `Strict-Transport-Security`.StrictTransportSecurityImpl(
      this.maxAge,
      includeSubDomains,
      this.preload)

  def withPreload(preload: Boolean): `Strict-Transport-Security` =
    new `Strict-Transport-Security`.StrictTransportSecurityImpl(
      this.maxAge,
      this.includeSubDomains,
      preload)
}
