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

package org.http4s.headers

import cats.{Semigroup, Eq}
import cats.data.NonEmptyList
import org.typelevel.ci._
import cats.parse.Parser
import org.http4s.Header
import org.http4s.internal.parsing.Rfc7230

sealed trait Vary {
  def ++(that: Vary): Vary = (this, that) match {
    case (Vary.`*`, _) | (_, Vary.`*`) => Vary.`*`
    case (Vary.HeaderList(these), Vary.HeaderList(those)) => Vary.HeaderList(these ++ those.toList)
  }
}

object Vary extends HeaderCompanion[Vary]("Vary") {
  def apply(headers: NonEmptyList[CIString]): Vary = HeaderList(headers)
  def apply(head: CIString, tail: CIString*): Vary = HeaderList(NonEmptyList.of(head, tail: _*))

  case object `*` extends Vary
  final case class HeaderList(headers: NonEmptyList[CIString]) extends Vary

  override private[http4s] val parser: Parser[Vary] =
    Parser.char('*').as(`*`).orElse(Rfc7230.headerRep1(Rfc7230.token.map(CIString(_))).map(apply))

  override implicit val headerInstance: Header[Vary, Header.Single] = createRendered {
    case HeaderList(values) => values
    case `*` => NonEmptyList.one(ci"*")
  }

  implicit val eqInstance: Eq[Vary] = Eq.fromUniversalEquals

  implicit val semigroupInstance: Semigroup[Vary] = Semigroup.instance(_ ++ _)
}
