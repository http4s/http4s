package org.http4s
package headers

import cats.data.NonEmptyList
import cats.implicits.catsSyntaxOptionId
import cats.parse.{Parser, Parser0, Rfc5234}
import org.http4s.Header
import org.http4s.headers.`Alt-Svc`.Value.Clear
import org.http4s.internal.parsing.Rfc3986
import org.http4s.parser.AdditionalRules
import org.http4s.util.{Renderer, Writer}
import org.typelevel.ci.{CIString, CIStringSyntax}

import scala.util.Try

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

case class `Alt-Svc`(alternatives: `Alt-Svc`.Value)

object `Alt-Svc` extends HeaderCompanion[`Alt-Svc`]("Alt-Svc") {

  override val name: CIString = ci"Alt-Svc"

  def fromString(header: String): ParseResult[`Alt-Svc`] =
    ParseResult.fromParser(parser, s"Cannot parse `Alt-Svc` header from $header")(header)

  final case class AltAuthority private(host: Option[CIString], port: Int )
  object AltAuthority {
    private[`Alt-Svc`] val parser: Parser0[AltAuthority] = {
      val port = Parser.string(":") *> Rfc3986.digit.rep.string.mapFilter { s =>
        Try(s.toInt).toOption
      }

      (Uri.Parser.host.? ~ port).map { case (host, port) =>
        AltAuthority(host.map(v => CIString(v.value)).flatMap(c => if (c.isEmpty) None else c.some), port)
      }
    }
    def fromString(authority: String): ParseResult[AltAuthority] =
      ParseResult.fromParser(parser, s"Cannot parse authority from $authority")(authority)
  }
  final case class AltService(protocolId: ProtocolId, authority: AltAuthority, maxAge: Option[Long] = None, persist: Boolean = false)
  object AltService {
    private[`Alt-Svc`] val parser: Parser[AltService] =
      ( (ProtocolId.parser <* Parser.string("=")) ~
        AltAuthority.parser.surroundedBy(Parser.char('"')) ~
        (
          Parser.ignoreCase(";") ~ Parser.char(' ').rep0 ~ Parser.ignoreCase("ma=") *>
            AdditionalRules.NonNegativeLong
          ).? ~
         (Parser.char(';') ~ Parser.char(' ').rep0 *> Parser.ignoreCase("persist=1")).?.map(_.isDefined)
        ).map{ case (((protocol, authority), ma), persist) =>  AltService(protocol, authority, ma, persist) }

    def fromString(altService: String): ParseResult[AltService] =
      ParseResult.fromParser(parser, s"Cannot parse altService from $altService")(altService)
  }

  sealed trait ProtocolId
  case object `http/1.1` extends ProtocolId
  case object `h2` extends ProtocolId
  case object `h3-25` extends ProtocolId
  object ProtocolId {
    private[`Alt-Svc`] val parser: Parser[ProtocolId] = Parser.oneOf(
      Parser.string("http/1.1").map(_ => `http/1.1`) ::
      Parser.string("h2").map(_ => `h2`) ::
      Parser.string("h3-25").map(_ => `h3-25`) :: Nil
    )
    def fromString(protocol: String): ParseResult[ProtocolId] =
      ParseResult.fromParser(parser, s"Cannot parse protocol $protocol")(protocol)
  }

  sealed trait Value
  object Value {
    /** All alternative services of the origin are invalidated. */
    case object Clear extends Value
    case class AltValue(alternatives: NonEmptyList[AltService]) extends Value
    object AltValue {
      def apply(altService: AltService, altServices: AltService*): AltValue =
        AltValue(NonEmptyList.of(altService, altServices *))
    }
  }

  implicit val protocolRenderer: Renderer[ProtocolId] = new Renderer[ProtocolId] {
    override def render(writer: Writer, t: ProtocolId): writer.type =
       writer << {
         t match {
           case `http/1.1` => ci"http/1.1"
           case `h2` => ci"h2"
           case `h3-25` => ci"h3-25"
         }
       }
  }

  implicit val altAuthorityRendered: Renderer[AltAuthority] = new Renderer[AltAuthority] {
    override def render(writer: Writer, t: AltAuthority): writer.type =
      writer << ci"\"" << t.host.getOrElse(ci"") << ci":" << t.port << ci"\""
  }

  implicit val altServiceRendered: Renderer[AltService] = new Renderer[AltService] {
    override def render(writer: Writer, t: AltService): writer.type =
      writer << t.protocolId << ci"=" << t.authority << t.maxAge.map(a => ci"; ma=$a").getOrElse(ci"") << {
        if (t.persist) ci"; persist=1" else ci""
      }
  }

  implicit val valueRenderer: Renderer[Value] = new Renderer[Value] {
    override def render(writer: Writer, t: Value): writer.type =
      t match {
        case Value.Clear => writer << "clear"
        case Value.AltValue(alternatives) =>
          alternatives.foldLeft((writer, 1)){ case ((w, ind), svc) =>
            (w << svc << {if (ind < alternatives.size) ", " else ""}, ind + 1)
          }
          writer
      }
  }

  implicit val headerInstance: Header[`Alt-Svc`, Header.Single] = {
    Header.createRendered(
      ci"Alt-Svc",
      _.alternatives,
      parse
    )
  }

  override private[http4s] val parser: Parser0[`Alt-Svc`] =
    Parser.oneOf(
      Parser.ignoreCase("Clear").map(_ => `Alt-Svc`(Clear)) ::
       Parser.repSep(AltService.parser, Parser.char(',') ~ Parser.char(' ').rep0)
        .map(svcs => `Alt-Svc`(Value.AltValue(svcs))) ::
      Nil
    )
}