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

import cats.Contravariant
import cats.Functor
import cats.Hash
import cats.MonoidK
import cats.Order
import cats.Show
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all._

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

final case class QueryParameterKey(value: String) extends AnyVal

object QueryParameterKey {
  implicit lazy val showInstance: Show[QueryParameterKey] =
    Show.show(_.value)

  implicit lazy val orderInstance: Order[QueryParameterKey] =
    Order.by(_.value)

  implicit lazy val hashInstance: Hash[QueryParameterKey] =
    Hash.by(_.value)
}

final case class QueryParameterValue(value: String) extends AnyVal

object QueryParameterValue {
  implicit lazy val showInstance: Show[QueryParameterValue] =
    Show.show(_.value)

  implicit lazy val orderInstance: Order[QueryParameterValue] =
    Order.by(_.value)

  implicit lazy val hashInstance: Hash[QueryParameterValue] =
    Hash.by(_.value)
}

/** type class defining the key of a query parameter
  * Usually used in conjunction with [[QueryParamEncoder]] and [[QueryParamDecoder]]
  *
  * Any [[QueryParam]] instance is also a valid [[QueryParamKeyLike]] instance
  * where the same key is used for all values.
  */
trait QueryParam[T] extends QueryParamKeyLike[T] {
  def key: QueryParameterKey
  override final def getKey(t: T): QueryParameterKey = key
}

object QueryParam {

  /** summon an implicit [[QueryParam]] */
  def apply[T](implicit ev: QueryParam[T]): QueryParam[T] = ev

  def fromKey[T](k: String): QueryParam[T] =
    new QueryParam[T] {
      def key: QueryParameterKey = QueryParameterKey(k)
    }
}

trait QueryParamKeyLike[T] {
  def getKey(t: T): QueryParameterKey
}

object QueryParamKeyLike {
  def apply[T](implicit ev: QueryParamKeyLike[T]): QueryParamKeyLike[T] = ev

  implicit val stringKey: QueryParamKeyLike[String] = new QueryParamKeyLike[String] {
    override def getKey(t: String): QueryParameterKey = QueryParameterKey(t)
  }
}

trait QueryParamCodec[T] extends QueryParamEncoder[T] with QueryParamDecoder[T]
object QueryParamCodec {
  def apply[A](implicit instance: QueryParamCodec[A]): QueryParamCodec[A] = instance

  def from[A](decodeA: QueryParamDecoder[A], encodeA: QueryParamEncoder[A]): QueryParamCodec[A] =
    new QueryParamCodec[A] {
      override def encode(value: A): QueryParameterValue = encodeA.encode(value)
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, A] =
        decodeA.decode(value)
    }

  def instantQueryParamCodec(formatter: DateTimeFormatter): QueryParamCodec[Instant] =
    instant(formatter)

  def localDateQueryParamCodec(formatter: DateTimeFormatter): QueryParamCodec[LocalDate] =
    localDate(formatter)

  def zonedDateTimeQueryParamCodec(formatter: DateTimeFormatter): QueryParamCodec[ZonedDateTime] =
    zonedDateTime(formatter)

  def instant(formatter: DateTimeFormatter): QueryParamCodec[Instant] =
    QueryParamCodec.from(QueryParamDecoder.instant(formatter), QueryParamEncoder.instant(formatter))

  def localDate(formatter: DateTimeFormatter): QueryParamCodec[LocalDate] =
    QueryParamCodec.from(
      QueryParamDecoder.localDate(formatter),
      QueryParamEncoder.localDate(formatter),
    )

  def localTime(formatter: DateTimeFormatter): QueryParamCodec[LocalTime] =
    QueryParamCodec.from(
      QueryParamDecoder.localTime(formatter),
      QueryParamEncoder.localTime(formatter),
    )

  def localDateTime(formatter: DateTimeFormatter): QueryParamCodec[LocalDateTime] =
    QueryParamCodec.from(
      QueryParamDecoder.localDateTime(formatter),
      QueryParamEncoder.localDateTime(formatter),
    )

  def dayOfWeek(formatter: DateTimeFormatter): QueryParamCodec[DayOfWeek] =
    QueryParamCodec.from(
      QueryParamDecoder.dayOfWeek(formatter),
      QueryParamEncoder.dayOfWeek(formatter),
    )

  def month(formatter: DateTimeFormatter): QueryParamCodec[Month] =
    QueryParamCodec.from(QueryParamDecoder.month(formatter), QueryParamEncoder.month(formatter))

  def monthDay(formatter: DateTimeFormatter): QueryParamCodec[MonthDay] =
    QueryParamCodec.from(
      QueryParamDecoder.monthDay(formatter),
      QueryParamEncoder.monthDay(formatter),
    )

  def year(formatter: DateTimeFormatter): QueryParamCodec[Year] =
    QueryParamCodec.from(QueryParamDecoder.year(formatter), QueryParamEncoder.year(formatter))

  def yearMonth(formatter: DateTimeFormatter): QueryParamCodec[YearMonth] =
    QueryParamCodec.from(
      QueryParamDecoder.yearMonth(formatter),
      QueryParamEncoder.yearMonth(formatter),
    )

  def zoneOffset(formatter: DateTimeFormatter): QueryParamCodec[ZoneOffset] =
    QueryParamCodec.from(
      QueryParamDecoder.zoneOffset(formatter),
      QueryParamEncoder.zoneOffset(formatter),
    )

  def zonedDateTime(formatter: DateTimeFormatter): QueryParamCodec[ZonedDateTime] =
    QueryParamCodec.from(
      QueryParamDecoder.zonedDateTime(formatter),
      QueryParamEncoder.zonedDateTime(formatter),
    )

  def offsetTime(formatter: DateTimeFormatter): QueryParamCodec[OffsetTime] =
    QueryParamCodec.from(
      QueryParamDecoder.offsetTime(formatter),
      QueryParamEncoder.offsetTime(formatter),
    )

  def offsetDateTime(formatter: DateTimeFormatter): QueryParamCodec[OffsetDateTime] =
    QueryParamCodec.from(
      QueryParamDecoder.offsetDateTime(formatter),
      QueryParamEncoder.offsetDateTime(formatter),
    )

  lazy val zoneId: QueryParamCodec[ZoneId] =
    QueryParamCodec.from(QueryParamDecoder.zoneId, QueryParamEncoder.zoneId)

  lazy val period: QueryParamCodec[Period] =
    QueryParamCodec.from(QueryParamDecoder.period, QueryParamEncoder.period)
}

/** Type class defining how to encode a `T` as a [[QueryParameterValue]]s
  * @see QueryParamCodecLaws
  */
trait QueryParamEncoder[T] { outer =>
  def encode(value: T): QueryParameterValue

  /** QueryParamEncoder is a contravariant functor. */
  def contramap[U](f: U => T): QueryParamEncoder[U] =
    new QueryParamEncoder[U] {
      override def encode(value: U): QueryParameterValue =
        outer.encode(f(value))
    }
}

object QueryParamEncoder {

  /** summon an implicit [[QueryParamEncoder]] */
  def apply[T](implicit ev: QueryParamEncoder[T]): QueryParamEncoder[T] = ev

  def fromCodec[T](implicit ev: QueryParamCodec[T]): QueryParamEncoder[T] = ev

  /** QueryParamEncoder is a contravariant functor. */
  implicit val ContravariantQueryParamEncoder: Contravariant[QueryParamEncoder] =
    new Contravariant[QueryParamEncoder] {
      override def contramap[A, B](fa: QueryParamEncoder[A])(f: B => A): QueryParamEncoder[B] =
        fa.contramap(f)
    }

  def fromShow[T](implicit
      sh: Show[T]
  ): QueryParamEncoder[T] =
    stringQueryParamEncoder.contramap(sh.show)

  implicit lazy val booleanQueryParamEncoder: QueryParamEncoder[Boolean] = fromShow[Boolean]
  implicit lazy val doubleQueryParamEncoder: QueryParamEncoder[Double] = fromShow[Double]
  implicit lazy val floatQueryParamEncoder: QueryParamEncoder[Float] = fromShow[Float]
  implicit lazy val shortQueryParamEncoder: QueryParamEncoder[Short] = fromShow[Short]
  implicit lazy val intQueryParamEncoder: QueryParamEncoder[Int] = fromShow[Int]
  implicit lazy val longQueryParamEncoder: QueryParamEncoder[Long] = fromShow[Long]

  implicit lazy val stringQueryParamEncoder: QueryParamEncoder[String] =
    new QueryParamEncoder[String] {
      override def encode(value: String): QueryParameterValue =
        QueryParameterValue(value)
    }

  implicit lazy val uriQueryParamEncoder: QueryParamEncoder[Uri] =
    QueryParamEncoder[String].contramap(_.renderString)

  def instantQueryParamEncoder(formatter: DateTimeFormatter): QueryParamEncoder[Instant] =
    instant(formatter)

  def localDateQueryParamEncoder(formatter: DateTimeFormatter): QueryParamEncoder[LocalDate] =
    localDate(formatter)

  def zonedDateTimeQueryParamEncoder(
      formatter: DateTimeFormatter
  ): QueryParamEncoder[ZonedDateTime] =
    zonedDateTime(formatter)

  def instant(formatter: DateTimeFormatter): QueryParamEncoder[Instant] =
    javaTimeQueryParamEncoder(formatter)

  def localDate(formatter: DateTimeFormatter): QueryParamEncoder[LocalDate] =
    javaTimeQueryParamEncoder(formatter)

  def localTime(formatter: DateTimeFormatter): QueryParamEncoder[LocalTime] =
    javaTimeQueryParamEncoder(formatter)

  def localDateTime(formatter: DateTimeFormatter): QueryParamEncoder[LocalDateTime] =
    javaTimeQueryParamEncoder(formatter)

  def dayOfWeek(formatter: DateTimeFormatter): QueryParamEncoder[DayOfWeek] =
    javaTimeQueryParamEncoder(formatter)

  def month(formatter: DateTimeFormatter): QueryParamEncoder[Month] =
    javaTimeQueryParamEncoder(formatter)

  def monthDay(formatter: DateTimeFormatter): QueryParamEncoder[MonthDay] =
    javaTimeQueryParamEncoder(formatter)

  def year(formatter: DateTimeFormatter): QueryParamEncoder[Year] =
    javaTimeQueryParamEncoder(formatter)

  def yearMonth(formatter: DateTimeFormatter): QueryParamEncoder[YearMonth] =
    javaTimeQueryParamEncoder(formatter)

  def zoneOffset(formatter: DateTimeFormatter): QueryParamEncoder[ZoneOffset] =
    javaTimeQueryParamEncoder(formatter)

  def zonedDateTime(formatter: DateTimeFormatter): QueryParamEncoder[ZonedDateTime] =
    javaTimeQueryParamEncoder(formatter)

  def offsetTime(formatter: DateTimeFormatter): QueryParamEncoder[OffsetTime] =
    javaTimeQueryParamEncoder(formatter)

  def offsetDateTime(formatter: DateTimeFormatter): QueryParamEncoder[OffsetDateTime] =
    javaTimeQueryParamEncoder(formatter)

  private def javaTimeQueryParamEncoder[T <: TemporalAccessor](
      formatter: DateTimeFormatter
  ): QueryParamEncoder[T] =
    QueryParamEncoder[String].contramap[T](formatter.format)

  implicit lazy val zoneId: QueryParamEncoder[ZoneId] = QueryParamEncoder[String].contramap(_.getId)

  implicit lazy val period: QueryParamEncoder[Period] =
    QueryParamEncoder[String].contramap(_.toString)
}

/** Type class defining how to decode a [[QueryParameterValue]] into a `T`
  * @see QueryParamCodecLaws
  */
trait QueryParamDecoder[T] { outer =>
  def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, T]

  /** QueryParamDecoder is a covariant functor. */
  def map[U](f: T => U): QueryParamDecoder[U] =
    new QueryParamDecoder[U] {
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, U] =
        outer.decode(value).map(f)
    }

  /** Use another decoder if this one fails. */
  def orElse[U >: T](qpd: QueryParamDecoder[U]): QueryParamDecoder[U] =
    new QueryParamDecoder[U] {
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, U] =
        outer.decode(value).orElse(qpd.decode(value))
    }

  /** Validate the currently parsed value a function to Either[ParseFailure, *]. */
  def emap[U](f: T => Either[ParseFailure, U]): QueryParamDecoder[U] =
    emapValidatedNel(f.andThen(_.toValidatedNel))

  /** Validate the currently parsed value using a function to ValidatedNel[ParseFailure, *]. */
  def emapValidatedNel[U](f: T => ValidatedNel[ParseFailure, U]): QueryParamDecoder[U] =
    new QueryParamDecoder[U] {
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, U] =
        outer.decode(value).andThen(f)
    }
}

object QueryParamDecoder {

  /** summon an implicit [[QueryParamDecoder]] */
  def apply[T](implicit ev: QueryParamDecoder[T]): QueryParamDecoder[T] = ev

  def fromCodec[T](implicit ev: QueryParamCodec[T]): QueryParamDecoder[T] = ev

  def fromUnsafeCast[T](cast: QueryParameterValue => T)(typeName: String): QueryParamDecoder[T] =
    new QueryParamDecoder[T] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, T] =
        Validated
          .catchNonFatal(cast(value))
          .leftMap(t => ParseFailure(s"Query decoding $typeName failed", t.getMessage))
          .toValidatedNel
    }

  /** QueryParamDecoder is a covariant functor. */
  implicit val FunctorQueryParamDecoder: Functor[QueryParamDecoder] =
    new Functor[QueryParamDecoder] {
      override def map[A, B](fa: QueryParamDecoder[A])(f: A => B): QueryParamDecoder[B] =
        fa.map(f)
    }

  /** QueryParamDecoder is a MonoidK. */
  implicit val PlusEmptyQueryParamDecoder: MonoidK[QueryParamDecoder] =
    new MonoidK[QueryParamDecoder] {
      def empty[A]: QueryParamDecoder[A] =
        fail[A]("Decoding failed.", "Empty decoder (always fails).")
      def combineK[A](a: QueryParamDecoder[A], b: QueryParamDecoder[A]): QueryParamDecoder[A] =
        a.orElse(b)
    }

  /** A decoder that always succeeds. */
  def success[A](a: A): QueryParamDecoder[A] =
    fromUnsafeCast[A](_ => a)("Success")

  /** A decoder that always fails. */
  def fail[A](sanitized: String, detail: String): QueryParamDecoder[A] =
    new QueryParamDecoder[A] {
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, A] =
        ParseFailure(sanitized, detail).invalidNel
    }

  implicit lazy val unitQueryParamDecoder: QueryParamDecoder[Unit] =
    success(())
  implicit lazy val booleanQueryParamDecoder: QueryParamDecoder[Boolean] =
    fromUnsafeCast[Boolean](_.value.toBoolean)("Boolean")
  implicit lazy val doubleQueryParamDecoder: QueryParamDecoder[Double] =
    fromUnsafeCast[Double](_.value.toDouble)("Double")
  implicit lazy val floatQueryParamDecoder: QueryParamDecoder[Float] =
    fromUnsafeCast[Float](_.value.toFloat)("Float")
  implicit lazy val shortQueryParamDecoder: QueryParamDecoder[Short] =
    fromUnsafeCast[Short](_.value.toShort)("Short")
  implicit lazy val intQueryParamDecoder: QueryParamDecoder[Int] =
    fromUnsafeCast[Int](_.value.toInt)("Int")
  implicit lazy val longQueryParamDecoder: QueryParamDecoder[Long] =
    fromUnsafeCast[Long](_.value.toLong)("Long")

  implicit lazy val charQueryParamDecoder: QueryParamDecoder[Char] = new QueryParamDecoder[Char] {
    def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, Char] =
      if (value.value.size == 1) value.value.head.validNel
      else
        ParseFailure(
          "Failed to parse Char query parameter",
          s"Could not parse ${value.value} as a Char",
        ).invalidNel
  }

  implicit lazy val stringQueryParamDecoder: QueryParamDecoder[String] =
    new QueryParamDecoder[String] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, String] =
        value.value.validNel
    }

  implicit val uriQueryParamDecoder: QueryParamDecoder[Uri] =
    new QueryParamDecoder[Uri] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, Uri] =
        Uri.fromString(value.value).toValidatedNel
    }

  def instantQueryParamDecoder(formatter: DateTimeFormatter): QueryParamDecoder[Instant] =
    instant(formatter)

  def localDateQueryParamDecoder(formatter: DateTimeFormatter): QueryParamDecoder[LocalDate] =
    localDate(formatter)

  def zonedDateTimeQueryParamDecoder(
      formatter: DateTimeFormatter
  ): QueryParamDecoder[ZonedDateTime] =
    zonedDateTime(formatter)

  def instant(formatter: DateTimeFormatter): QueryParamDecoder[Instant] =
    javaTimeQueryParamDecoder(formatter, Instant.from, "Instant")

  def localDate(formatter: DateTimeFormatter): QueryParamDecoder[LocalDate] =
    javaTimeQueryParamDecoder(formatter, LocalDate.from, "LocalDate")

  def localTime(formatter: DateTimeFormatter): QueryParamDecoder[LocalTime] =
    javaTimeQueryParamDecoder(formatter, LocalTime.from, "LocalTime")

  def localDateTime(formatter: DateTimeFormatter): QueryParamDecoder[LocalDateTime] =
    javaTimeQueryParamDecoder(formatter, LocalDateTime.from, "LocalDateTime")

  def dayOfWeek(formatter: DateTimeFormatter): QueryParamDecoder[DayOfWeek] =
    javaTimeQueryParamDecoder(formatter, DayOfWeek.from, "DayOfWeek")

  def month(formatter: DateTimeFormatter): QueryParamDecoder[Month] =
    javaTimeQueryParamDecoder(formatter, Month.from, "Month")

  def monthDay(formatter: DateTimeFormatter): QueryParamDecoder[MonthDay] =
    javaTimeQueryParamDecoder(formatter, MonthDay.from, "MonthDay")

  def year(formatter: DateTimeFormatter): QueryParamDecoder[Year] =
    javaTimeQueryParamDecoder(formatter, Year.from, "Year")

  def yearMonth(formatter: DateTimeFormatter): QueryParamDecoder[YearMonth] =
    javaTimeQueryParamDecoder(formatter, YearMonth.from, "YearMonth")

  def zoneOffset(formatter: DateTimeFormatter): QueryParamDecoder[ZoneOffset] =
    javaTimeQueryParamDecoder(formatter, ZoneOffset.from, "ZoneOffset")

  def zonedDateTime(formatter: DateTimeFormatter): QueryParamDecoder[ZonedDateTime] =
    javaTimeQueryParamDecoder(formatter, ZonedDateTime.from, "ZonedDateTime")

  def offsetTime(formatter: DateTimeFormatter): QueryParamDecoder[OffsetTime] =
    javaTimeQueryParamDecoder(formatter, OffsetTime.from, "OffsetTime")

  def offsetDateTime(formatter: DateTimeFormatter): QueryParamDecoder[OffsetDateTime] =
    javaTimeQueryParamDecoder(formatter, OffsetDateTime.from, "OffsetDateTime")

  private def javaTimeQueryParamDecoder[T](
      formatter: DateTimeFormatter,
      fromTemporalAccessor: TemporalAccessor => T,
      displayName: String,
  ): QueryParamDecoder[T] =
    (value: QueryParameterValue) =>
      Validated
        .catchNonFatal(fromTemporalAccessor(formatter.parse(value.value)))
        .leftMap(e =>
          ParseFailure(s"Failed to decode value ${value.value} as $displayName", e.getMessage)
        )
        .toValidatedNel

  implicit lazy val zoneId: QueryParamDecoder[ZoneId] =
    javaTimeQueryParamDecoderFromString(ZoneId.of, "ZoneId")

  implicit lazy val period: QueryParamDecoder[Period] =
    javaTimeQueryParamDecoderFromString(Period.parse, "Period")

  private def javaTimeQueryParamDecoderFromString[T](
      parse: String => T,
      displayName: String,
  ): QueryParamDecoder[T] = QueryParamDecoder[String].emap(s =>
    Either
      .catchNonFatal(parse(s))
      .leftMap(e => ParseFailure(s"Failed to decode value $s as $displayName", e.getMessage))
  )

}
