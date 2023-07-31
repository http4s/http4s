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
import cats.parse._
import org.http4s.CacheDirective._
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc2616
import org.http4s.parser.AdditionalRules
import org.typelevel.ci._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

object `Cache-Control` extends HeaderCompanion[`Cache-Control`]("Cache-Control") {
  def apply(head: CacheDirective, tail: CacheDirective*): `Cache-Control` =
    apply(NonEmptyList(head, tail.toList))

  private[http4s] val FieldNames: Parser[NonEmptyList[String]] =
    CommonRules.quotedString.repSep(CommonRules.listSep)
  private[http4s] val DeltaSeconds: Parser[Duration] =
    AdditionalRules.NonNegativeLong.map(Duration(_, TimeUnit.SECONDS))

  private[http4s] val CacheDirective: Parser[CacheDirective] =
    Parser.oneOf(
      (Parser.ignoreCase("no-cache") *> (Parser.string("=") *> FieldNames).?).map { fn =>
        `no-cache`(fn.map(_.map(CIString(_)).toList).getOrElse(Nil))
      } ::
        Parser.ignoreCase("no-store").as(`no-store`) ::
        Parser.ignoreCase("no-transform").as(`no-transform`) ::
        Parser.ignoreCase("max-age=") *> DeltaSeconds.map(s => `max-age`(s)) ::
        Parser.ignoreCase("max-stale") *> (Parser.string("=") *> DeltaSeconds).?.map(s =>
          `max-stale`(s)
        ) ::
        Parser.ignoreCase("min-fresh=") *> DeltaSeconds.map(s => `min-fresh`(s)) ::
        Parser.ignoreCase("only-if-cached").as(`only-if-cached`) ::
        Parser.ignoreCase("public").as(`public`) ::
        (Parser.ignoreCase("private") *> (Parser.string("=") *> FieldNames).?.map { fn =>
          `private`(fn.map(_.map(CIString(_)).toList).getOrElse(Nil))
        }) ::
        Parser.ignoreCase("must-revalidate").as(`must-revalidate`) ::
        Parser.ignoreCase("proxy-revalidate").as(`proxy-revalidate`) ::
        Parser.ignoreCase("s-maxage=") *> DeltaSeconds.map(s => `s-maxage`(s)) ::
        Parser.ignoreCase("stale-if-error=") *> DeltaSeconds.map(s => `stale-if-error`(s)) ::
        Parser.ignoreCase("stale-while-revalidate=") *> DeltaSeconds.map(s =>
          `stale-while-revalidate`(s)
        ) ::
        (Rfc2616.token ~ (Parser.string("=") *> (Rfc2616.token | CommonRules.quotedString)).?).map {
          case (name: String, arg: Option[String]) =>
            org.http4s.CacheDirective(CIString(name), arg)
        } :: Nil
    )

  private[http4s] val parser: Parser[`Cache-Control`] =
    CacheDirective.repSep(CommonRules.listSep).map(`Cache-Control`(_))

  implicit val headerInstance: Header[`Cache-Control`, Header.Recurring] =
    createRendered(
      _.values
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Cache-Control`] =
    (a, b) => `Cache-Control`(a.values.concatNel(b.values))

}

final case class `Cache-Control`(values: NonEmptyList[CacheDirective])
