/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/ResponseCookie.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import cats.parse.Parser
import cats.parse.Rfc5234
import org.http4s.internal.parsing.RelaxedCookies
import org.http4s.internal.parsing.Rfc1034
import org.http4s.internal.parsing.Rfc2616
import org.http4s.util.Renderable
import org.http4s.util.Writer

import java.time.DateTimeException
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** @param extension The extension attributes of the cookie.  If there is more
  * than one, they are joined by semi-colon, which must not appear in an
  * attribute value.
  */
final case class ResponseCookie(
    name: String,
    content: String,
    expires: Option[HttpDate] = None,
    maxAge: Option[Long] = None,
    domain: Option[String] = None,
    path: Option[String] = None,
    sameSite: Option[SameSite] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    extension: Option[String] = None,
) extends Renderable { self =>
  override lazy val renderString: String = super.renderString

  override def render(writer: Writer): writer.type = {
    writer.append(name).append('=').append(content)
    expires.foreach { e =>
      writer.append("; Expires=").append(e)
    }
    maxAge.foreach(writer.append("; Max-Age=").append(_))
    domain.foreach(writer.append("; Domain=").append(_))
    path.foreach(writer.append("; Path=").append(_))
    sameSite.foreach(writer.append("; SameSite=").append(_))
    if (secure || sameSite.contains(SameSite.None)) writer.append("; Secure")
    if (httpOnly) writer.append("; HttpOnly")
    extension.foreach(writer.append("; ").append(_))
    writer
  }

  def clearCookie: ResponseCookie =
    copy(content = "", expires = Some(HttpDate.Epoch))

  private def withExpires(expires: HttpDate): ResponseCookie =
    copy(expires = Some(expires))

  private def withMaxAge(maxAge: Long): ResponseCookie =
    copy(maxAge = Some(maxAge))

  private def withDomain(domain: String): ResponseCookie =
    copy(domain = Some(domain))

  private def withPath(path: String): ResponseCookie =
    copy(path = Some(path))

  private def withSecure(secure: Boolean): ResponseCookie =
    copy(secure = secure)

  private def withHttpOnly(httpOnly: Boolean): ResponseCookie =
    copy(httpOnly = httpOnly)

  private def withSameSite(sameSite: SameSite): ResponseCookie =
    copy(sameSite = Some(sameSite))

  private def addExtension(extension: String): ResponseCookie =
    copy(extension = self.extension.fold(Option(extension))(old => Some(old + "; " + extension)))
}

object ResponseCookie {
  private[http4s] val parser: Parser[ResponseCookie] = {
    import Parser.{char, charIn, failWith, ignoreCase, pure}
    import Rfc2616.Rfc1123Date
    import Rfc5234.digit
    import RelaxedCookies.{cookieName, cookieValue}

    /* cookie-pair       = cookie-name "=" cookie-value */
    val cookiePair = (cookieName <* char('=')) ~ cookieValue

    /* sane-cookie-date  = <rfc1123-date, defined in [RFC2616], Section 3.3.1> */
    val saneCookieDate = Rfc2616.rfc1123Date.flatMap { (date: Rfc1123Date) =>
      import date._
      try {
        val dt = ZonedDateTime.of(year, month, day, hour, min, sec, 0, ZoneOffset.UTC)
        pure(org.http4s.HttpDate.unsafeFromZonedDateTime(dt))
      } catch {
        case _: DateTimeException =>
          failWith(s"Invalid sane-cookie-date: $year-$month-$day $hour:$min:$sec")
      }
    }

    /* expires-av        = "Expires=" sane-cookie-date */
    val expiresAv = ignoreCase("Expires=").with1 *> saneCookieDate.map {
      (expires: HttpDate) => (cookie: ResponseCookie) => cookie.withExpires(expires)
    }

    /* non-zero-digit    = %x31-39
     *                   ; digits 1 through 9
     */
    val nonZeroDigit = charIn(0x31.toChar to 0x39.toChar)

    /* max-age-av        = "Max-Age=" non-zero-digit *DIGIT
     *                   ; In practice, both expires-av and max-age-av
     *                   ; are limited to dates representable by the
     *                   ; user agent.
     */
    val maxAgeAv = ignoreCase("Max-Age=") *> (nonZeroDigit ~ digit.rep0).string.map {
      (maxAge: String) => (cookie: ResponseCookie) => cookie.withMaxAge(maxAge.toLong)
    }

    /* domain-value      = <subdomain>
     *                   ; defined in [RFC1034], Section 3.5, as
     *                   ; *enhanced by [RFC1123], Section 2.1
     */
    def domainValue = Rfc1034.subdomain

    /* domain-av         = "Domain=" domain-value
     *
     * But https://datatracker.ietf.org/doc/html/rfc6265#section-5.2.3 mandates
     * a leading dot, which is invalid per domain-value.
     */
    val domainAv = ignoreCase("Domain=") *> (char('.').? ~ domainValue).string.map {
      (domain: String) => (cookie: ResponseCookie) => cookie.withDomain(domain)
    }

    /* av-octet = %x20-3A / %x3C-7E
     *          ; any CHAR except CTLs or ";"
     *
     * as proposed by https://www.rfc-editor.org/errata/eid3444
     */
    val avOctet = charIn(0x20.toChar to 0x3a.toChar)
      .orElse(charIn(0x3c.toChar to 0x7e.toChar))

    /* path-value = *av-octet
     *
     * as amended by https://www.rfc-editor.org/errata/eid3444
     */
    val pathValue = avOctet.rep0.string

    /* path-av           = "Path=" path-value */
    val pathAv = ignoreCase("Path=") *> pathValue.map { case path: String =>
      (cookie: ResponseCookie) => cookie.withPath(path)
    }

    /* secure-av         = "Secure" */
    val secureAv = ignoreCase("Secure").as { (cookie: ResponseCookie) =>
      cookie.withSecure(true)
    }

    /* httponly-av       = "HttpOnly" */
    val httponlyAv = ignoreCase("HttpOnly").as { (cookie: ResponseCookie) =>
      cookie.withHttpOnly(true)
    }

    /* samesite-value    = "Strict" / "Lax" / "None"
     * from https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis-06
     *
     * Anything but "strict" or "lax" is "none"
     */
    val samesiteValue =
      ignoreCase("strict")
        .as(SameSite.Strict)
        .orElse(ignoreCase("lax").as(SameSite.Lax))
        .orElse(avOctet.rep0.as(SameSite.None))

    /* samesite-av       = "SameSite" BWS "=" BWS samesite-value
     * from https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis-06
     */
    val samesiteAv = ignoreCase("SameSite=") *> samesiteValue.map {
      (sameSite: SameSite) => (cookie: ResponseCookie) => cookie.withSameSite(sameSite)
    }

    /* extension-av = *av-octet
     *
     * as amended by https://www.rfc-editor.org/errata/eid3444
     */
    val extensionAv = avOctet.rep.string.map { (ext: String) => (cookie: ResponseCookie) =>
      cookie.addExtension(ext)
    }

    /* cookie-av         = expires-av / max-age-av / domain-av /
     *                     path-av / secure-av / httponly-av /
     *                     extension-av
     */
    val cookieAv = expiresAv
      .orElse(maxAgeAv)
      .orElse(domainAv)
      .orElse(pathAv)
      .orElse(secureAv)
      .orElse(httponlyAv)
      .orElse(samesiteAv)
      .orElse(extensionAv)

    /* set-cookie-string = cookie-pair *( ";" SP cookie-av ) */
    (cookiePair ~ (char(';') *> char(' ').rep0 *> cookieAv).rep0).map { case ((name, value), avs) =>
      avs.foldLeft(ResponseCookie(name, value))((p, f) => f(p))
    }
  }
}
