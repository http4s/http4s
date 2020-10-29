/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats._
import cats.data._
import cats.implicits._
import cats.laws.discipline.{arbitrary => _, _}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}

import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._

class QueryParamCodecSpec extends Http4sSpec with QueryParamCodecInstances {
  checkAll("Boolean QueryParamCodec", QueryParamCodecLaws[Boolean])
  checkAll("Double QueryParamCodec", QueryParamCodecLaws[Double])
  checkAll("Float QueryParamCodec", QueryParamCodecLaws[Float])
  checkAll("Short QueryParamCodec", QueryParamCodecLaws[Short])
  checkAll("Int QueryParamCodec", QueryParamCodecLaws[Int])
  checkAll("Long QueryParamCodec", QueryParamCodecLaws[Long])
  checkAll("String QueryParamCodec", QueryParamCodecLaws[String])
  checkAll("Instant QueryParamCodec", QueryParamCodecLaws[Instant])
  checkAll("LocalDate QueryParamCodec", QueryParamCodecLaws[LocalDate])
  checkAll("ZonedDateTime QueryParamCodec", QueryParamCodecLaws[ZonedDateTime])

  // Law checks for instances.
  checkAll(
    "Functor[QueryParamDecoder]",
    FunctorTests[QueryParamDecoder].functor[Int, String, Boolean])
  checkAll("MonoidK[QueryParamDecoder]", MonoidKTests[QueryParamDecoder].monoidK[Int])
  checkAll(
    "Contravariant[QueryParamEncoder]",
    ContravariantTests[QueryParamEncoder].contravariant[Int, String, Boolean])

  // The PlusEmpty check above validates fail() but we need an explicit test for success().
  "success(a) always succeeds" >> forAll { (n: Int, qpv: QueryParameterValue) =>
    QueryParamDecoder.success(n).decode(qpv) must_== n.valid
  }
}

trait QueryParamCodecInstances { this: Http4sSpec =>

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
      as.forall(a => x.encode(a) === y.encode(a))
    }

  implicit val eqInstant: Eq[Instant] = Eq.fromUniversalEquals[Instant]

  implicit val eqLocalDate: Eq[LocalDate] = Eq.fromUniversalEquals[LocalDate]

  implicit val eqZonedDateTime: Eq[ZonedDateTime] = Eq.fromUniversalEquals[ZonedDateTime]

  implicit def ArbQueryParamDecoder[A: Arbitrary]: Arbitrary[QueryParamDecoder[A]] =
    Arbitrary(arbitrary[String => A].map(QueryParamDecoder[String].map))

  implicit def ArbQueryParamEncoder[A: Cogen]: Arbitrary[QueryParamEncoder[A]] =
    Arbitrary(arbitrary[A => String].map(QueryParamEncoder[String].contramap))

  implicit val ArbQueryParameterValue: Arbitrary[QueryParameterValue] =
    Arbitrary(arbitrary[String].map(QueryParameterValue.apply _))

  implicit val instantQueryParamCodec: QueryParamCodec[Instant] =
    QueryParamCodec.instantQueryParamCodec(DateTimeFormatter.ISO_INSTANT)

  implicit val localDateQueryParamCodec: QueryParamCodec[LocalDate] =
    QueryParamCodec.localDateQueryParamCodec(DateTimeFormatter.ISO_LOCAL_DATE)

  implicit val zonedDateTimeQueryParamCodec: QueryParamCodec[ZonedDateTime] =
    QueryParamCodec.zonedDateTimeQueryParamCodec(DateTimeFormatter.ISO_ZONED_DATE_TIME)

  implicit val ArbitraryInstant: Arbitrary[Instant] =
    Arbitrary(
      Gen
        .choose[Long](Instant.MIN.getEpochSecond, Instant.MAX.getEpochSecond)
        .map(Instant.ofEpochSecond))

  implicit val ArbitraryLocalDate: Arbitrary[LocalDate] =
    Arbitrary(
      Gen
        .choose[Long](LocalDate.MIN.toEpochDay, LocalDate.MAX.toEpochDay)
        .map(LocalDate.ofEpochDay))

  implicit val ArbitraryZonedDateTime: Arbitrary[ZonedDateTime] = {
    val zoneIds = {
      val zoneIdIter = ZoneId.getAvailableZoneIds.asScala.iterator
      // Workaround for a bug in Java 1.8:
      //   - https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8138664
      // See also the original issue:
      //   - https://github.com/http4s/http4s/issues/3664
      if (sys.props.get("java.specification.version").contains("1.8"))
        zoneIdIter.filterNot(_ == "GMT0")
      else
        zoneIdIter
    }.toSeq

    Arbitrary(
      for {
        instant <- Arbitrary.arbitrary[Instant] // re-use `Gen` from `Arbitrary[Instant]` here
        zoneId <- Gen.oneOf(zoneIds)
      } yield ZonedDateTime.ofInstant(instant, ZoneId.of(zoneId))
    )
  }
}
