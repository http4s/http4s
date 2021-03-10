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

import cats.parse.{Parser, Rfc5234}
import org.http4s.internal.CharPredicate
import org.http4s.internal.parsing.{Rfc2616, Rfc3986, Rfc7230}
import org.http4s.util.{Renderable, Writer}
import java.nio.charset.StandardCharsets
import org.typelevel.ci.CIString
import scala.collection.immutable.TreeMap

object `Content-Disposition` {
  val safeChars = CharPredicate.Printable -- "%\"\\"

  def parse(s: String): ParseResult[`Content-Disposition`] =
    ParseResult.fromParser(parser, "Invalid Content-Disposition header")(s)

  private[http4s] val parser = {
    sealed trait ValueChar
    case class AsciiChar(c: Char) extends ValueChar
    case class EncodedChar(a: Char, b: Char) extends ValueChar

    val attrChar = Rfc3986.alpha
      .orElse(Rfc3986.digit)
      .orElse(Parser.charIn('!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~'))
      .map { (a: Char) =>
        AsciiChar(a)
      }
    val pctEncoded = (Parser.string("%") *> Rfc3986.hexdig ~ Rfc3986.hexdig).map {
      case (a: Char, b: Char) => EncodedChar(a, b)
    }
    val valueChars = attrChar.orElse(pctEncoded).rep
    val language = Parser.string(Rfc5234.alpha.rep) ~ (Parser.string("-") *> Rfc2616.token).rep0
    val charset = Parser.string("UTF-8").as(StandardCharsets.UTF_8)
    val extValue = (Rfc5234.dquote *> Parser.charsWhile0(
      CharPredicate.All -- '"') <* Rfc5234.dquote) | (charset ~ (Parser.string(
      "'") *> language.? <* Parser.string("'")) ~ valueChars).map { case ((charset, _), values) =>
      values
        .map {
          case EncodedChar(a: Char, b: Char) =>
            val charByte = (Character.digit(a, 16) << 4) + Character.digit(b, 16)
            new String(Array(charByte.toByte), charset)
          case AsciiChar(a) => a.toString
        }
        .toList
        .mkString
    }

    val value = Rfc7230.token | Rfc7230.quotedString

    val parameter = for {
      tok <- Rfc7230.token <* Parser.string("=") <* Rfc7230.ows
      v <- if (tok.endsWith("*")) extValue else value
    } yield (tok, v)

    (Rfc7230.token ~ (Parser.string(";") *> Rfc7230.ows *> parameter).rep0).map {
      case (token: String, params: List[(String, String)]) =>
        `Content-Disposition`(token, params.toMap)
    }
  }

  implicit val headerInstance: Header[`Content-Disposition`, Header.Single] =
    Header.createRendered(
      CIString("Content-Disposition"),
      v =>
        new Renderable {

          def render(writer: Writer): writer.type = {
            val renderExtFilename =
              v.parameters.get("filename").exists(!safeChars.matchesAll(_))
            val withExtParams =
              if (renderExtFilename && !v.parameters.contains("filename*"))
                v.parameters + ("filename*" -> v.parameters("filename"))
              else v.parameters
            val withExtParamsSorted =
              if (withExtParams.contains("filename") && withExtParams.contains("filename*"))
                TreeMap[String, String]() ++ withExtParams
              else withExtParams

            writer.append(v.dispositionType)
            withExtParamsSorted.foreach {
              case (k, v) if k == "filename" =>
                writer << "; " << k << '=' << '"'
                writer.eligibleOnly(v, keep = safeChars, placeholder = '?') << '"'
              case (k, v) if k.endsWith("*") =>
                writer << "; " << k << '=' << "UTF-8''" << Uri.encode(v)
              case (k, v) => writer << "; " << k << "=\"" << v << '"'
            }
            writer
          }
        },
      parse
    )
}

// see http://tools.ietf.org/html/rfc2183
final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String])
