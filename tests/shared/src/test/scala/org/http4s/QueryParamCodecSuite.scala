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

import cats._
import cats.data._
import cats.laws.discipline.{arbitrary => _, _}
import cats.syntax.all._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Cogen
import org.scalacheck.Prop._

import java.time._
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField.MONTH_OF_YEAR
import java.time.temporal.ChronoField.YEAR

class QueryParamCodecSuite extends Http4sSuite with QueryParamCodecInstances {
  checkAll("Boolean QueryParamCodec", QueryParamCodecLaws[Boolean])
  checkAll("Double QueryParamCodec", QueryParamCodecLaws[Double])
  checkAll("Float QueryParamCodec", QueryParamCodecLaws[Float])
  checkAll("Short QueryParamCodec", QueryParamCodecLaws[Short])
  checkAll("Int QueryParamCodec", QueryParamCodecLaws[Int])
  checkAll("Long QueryParamCodec", QueryParamCodecLaws[Long])
  checkAll("String QueryParamCodec", QueryParamCodecLaws[String])
  checkAll("Instant QueryParamCodec", QueryParamCodecLaws[Instant])
  checkAll("LocalDate QueryParamCodec", QueryParamCodecLaws[LocalDate])
  checkAll("LocalTime QueryParamCodec", QueryParamCodecLaws[LocalTime])
  checkAll("LocalDateTime QueryParamCodec", QueryParamCodecLaws[LocalDateTime])
  if (Platform.isJvm) { // enum arbitraries are implemented with reflection
    checkAll("DayOfWeek QueryParamCodec", QueryParamCodecLaws[DayOfWeek])
    checkAll("Month QueryParamCodec", QueryParamCodecLaws[Month])
    checkAll("MonthDay QueryParamCodec", QueryParamCodecLaws[MonthDay])
  }
  checkAll("Year QueryParamCodec", QueryParamCodecLaws[Year])
  checkAll("YearMonth QueryParamCodec", QueryParamCodecLaws[YearMonth])
  checkAll("ZoneOffset QueryParamCodec", QueryParamCodecLaws[ZoneOffset])
  checkAll("ZoneId QueryParamCodec", QueryParamCodecLaws[ZoneId])
  checkAll("Period QueryParamCodec", QueryParamCodecLaws[Period])

  if (!sys.props.get("java.specification.version").contains("1.8")) {
    // skipping this property due to bug in JDK8
    // where round trip conversion fails for region-based zone id & daylight saving time
    // see https://bugs.openjdk.java.net/browse/JDK-8183553 and https://bugs.openjdk.java.net/browse/JDK-8066982
    checkAll("ZonedDateTime QueryParamCodec", QueryParamCodecLaws[ZonedDateTime])
    checkAll("OffsetTime QueryParamCodec", QueryParamCodecLaws[OffsetTime])
    checkAll("OffsetDateTime QueryParamCodec", QueryParamCodecLaws[OffsetDateTime])

    test("QueryParamCodec[ZonedDateTime] handles DateTimeException") {
      implicit val zonedDateTimeQueryParamCodec: QueryParamCodec[ZonedDateTime] =
        QueryParamCodec.zonedDateTimeQueryParamCodec(DateTimeFormatter.ISO_INSTANT)
      forAll { (instant: Instant) =>
        QueryParamDecoder[ZonedDateTime].decode(QueryParameterValue(instant.toString)) match {
          case Validated.Invalid(NonEmptyList(ParseFailure(_, _), _)) => ()
          case Validated.Valid(zdt) => fail(s"Parsing incorrectly succeeded with $zdt")
        }
      }
    }
  }

  // Law checks for instances.
  checkAll(
    "Functor[QueryParamDecoder]",
    FunctorTests[QueryParamDecoder].functor[Int, String, Boolean],
  )
  checkAll("MonoidK[QueryParamDecoder]", MonoidKTests[QueryParamDecoder].monoidK[Int])
  checkAll(
    "Contravariant[QueryParamEncoder]",
    ContravariantTests[QueryParamEncoder].contravariant[Int, String, Boolean],
  )

  // The PlusEmpty check above validates fail() but we need an explicit test for success().
  test("success(a) always succeeds") {
    forAll { (n: Int, qpv: QueryParameterValue) =>
      QueryParamDecoder.success(n).decode(qpv) == n.valid
    }
  }
}

trait QueryParamCodecInstances {

  // We will assume for the purposes of testing that QueryParamDecoders are equal if they
  // produce the same result for a bunch of arbitrary strings.
  implicit def EqQueryParamDecoder[A: Eq]: Eq[QueryParamDecoder[A]] = {
    val vnp = Eq[Validated[NonEmptyList[ParseFailure], A]]
    Eq.instance { (x, y) =>
      val ss = List.fill(100)(arbitrary[String].sample).flatten.map(QueryParameterValue.apply)
      ss.forall(s => vnp.eqv(x.decode(s), y.decode(s)))
    }
  }

  // We will assume for the purposes of testing that QueryParamEncoders are equal if they
  // produce the same result for a bunch of arbitrary inputs.
  implicit def EqQueryParamEncoder[A: Arbitrary]: Eq[QueryParamEncoder[A]] =
    Eq.instance { (x, y) =>
      val as = List.fill(100)(arbitrary[A].sample).flatten
      as.forall(a => x.encode(a) == y.encode(a))
    }

  implicit val eqInstant: Eq[Instant] = Eq.fromUniversalEquals

  implicit val eqLocalDate: Eq[LocalDate] = Eq.fromUniversalEquals

  implicit val eqLocalTime: Eq[LocalTime] = Eq.fromUniversalEquals

  implicit val eqLocalDateTime: Eq[LocalDateTime] = Eq.fromUniversalEquals

  implicit val eqDayOfWeek: Eq[DayOfWeek] = Eq.fromUniversalEquals

  implicit val eqMonth: Eq[Month] = Eq.fromUniversalEquals

  implicit val eqMonthDay: Eq[MonthDay] = Eq.fromUniversalEquals

  implicit val eqYear: Eq[Year] = Eq.fromUniversalEquals

  implicit val eqYearWeek: Eq[YearMonth] = Eq.fromUniversalEquals

  implicit val eqZoneOffset: Eq[ZoneOffset] = Eq.fromUniversalEquals

  implicit val eqZonedDateTime: Eq[ZonedDateTime] = Eq.fromUniversalEquals

  implicit val eqOffsetTime: Eq[OffsetTime] = Eq.fromUniversalEquals

  implicit val eqOffsetDateTime: Eq[OffsetDateTime] = Eq.fromUniversalEquals

  implicit val eqZoneId: Eq[ZoneId] = Eq.fromUniversalEquals

  implicit val eqPeriod: Eq[Period] = Eq.fromUniversalEquals

  implicit def ArbQueryParamDecoder[A: Arbitrary]: Arbitrary[QueryParamDecoder[A]] =
    Arbitrary(arbitrary[String => A].map(QueryParamDecoder[String].map))

  implicit def ArbQueryParamEncoder[A: Cogen]: Arbitrary[QueryParamEncoder[A]] =
    Arbitrary(arbitrary[A => String].map(QueryParamEncoder[String].contramap))

  implicit val ArbQueryParameterValue: Arbitrary[QueryParameterValue] =
    Arbitrary(arbitrary[String].map(QueryParameterValue.apply _))

  implicit val instantQueryParamCodec: QueryParamCodec[Instant] =
    QueryParamCodec.instantQueryParamCodec(DateTimeFormatter.ISO_INSTANT)

  implicit val localDateQueryParamCodec: QueryParamCodec[LocalDate] =
    QueryParamCodec.localDate(DateTimeFormatter.ISO_LOCAL_DATE)

  implicit val localTimeQueryParamCodec: QueryParamCodec[LocalTime] =
    QueryParamCodec.localTime(DateTimeFormatter.ISO_LOCAL_TIME)

  implicit val localDateTimeQueryParamCodec: QueryParamCodec[LocalDateTime] =
    QueryParamCodec.localDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  implicit val dayOfWeekQueryParamCodec: QueryParamCodec[DayOfWeek] =
    QueryParamCodec.dayOfWeek(DateTimeFormatter.ofPattern("E"))

  implicit val monthQueryParamCodec: QueryParamCodec[Month] =
    QueryParamCodec.month(DateTimeFormatter.ofPattern("MM"))

  implicit val monthDayQueryParamCodec: QueryParamCodec[MonthDay] =
    QueryParamCodec.monthDay(DateTimeFormatter.ofPattern("--MM-dd"))

  implicit val yearQueryParamCodec: QueryParamCodec[Year] = QueryParamCodec.year(
    new DateTimeFormatterBuilder().appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD).toFormatter
  )

  implicit val yearMonthQueryParamCodec: QueryParamCodec[YearMonth] = QueryParamCodec.yearMonth(
    new DateTimeFormatterBuilder()
      .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
      .appendLiteral('-')
      .appendValue(MONTH_OF_YEAR, 2)
      .toFormatter
  )

  implicit val zoneOffsetQueryParamCodec: QueryParamCodec[ZoneOffset] =
    QueryParamCodec.zoneOffset(DateTimeFormatter.ofPattern("XXXXX"))

  implicit val zonedDateTimeQueryParamCodec: QueryParamCodec[ZonedDateTime] =
    QueryParamCodec.zonedDateTime(DateTimeFormatter.ISO_ZONED_DATE_TIME)

  implicit val offsetTimeQueryParamCodec: QueryParamCodec[OffsetTime] =
    QueryParamCodec.offsetTime(DateTimeFormatter.ISO_OFFSET_TIME)

  implicit val offsetDateTimeQueryParamCodec: QueryParamCodec[OffsetDateTime] =
    QueryParamCodec.offsetDateTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  implicit val zoneIdQueryParamCodec: QueryParamCodec[ZoneId] = QueryParamCodec.zoneId

  implicit val periodQueryParamCodec: QueryParamCodec[Period] = QueryParamCodec.period

}
