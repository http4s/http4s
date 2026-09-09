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

import cats.implicits._
import cats.kernel.Semigroup
import cats.parse._
import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration

object `Keep-Alive` {

  private sealed trait Parameter
  private final case class Timeout(timeoutSeconds: Long) extends Parameter
  private final case class Max(max: Long) extends Parameter
  private final case class Extension(ext: (String, Option[String])) extends Parameter

  /*
Spec Version: https://datatracker.ietf.org/doc/html/draft-thomson-hybi-http-timeout-03 (Note:  Max is deprecated and gone)
Keep-Alive           = "Keep-Alive" ":" 1#keep-alive-info
keep-alive-info      =   "timeout" "=" delta-seconds
                       / "max" "=" 1*DIGIT
                       / keep-alive-extension
keep-alive-extension = token [ "=" ( token / quoted-string ) ]
   */

  def apply(
      timeoutSeconds: Option[Long],
      max: Option[Long],
      extension: List[(String, Option[String])],
  ): ParseResult[`Keep-Alive`] =
    if (timeoutSeconds.isDefined || max.isDefined || extension.nonEmpty) {
      val reservedToken = "token"
      if (extension.exists(p => p._1 == reservedToken)) {
        ParseResult.fail(
          "Invalid Keep-Alive header",
          s"Reserved token '$reservedToken' was found in the extensions.",
        )
      } else {
        val validatedTimeoutSeconds = timeoutSeconds.traverse(t => nonNegativeLong(t, "timeout"))
        val validatedMax = max.traverse(m => nonNegativeLong(m, "max"))
        (validatedTimeoutSeconds, validatedMax).mapN((t, m) => impl(t, m, extension))
      }
    } else {
      ParseResult.fail("Invalid Keep-Alive header", "All fields of Keep-Alive were empty")
    }

  def unsafeApply(
      timeoutSeconds: Option[Long],
      max: Option[Long],
      extension: List[(String, Option[String])],
  ): `Keep-Alive` =
    apply(timeoutSeconds, max, extension).fold(throw _, identity)

  def parse(s: String): ParseResult[`Keep-Alive`] =
    ParseResult.fromParser(parser, "Invalid Keep-Alive header")(s)

  private def nonNegativeLong(l: Long, fieldName: String): ParseResult[Long] =
    if (l >= 0) ParseResult.success(l)
    else
      ParseResult.fail(
        s"Invalid long for $fieldName",
        s"$fieldName which was $l must be greater than or equal to 0 seconds",
      )

  private def safeToLong(s: String): Option[Long] =
    try Some(s.toLong)
    catch {
      case _: NumberFormatException => None
    }
  private val parser: Parser[`Keep-Alive`] = {
    import CommonRules.{headerRep1, quotedString, token}
    import Numbers.digits

    // "timeout" "=" delta-seconds
    val timeout: Parser[Timeout] =
      Parser.string("timeout=") *> digits.mapFilter(s => safeToLong(s).map(Timeout.apply))

    // "max" "=" 1*DIGIT
    val max: Parser[Max] =
      Parser.string("max=") *> digits.mapFilter(s => safeToLong(s).map(Max.apply))

    // keep-alive-extension = token [ "=" ( token / quoted-string ) ]
    val keepAliveExtension: Parser[Extension] =
      (token ~ (Parser.char('=') *> token.orElse(quotedString)).?).map(Extension.apply)

    /*
  keep-alive-info      = "timeout" "=" delta-seconds
                       / "max" "=" 1*DIGIT
                       / keep-alive-extension
     */
    val keepAliveInfo: Parser[Parameter] = timeout.orElse(max).orElse(keepAliveExtension)

    headerRep1(keepAliveInfo).map { nel =>
      var timeoutSeconds: Option[Long] = None
      var max: Option[Long] = None
      val extension: ListBuffer[(String, Option[String])] = ListBuffer.empty
      nel.foldLeft(()) { (_, ka) =>
        ka match {
          case Timeout(n) => if (timeoutSeconds.isEmpty) timeoutSeconds = Some(n) else ()
          case Max(n) => if (max.isEmpty) max = Some(n) else ()
          case Extension(p) => extension.append(p)
        }
      }
      unsafeApply(timeoutSeconds, max, extension.toList)
    }
  }

  private def impl(
      timeoutSeconds: Option[Long],
      max: Option[Long],
      extension: List[(String, Option[String])],
  ) =
    new `Keep-Alive`(timeoutSeconds, max, extension) {}

  implicit val headerInstance: Header[`Keep-Alive`, Header.Recurring] = Header.createRendered(
    ci"Keep-Alive",
    v =>
      new Renderable {
        def render(writer: Writer): writer.type =
          v match {
            case `Keep-Alive`(t, m, e) =>
              var hasWritten = false
              t.foreach { l =>
                writer << "timeout=" << l
                hasWritten = true
              }
              m.foreach { l =>
                if (hasWritten) writer << ", " else hasWritten = true
                writer << "max=" << l
              }

              e.foreach { p =>
                if (hasWritten) writer << ", " else hasWritten = true
                writer << p._1
                p._2.foreach { qts =>
                  writer << "="
                  writer.quote(
                    qts
                  )
                } // All tokens are valid if we quote them as if they were quoted-string
              }
              writer
          }
      },
    parse,
  )

  implicit val headerSemigroupInstance: Semigroup[`Keep-Alive`] = new Semigroup[`Keep-Alive`] {
    def combine(x: `Keep-Alive`, y: `Keep-Alive`): `Keep-Alive` =
      impl(
        x.timeoutSeconds.orElse(y.timeoutSeconds),
        x.max.orElse(y.max),
        x.extension ++ y.extension,
      )
  }

}

sealed abstract case class `Keep-Alive` private (
    timeoutSeconds: Option[Long],
    max: Option[Long],
    extension: List[(String, Option[String])],
) {
  def toTimeoutDuration: Option[FiniteDuration] =
    timeoutSeconds.map(FiniteDuration(_, TimeUnit.SECONDS))
}
