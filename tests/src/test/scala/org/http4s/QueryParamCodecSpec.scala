package org.http4s

import scalaz.{ Equal, Validation, NonEmptyList }
import scalaz.syntax.id._
import scalaz.scalacheck.ScalazProperties._
import scalaz.scalacheck.ScalazArbitrary._

import org.scalacheck.{ Arbitrary, Cogen }
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._

class QueryParamCodecSpec extends Http4sSpec with QueryParamCodecInstances {

  checkAll("Boolean QueryParamCodec", QueryParamCodecLaws[Boolean])
  checkAll("Double QueryParamCodec" , QueryParamCodecLaws[Double])
  checkAll("Float QueryParamCodec"  , QueryParamCodecLaws[Float])
  checkAll("Short QueryParamCodec"  , QueryParamCodecLaws[Short])
  checkAll("Int QueryParamCodec"    , QueryParamCodecLaws[Int])
  checkAll("Long QueryParamCodec"   , QueryParamCodecLaws[Long])
  checkAll("String QueryParamCodec" , QueryParamCodecLaws[String])

  // Law checks for instances.
  checkAll("Functor[QueryParamDecoder]", functor.laws[QueryParamDecoder])
  checkAll("PlusEmpty[QueryParamDecoder]", plusEmpty.laws[QueryParamDecoder])
  checkAll("Contravariant[QueryParamEncoder]", contravariant.laws[QueryParamEncoder])

  // The PlusEmpty check above validates fail() but we need an explicit test for success().
  "success(a) always succeeds" >> forAll { (n: Int, qpv: QueryParameterValue) =>
    QueryParamDecoder.success(n).decode(qpv) must_== Validation.success(n)
  }

}

trait QueryParamCodecInstances { this: Http4sSpec =>

  // We will assume for the purposes of testing that QueryParamDecoders are equal if they
  // produce the same result for a bunch of arbitrary strings.
  implicit def EqQueryParamDecoder[A: Equal]: Equal[QueryParamDecoder[A]] = {
    val vnp = Equal[Validation[NonEmptyList[ParseFailure], A]]
    Equal.equal { (x, y) =>
      val ss = List.fill(50)(arbitrary[String].sample).flatten.map(QueryParameterValue.apply)
      ss.forall(s => vnp.equal(x.decode(s), y.decode(s)))
    }
  }

  // We will assume for the purposes of testing that QueryParamEncoders are equal if they
  // produce the same result for a bunch of arbitrary inputs.
  implicit def EqQueryParamEncoder[A: Arbitrary]: Equal[QueryParamEncoder[A]] = {
    Equal.equal { (x, y) =>
      val as = List.fill(20)(arbitrary[A].sample).flatten
      as.forall(a => x.encode(a) === y.encode(a))
    }
  }

  implicit def ArbQueryParamDecoder[A: Arbitrary]: Arbitrary[QueryParamDecoder[A]] =
    Arbitrary(arbitrary[String => A].map(QueryParamDecoder[String].map))

  implicit def ArbQueryParamEncoder[A: Cogen]: Arbitrary[QueryParamEncoder[A]] =
    Arbitrary(arbitrary[A => String].map(QueryParamEncoder[String].contramap))

  implicit val ArbQueryParameterValue: Arbitrary[QueryParameterValue] =
    Arbitrary(arbitrary[String].map(QueryParameterValue))

}
