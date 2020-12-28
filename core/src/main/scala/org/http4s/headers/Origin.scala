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

package org.http4s
package headers

import cats.data.NonEmptyList
import cats.implicits.toBifunctorOps
import cats.parse.{Parser, Rfc5234}
import org.http4s.Uri.RegName
import org.http4s.util.{Renderable, Writer}

sealed abstract class Origin extends Header.Parsed {
  def key: Origin.type =
    Origin
}

object Origin extends HeaderKey.Internal[Origin] with HeaderKey.Singleton {
  // An Origin header may be the string "null", representing an "opaque origin":
  // https://stackoverflow.com/questions/42239643/when-does-firefox-set-the-origin-header-to-null-in-post-requests
  case object Null extends Origin {
    def renderValue(writer: Writer): writer.type =
      writer << "null"
  }

  // If the Origin is not "null", it is a non-empty list of Hosts:
  // http://tools.ietf.org/html/rfc6454#section-7
  final case class HostList(hosts: NonEmptyList[Host]) extends Origin {
    def renderValue(writer: Writer): writer.type = {
      writer << hosts.head
      hosts.tail.foreach { host =>
        writer << " "
        writer << host
      }
      writer
    }
  }

  // A host in an Origin header isn't a full URI.
  // It only contains a scheme, a host, and an optional port.
  // Hence we re-used parts of the Uri class here, but we don't use a whole Uri:
  // http://tools.ietf.org/html/rfc6454#section-7
  final case class Host(scheme: Uri.Scheme, host: Uri.Host, port: Option[Int] = None)
      extends Renderable {
    def toUri: Uri =
      Uri(scheme = Some(scheme), authority = Some(Uri.Authority(host = host, port = port)))

    def render(writer: Writer): writer.type =
      toUri.render(writer)
  }

  private[http4s] val parser: Parser[Origin] = {
    import Parser.{`end`, char, rep, rep1, string1, until1}
    import Rfc5234.{alpha, digit}

    val unknownScheme =
      alpha ~ rep(alpha.orElse1(digit).orElse1(char('+')).orElse1(char('-')).orElse1(char('.')))
    val http = string1("http")
    val https = string1("https")
    val scheme = List(https, http, unknownScheme)
      .reduceLeft(_ orElse1 _)
      .string
      .map(Uri.Scheme.unsafeFromString)
    val stringHost = until1(char(':').orElse(`end`)).map(RegName.apply)
    val bracketedIpv6 = char('[') *> Uri.Ipv6Address.parser <* char(']')
    val host = List(bracketedIpv6, Uri.Ipv4Address.parser, stringHost).reduceLeft(_ orElse1 _)
    val port = char(':') *> rep1(digit, 1).string.map(_.toInt)
    val nullHost = (string1("null") *> `end`).orElse(`end`).as(Origin.Null)

    val singleHost = ((scheme <* string1("://")) ~ host ~ port.?).map { case ((sch, host), port) =>
      Origin.Host(sch, host, port)
    }

    nullHost.orElse((singleHost ~ rep(char(' ') *> singleHost)).map(hosts =>
      Origin.HostList(NonEmptyList.of(hosts._1, hosts._2: _*))))
  }

  override def parse(s: String): ParseResult[Origin] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid Origin Header", e.toString)
    }
}
