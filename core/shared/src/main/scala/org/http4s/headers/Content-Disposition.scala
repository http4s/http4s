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

import cats.parse.Parser
import cats.parse.Rfc5234
import org.http4s.internal.CharPredicate
import org.http4s.internal.parsing.Rfc2616
import org.http4s.internal.parsing.Rfc3986
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

import java.nio.charset.StandardCharsets
import scala.collection.immutable.TreeMap

object `Content-Disposition` {
  private val safeChars = CharPredicate.Printable -- "%\"\\"

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
    val charset = Parser.ignoreCase("UTF-8").as(StandardCharsets.UTF_8)
    val extValue = (Rfc5234.dquote *> Parser.charsWhile0(
      CharPredicate.All -- '"'
    ) <* Rfc5234.dquote) | (charset ~ (Parser.string("'") *> language.? <* Parser.string(
      "'"
    )) ~ valueChars).map { case ((charset, _), values) =>
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
    } yield (CIString(tok), v)

    (Rfc7230.token ~ (Parser.string(";") *> Rfc7230.ows *> parameter).rep0).map {
      case (token: String, params: List[(CIString, String)]) =>
        `Content-Disposition`(token, params.toMap)
    }
  }

  implicit val headerInstance: Header[`Content-Disposition`, Header.Single] =
    Header.createRendered(
      ci"Content-Disposition",
      v =>
        new Renderable {
          // Adapted from https://github.com/akka/akka-http/blob/b071bd67547714bd8bed2ccd8170fbbc6c2dbd77/akka-http-core/src/main/scala/akka/http/scaladsl/model/headers/headers.scala#L468-L492
          def render(writer: Writer): writer.type = {
            val renderExtFilename =
              v.parameters.get(ci"filename").exists(!safeChars.matchesAll(_))
            val withExtParams =
              if (renderExtFilename && !v.parameters.contains(ci"filename*"))
                v.parameters + (ci"filename*" -> v.parameters(ci"filename"))
              else v.parameters
            val withExtParamsSorted =
              if (withExtParams.contains(ci"filename") && withExtParams.contains(ci"filename*"))
                TreeMap[CIString, String]() ++ withExtParams
              else withExtParams

            writer.append(v.dispositionType)
            withExtParamsSorted.foreach {
              case (k, v) if k == ci"filename" =>
                writer << "; " << k << '=' << '"'
                writer.eligibleOnly(v, keep = safeChars, placeholder = '?') << '"'
              case (k @ ci"${_}*", v) =>
                writer << "; " << k << '=' << "UTF-8''" << Uri.encode(v)
              case (k, v) => writer << "; " << k << "=\"" << v << '"'
            }
            writer
          }
        },
      parse,
    )
}

// see https://datatracker.ietf.org/doc/html/rfc2183
final case class `Content-Disposition`(dispositionType: String, parameters: Map[CIString, String])
