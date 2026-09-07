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
import cats.parse.{Parser, Parser0}
import org.http4s.Header
import org.http4s.headers.`Alt-Svc`.Value.Clear
import org.http4s.internal.parsing.{CommonRules, Rfc3986}
import org.http4s.util.{Renderable, Renderer, Writer}
import org.typelevel.ci.{CIString, CIStringSyntax}

import scala.util.Try

final case class `Alt-Svc`(alternatives: `Alt-Svc`.Value)

object `Alt-Svc` extends HeaderCompanion[`Alt-Svc`]("Alt-Svc") {

  override val name: CIString = ci"Alt-Svc"

  def fromString(header: String): ParseResult[`Alt-Svc`] =
    ParseResult.fromParser(parser, s"Cannot parse `Alt-Svc` header from $header")(header)

  final case class AltAuthority private (host: Option[Uri.Host], port: Int)
  object AltAuthority {
    private def isEmptyRegName(host: Uri.Host): Boolean = host match {
      case Uri.RegName(n) => n.isEmpty
      case _ => false
    }

    private[`Alt-Svc`] val parser: Parser0[AltAuthority] = {
      val port = Parser.char(':') *> Rfc3986.digit.rep.string.mapFilter { s =>
        Try(s.toInt).toOption
      }

      (Uri.Parser.host.? ~ port).map { case (host, port) =>
        AltAuthority(host.filterNot(isEmptyRegName), port)
      }
    }
    def fromString(authority: String): ParseResult[AltAuthority] =
      ParseResult.fromParser(parser, s"Cannot parse authority from $authority")(authority)
  }
  final case class AltService(
      protocolId: ProtocolId,
      authority: AltAuthority,
      maxAge: Option[Long] = None,
      persist: Boolean = false,
  )
  object AltService {
    // parameter = token "=" ( token / quoted-string ), unordered and extensible.
    // Unknown parameters are accepted and ignored, per RFC 7838.
    private val parameter: Parser[(String, String)] =
      (CommonRules.token ~ (Parser.char('=') *> CommonRules.token.orElse(CommonRules.quotedString)))
        .map { case (k, v) => (k.toLowerCase, v) }

    private val parameters: Parser0[List[(String, String)]] =
      (CommonRules.ows.with1 *> Parser.char(';') *> CommonRules.ows *> parameter).rep0

    private[`Alt-Svc`] val parser: Parser[AltService] =
      (
        (ProtocolId.parser <* Parser.char('=')) ~
          AltAuthority.parser.surroundedBy(Parser.char('"')) ~
          parameters
      ).map { case ((protocol, authority), params) =>
        val ma = params.collectFirst { case ("ma", v) => v }.flatMap(v => Try(v.toLong).toOption)
        val persist = params.exists {
          case ("persist", v) => v == "1"
          case _ => false
        }
        AltService(protocol, authority, ma, persist)
      }

    def fromString(altService: String): ParseResult[AltService] =
      ParseResult.fromParser(parser, s"Cannot parse altService from $altService")(altService)
  }

  /** An ALPN protocol identifier. The registry of protocol ids is open-ended (see
    * https://www.iana.org/assignments/tls-extensiontype-values/tls-extensiontype-values.xhtml#alpn-protocol-ids),
    * so this is not a closed set: [[fromString]] accepts any token, and named constants are
    * provided only for common values.
    */
  final case class ProtocolId private (value: CIString) extends Renderable {
    override def render(writer: Writer): writer.type = writer << value
  }
  object ProtocolId {
    val `http/1.1`: ProtocolId = ProtocolId(ci"http/1.1")
    val h2: ProtocolId = ProtocolId(ci"h2")
    val h2c: ProtocolId = ProtocolId(ci"h2c")
    val h3: ProtocolId = ProtocolId(ci"h3")
    val `h3-25`: ProtocolId = ProtocolId(ci"h3-25")
    val `h3-29`: ProtocolId = ProtocolId(ci"h3-29")

    def fromString(protocol: String): ParseResult[ProtocolId] =
      ParseResult.fromParser(parser, s"Cannot parse protocol $protocol")(protocol)

    // protocol-id is delimited by '=' and appears before any parameters, so any run of
    // non-delimiter, non-whitespace characters is accepted (covers both plain tokens like
    // `h2`/`h3-29` and legacy values like `http/1.1`, which is not itself a valid RFC 7230 token).
    private[`Alt-Svc`] val parser: Parser[ProtocolId] =
      Parser
        .charsWhile(c => c != '=' && c != ',' && c != ';' && c != ' ' && c != '\t' && c != '"')
        .map(s => ProtocolId(CIString(s)))
  }

  sealed trait Value
  object Value {

    /** All alternative services of the origin are invalidated. */
    final case object Clear extends Value
    final case class AltValue(alternatives: NonEmptyList[AltService]) extends Value
    object AltValue {
      def apply(altService: AltService, altServices: AltService*): AltValue =
        AltValue(NonEmptyList.of(altService, altServices *))
    }
  }

  implicit val altAuthorityRendered: Renderer[AltAuthority] = new Renderer[AltAuthority] {
    override def render(writer: Writer, t: AltAuthority): writer.type = {
      writer << "\""
      t.host.foreach(writer << _)
      writer << ":" << t.port << "\""
    }
  }

  implicit val altServiceRendered: Renderer[AltService] = new Renderer[AltService] {
    override def render(writer: Writer, t: AltService): writer.type =
      writer << t.protocolId << ci"=" << t.authority << t.maxAge
        .map(a => ci"; ma=$a")
        .getOrElse(ci"") << {
        if (t.persist) ci"; persist=1" else ci""
      }
  }

  implicit val valueRenderer: Renderer[Value] = new Renderer[Value] {
    override def render(writer: Writer, t: Value): writer.type =
      t match {
        case Value.Clear => writer << "clear"
        case Value.AltValue(alternatives) =>
          alternatives.foldLeft((writer, 1)) { case ((w, ind), svc) =>
            (w << svc << { if (ind < alternatives.size) ", " else "" }, ind + 1)
          }
          writer
      }
  }

  implicit val headerInstance: Header[`Alt-Svc`, Header.Recurring] =
    Header.createRendered(
      ci"Alt-Svc",
      _.alternatives,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Alt-Svc`] = (a, b) =>
    (a.alternatives, b.alternatives) match {
      case (Value.AltValue(xs), Value.AltValue(ys)) => `Alt-Svc`(Value.AltValue(xs.concatNel(ys)))
      case (Value.Clear, _) => a
      case (_, Value.Clear) => b
    }

  override private[http4s] val parser: Parser0[`Alt-Svc`] =
    Parser.oneOf(
      Parser.ignoreCase("Clear").map(_ => `Alt-Svc`(Clear)) ::
        CommonRules
          .headerRep1(AltService.parser)
          .map(svcs => `Alt-Svc`(Value.AltValue(svcs))) ::
        Nil
    )
}
