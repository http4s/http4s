/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package parser

import cats.data.NonEmptyList
import java.nio.charset.{Charset, StandardCharsets}

import org.http4s._
import org.http4s.headers.Origin
import org.http4s.internal.parboiled2._
import cats.parse.{Parser, Parser1, Rfc5234}
import org.http4s.Uri.RegName
import cats.implicits._

trait OriginHeader {
  def ORIGIN(value: String): ParseResult[Origin] = {
    OriginHeader.parser.parseAll(value).leftMap { e =>
      ParseFailure("Invalid Origin Header", e.toString)
    }
//    new OriginParser(value).parse
  }

  private class OriginParser(value: String)
      extends Http4sHeaderParser[Origin](value)
      with Rfc3986Parser {
    override def charset: Charset =
      StandardCharsets.ISO_8859_1

    def entry: Rule1[Origin] =
      rule {
        nullEntry | hostListEntry
      }

    // The spec states that an Origin may be the string "null":
    // http://tools.ietf.org/html/rfc6454#section-7
    //
    // However, this MDN article states that it may be the empty string:
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Origin
    //
    // Although the MDN article is possibly wrong,
    // it seems likely we could get either case,
    // so we read both as Origin.Null and re-serialize it as "null":
    def nullEntry: Rule1[Origin] =
      rule {
        (str("") ~ EOI | str("null") ~ EOI) ~> { () =>
          Origin.Null
        }
      }

    def hostListEntry: Rule1[Origin] =
      rule {
        (host ~ zeroOrMore(" " ~ host)) ~> {
          (head: Origin.Host, tail: collection.Seq[Origin.Host]) =>
            Origin.HostList(NonEmptyList(head, tail.toList))
        }
      }

    def host: Rule1[Origin.Host] =
      rule {
        (scheme ~ "://" ~ Host ~ Port) ~> { (s, h, p) =>
          Origin.Host(s, h, p)
        }
      }
  }
}

object OriginHeader {
  private[http4s] val parser: Parser1[Origin] = {
    import Parser.{string1, rep, `end`, char, until1}
    import Rfc5234.{alpha, digit}


    val unknownScheme = alpha ~ rep(alpha.string orElse1 digit.string orElse1 string1("+").string orElse1 string1("-").string orElse1 string1(".").string)
    val http = string1("http")
    val https = string1("https")
    val scheme = List(https, http, unknownScheme).reduceLeft(_ orElse1 _).string.map(Uri.Scheme.unsafeFromString)
    val host = until1(char(':').orElse(`end`)).string.map(RegName.apply)
    val port = char(':') *> rep(digit).map {
      case Nil => None
      case li =>
        Some(li.mkString.toInt)
    }

    (scheme ~ string1("://").void ~ host ~ port).map { case (((sch, _), host), port) =>
      val one = Origin.Host(sch, host, port)
      Origin.HostList(NonEmptyList.of(one))
    }
  }
}
