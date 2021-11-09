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

import cats.parse.Parser
import cats.parse.Parser0
import cats.syntax.either._
import org.http4s.internal.parsing.Rfc8941
import org.http4s.util.Renderable
import org.http4s.util.Writer

import java.util.Base64

/** Structured Field Values for HTTP (RFC8941)
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc8941]]
  */
sealed trait StructuredField extends Renderable

object StructuredField {

  // 3 top-level types: SfItem, SfList, SfDictionary

  // bare items

  sealed trait BareItem extends Renderable

  object BareItem {
    val parser: Parser[BareItem] =
      Rfc8941.bareItem(
        SfInteger.parser,
        SfDecimal.parser,
        SfString.parser,
        SfToken.parser,
        SfBinary.parser,
        SfBoolean.parser,
      )
  }

  sealed abstract case class SfInteger(value: Long) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer << value
  }

  object SfInteger {
    private val MaxValue: Long = 999999999999999L

    private val MinValue: Long = -MaxValue

    private def unsafeFromLong(l: Long): SfInteger =
      new SfInteger(l) {}

    def fromLong(l: Long): Option[SfInteger] =
      if (l < MinValue || l > MaxValue) None else Some(unsafeFromLong(l))

    val parser: Parser[SfInteger] =
      Rfc8941.sfInteger.map(unsafeFromLong)
  }

  sealed abstract case class SfDecimal(value: BigDecimal) extends BareItem {
    override def render(writer: Writer): writer.type =
      if (value.scale > 3)
        writer << value.setScale(3, BigDecimal.RoundingMode.HALF_UP).toString
      else
        writer << value.toString
  }

  object SfDecimal {
    private val MaxValue: BigDecimal = BigDecimal("999999999999.999")

    private val MinValue: BigDecimal = -MaxValue

    private def unsafeFromBigDecimal(d: BigDecimal): SfDecimal =
      new SfDecimal(d) {}

    def fromBigDecimal(d: BigDecimal): Option[SfDecimal] =
      if (d < MinValue || d > MaxValue) None else Some(unsafeFromBigDecimal(d))

    val parser: Parser[SfDecimal] =
      Rfc8941.sfDecimal.map(unsafeFromBigDecimal)
  }

  sealed abstract case class SfString(value: String) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer << value
  }

  object SfString {
    private def unsafeFromString(s: String): SfString =
      new SfString(s) {}

    def fromString(s: String): Option[SfString] =
      parser.parseAll(s).toOption

    val parser: Parser[SfString] =
      Rfc8941.sfString.map(unsafeFromString)
  }

  sealed abstract case class SfToken(value: String) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer << value
  }

  object SfToken {
    private def unsafeFromString(s: String): SfToken =
      new SfToken(s) {}

    def fromString(s: String): Option[SfToken] =
      parser.parseAll(s).toOption

    val parser: Parser[SfToken] =
      Rfc8941.sfToken.map(unsafeFromString)
  }

  sealed abstract case class SfBinary(value: String) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer << ':' << value << ':'

    // Some strings are valid for the parser, but will fail with the Base64 decoder
    def toBytes: Option[Array[Byte]] =
      Either
        .catchNonFatal(Base64.getDecoder.decode(value))
        .toOption
  }

  object SfBinary {
    private def unsafeFromString(s: String): SfBinary =
      new SfBinary(s) {}

    def fromBytes(b: Array[Byte]): Option[SfBinary] =
      Either
        .catchNonFatal(Base64.getEncoder.encodeToString(b))
        .map(unsafeFromString)
        .toOption

    val parser: Parser[SfBinary] =
      Rfc8941.sfBinary.map(unsafeFromString)
  }

  final case class SfBoolean(value: Boolean) extends BareItem {
    override def render(writer: Writer): writer.type =
      value match {
        case false => writer << "?0"
        case true => writer << "?1"
      }
  }

  object SfBoolean {
    val parser: Parser[SfBoolean] =
      Rfc8941.sfBoolean.map(apply)
  }

  // key & parameters

  sealed abstract case class Key(value: String) extends Renderable {
    override def render(writer: Writer): writer.type =
      writer << value
  }

  object Key {
    private[http4s] def unsafeFromString(s: String): Key =
      new Key(s) {}

    def fromString(s: String): Option[Key] =
      parser.parseAll(s).toOption

    val parser: Parser[Key] =
      Rfc8941.key.map(unsafeFromString)
  }

  final case class Parameters(values: List[(Key, BareItem)]) extends Renderable {
    private def toRenderable(kv: (Key, BareItem)): Renderable =
      new Renderable {
        def render(writer: Writer): writer.type =
          kv match {
            case (key, SfBoolean(true)) =>
              writer << ';' << key
            case (key, value) =>
              writer << ';' << key << '=' << value
          }
      }

    override def render(writer: Writer): writer.type =
      writer.addList(values.map(toRenderable), "", "", "")
  }

  object Parameters {
    val parser: Parser0[Parameters] =
      Rfc8941
        .parameters(Key.parser, BareItem.parser)
        .map(_.map(kv => (kv._1, kv._2.fold[BareItem](SfBoolean(true))(identity))))
        .map(apply)
  }

  // members

  sealed trait Member extends Renderable

  object Member {
    val parser: Parser[Member] =
      Rfc8941.member(SfItem.parser, InnerList.parser)
  }

  final case class SfItem(value: BareItem, params: Parameters) extends Member with StructuredField {
    override def render(writer: Writer): writer.type =
      writer << value << params
  }

  object SfItem {
    val parser: Parser[SfItem] =
      Rfc8941.sfItem(BareItem.parser, Parameters.parser).map(t => apply(t._1, t._2))
  }

  final case class InnerList(values: List[SfItem], params: Parameters) extends Member {
    override def render(writer: Writer): writer.type =
      writer.addList(values, " ", "(", ")").append(params)
  }

  object InnerList {
    val parser: Parser[InnerList] =
      Rfc8941.innerList(SfItem.parser, Parameters.parser).map(t => apply(t._1, t._2))
  }

  // containers

  final case class SfList(values: List[Member]) extends StructuredField {
    override def render(writer: Writer): writer.type =
      writer.addList(values, ", ", "", "")
  }

  object SfList {
    val parser: Parser[SfList] =
      Rfc8941.sfList(Member.parser).map(apply)
  }

  final case class SfDictionary(values: List[(Key, Member)]) extends StructuredField {
    private def toRenderable(kv: (Key, Member)): Renderable =
      new Renderable {
        def render(writer: Writer): writer.type =
          kv match {
            case (key, SfItem(SfBoolean(true), params)) =>
              writer << key << params
            case (key, value) =>
              writer << key << '=' << value
          }
      }

    override def render(writer: Writer): writer.type =
      writer.addList(values.map(toRenderable), ", ", "", "")
  }

  object SfDictionary {
    val parser: Parser[SfDictionary] =
      Rfc8941
        .sfDictionary(Key.parser, Parameters.parser, Member.parser)
        .map(_.map(kv => (kv._1, kv._2.fold(p => SfItem(SfBoolean(true), p), identity))))
        .map(apply)
  }
}
