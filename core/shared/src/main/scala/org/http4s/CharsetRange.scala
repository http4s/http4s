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

import cats.Eq
import cats.Show
import org.http4s.util._

sealed abstract class CharsetRange extends HasQValue with Renderable {
  def qValue: QValue
  def withQValue(q: QValue): CharsetRange

  /** True if this charset range matches the charset.
    *
    * @since 0.16.1
    */
  final def matches(charset: Charset): Boolean =
    this match {
      case CharsetRange.`*`(_) => true
      case CharsetRange.Atom(cs, _) => charset == cs
    }
}

object CharsetRange {
  sealed case class `*`(qValue: QValue) extends CharsetRange {
    override final def withQValue(q: QValue): CharsetRange.`*` = copy(qValue = q)

    final def render(writer: Writer): writer.type = writer << "*" << qValue
  }

  object `*` extends `*`(QValue.One)

  final case class Atom protected[http4s] (charset: Charset, qValue: QValue = QValue.One)
      extends CharsetRange {
    override def withQValue(q: QValue): CharsetRange.Atom = copy(qValue = q)

    def render(writer: Writer): writer.type = writer << charset << qValue
  }

  implicit def fromCharset(cs: Charset): CharsetRange.Atom = cs.toRange

  implicit val http4sEqForCharsetRange: Eq[CharsetRange] = Eq.fromUniversalEquals
  implicit val http4sShowForCharsetRange: Show[CharsetRange] = Show.fromToString
}
