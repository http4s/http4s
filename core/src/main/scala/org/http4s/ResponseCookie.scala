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

import cats.parse.{Parser, Parser1, Rfc5234}
import java.time.{DateTimeException, ZoneOffset, ZonedDateTime}
import org.http4s.internal.parsing.{Rfc1034, Rfc2616, Rfc6265}
import org.http4s.util.{Renderable, Writer}

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
    extension: Option[String] = None
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
    if (secure || sameSite.exists(_ == SameSite.None)) writer.append("; Secure")
    if (httpOnly) writer.append("; HttpOnly")
    extension.foreach(writer.append("; ").append(_))
    writer
  }

  def clearCookie: headers.`Set-Cookie` =
    headers.`Set-Cookie`(copy(content = "", expires = Some(HttpDate.Epoch), maxAge = Some(0L)))

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
    copy(sameSite = sameSite)

  private def addExtension(extension: String): ResponseCookie =
    copy(extension = self.extension.fold(Option(extension))(old => Some(old + "; " + extension)))
}

object ResponseCookie {
  private[http4s] val parser: Parser1[ResponseCookie] = {
    import Parser.{char, charIn, failWith, ignoreCase1, pure, rep, string1}
    import Rfc2616.Rfc1123Date
    import Rfc5234.digit
    import Rfc6265.{cookieName, cookieValue}

    /* cookie-pair       = cookie-name "=" cookie-value */
    val cookiePair = (cookieName <* char('=')) ~ cookieValue

    /* sane-cookie-date  = <rfc1123-date, defined in [RFC2616], Section 3.3.1> */
    val saneCookieDate = Rfc2616.rfc1123Date.flatMap { date: Rfc1123Date =>
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
    val expiresAv = ignoreCase1("Expires=").with1 *> saneCookieDate.map {
      expires: HttpDate => (cookie: ResponseCookie) => cookie.withExpires(expires)
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
    val maxAgeAv = ignoreCase1("Max-Age=") *> (nonZeroDigit ~ rep(digit)).string.map {
      maxAge: String => (cookie: ResponseCookie) => cookie.withMaxAge(maxAge.toLong)
    }

    /* domain-value      = <subdomain>
     *                   ; defined in [RFC1034], Section 3.5, as
     *                   ; *enhanced by [RFC1123], Section 2.1
     */
    def domainValue = Rfc1034.subdomain

    /* domain-av         = "Domain=" domain-value
     *
     * But https://tools.ietf.org/html/rfc6265#section-5.2.3 mandates
     * a leading dot, which is invalid per domain-value.
     */
    val domainAv = ignoreCase1("Domain=") *> (char('.').? ~ domainValue).string.map {
      domain: String => (cookie: ResponseCookie) => cookie.withDomain(domain)
    }

    /* av-octet = %x20-3A / %x3C-7E
     *          ; any CHAR except CTLs or ";"
     *
     * as proposed by https://www.rfc-editor.org/errata/eid3444
     */
    val avOctet = charIn(0x20.toChar to 0x3a.toChar)
      .orElse1(charIn(0x3c.toChar to 0x7e.toChar))

    /* path-value = *av-octet
     *
     * as amended by https://www.rfc-editor.org/errata/eid3444
     */
    val pathValue = avOctet.rep.string

    /* path-av           = "Path=" path-value */
    val pathAv = ignoreCase1("Path=") *> pathValue.map { case path: String =>
      (cookie: ResponseCookie) => cookie.withPath(path)
    }

    /* secure-av         = "Secure" */
    val secureAv = ignoreCase1("Secure").as { (cookie: ResponseCookie) =>
      cookie.withSecure(true)
    }

    /* httponly-av       = "HttpOnly" */
    val httponlyAv = ignoreCase1("HttpOnly").as { (cookie: ResponseCookie) =>
      cookie.withHttpOnly(true)
    }

    /* samesite-value    = "Strict" / "Lax" / "None"
     * from https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis-06
     *
     * Anything but "strict" or "lax" is "none"
     */
    val samesiteValue =
      (ignoreCase1("strict")
        .as(SameSite.Strict))
        .orElse1(ignoreCase1("lax").as(SameSite.Lax))
        .orElse(avOctet.rep.as(SameSite.None))

    /* samesite-av       = "SameSite" BWS "=" BWS samesite-value
     * from https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis-06
     */
    val samesiteAv = ignoreCase1("SameSite=") *> samesiteValue.map {
      (sameSite: SameSite) => (cookie: ResponseCookie) => cookie.withSameSite(sameSite)
    }

    /* extension-av = *av-octet
     *
     * as amended by https://www.rfc-editor.org/errata/eid3444
     */
    val extensionAv = avOctet.rep1.string.map { (ext: String) => (cookie: ResponseCookie) =>
      cookie.addExtension(ext)
    }

    /* cookie-av         = expires-av / max-age-av / domain-av /
     *                     path-av / secure-av / httponly-av /
     *                     extension-av
     */
    val cookieAv = expiresAv
      .orElse1(maxAgeAv)
      .orElse1(domainAv)
      .orElse1(pathAv)
      .orElse1(secureAv)
      .orElse1(httponlyAv)
      .orElse1(samesiteAv)
      .orElse(extensionAv)

    /* set-cookie-string = cookie-pair *( ";" SP cookie-av ) */
    (cookiePair ~ (string1("; ") *> cookieAv).rep).map { case ((name, value), avs) =>
      avs.foldLeft(ResponseCookie(name, value))((p, f) => f(p))
    }
  }
}
