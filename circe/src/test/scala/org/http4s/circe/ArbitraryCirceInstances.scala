/* Lifted from https://raw.githubusercontent.com/travisbrown/circe/v0.3.0/tests/shared/src/main/scala/io/circe/tests/ArbitraryInstances.scala
 * License: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.http4s.circe

import cats.data.OneAnd
import io.circe.{ Json, JsonBigDecimal, JsonDouble, JsonLong, JsonNumber, JsonObject }
import java.util.UUID
import org.http4s.TestInstances
import org.scalacheck.{ Arbitrary, Gen, Shrink }
import org.scalacheck.Arbitrary.arbitrary

trait ArbitraryCirceInstances extends TestInstances {
  private[this] def maxDepth: Int = 5
  private[this] def maxSize: Int = 20

  private[this] def genNull: Gen[Json] = Gen.const(Json.empty)
  private[this] def genBool: Gen[Json] = Arbitrary.arbBool.arbitrary.map(Json.bool)

  private[this] def genNumber: Gen[Json] = Gen.oneOf(
    Arbitrary.arbLong.arbitrary.map(Json.long),
    Arbitrary.arbDouble.arbitrary.map(Json.numberOrNull)
  )

  private[this] def genString: Gen[Json] = arbitrary[String].map(Json.string)

  private[this] def genArray(depth: Int): Gen[Json] = Gen.choose(0, maxSize).flatMap { size =>
    Gen.listOfN(
      size,
      arbitraryJsonAtDepth(depth + 1).arbitrary
    ).map(Json.array)
  }

  private[this] def genObject(depth: Int): Gen[Json] = Gen.choose(0, maxSize).flatMap { size =>
    Gen.listOfN(
      size,
      for {
        k <- arbitrary[String]
        v <- arbitraryJsonAtDepth(depth + 1).arbitrary
      } yield k -> v
    ).map(Json.obj)
  }

  private[this] def arbitraryJsonAtDepth(depth: Int): Arbitrary[Json] = {
    val genJsons = List(genNumber, genString) ++ (
      if (depth < maxDepth) List(genArray(depth), genObject(depth)) else Nil
    )

    Arbitrary(Gen.oneOf(genNull, genBool, genJsons: _*))
  }

  implicit def arbitraryJson: Arbitrary[Json] = arbitraryJsonAtDepth(0)

  implicit def arbitraryJsonObject: Arbitrary[JsonObject] =
    Arbitrary(genObject(0).map(_.asObject.get))

  implicit def arbitraryUUID: Arbitrary[UUID] = Arbitrary(Gen.uuid)

  private[this] val minNumberShrink = BigDecimal.valueOf(1L)
  private[this] val zero = BigDecimal.valueOf(0L)
  private[this] val two = BigDecimal.valueOf(2L)

  /**
   * Copied from ScalaCheck.
   */
  private[this] def interleave[T](xs: Stream[T], ys: Stream[T]): Stream[T] =
    if (xs.isEmpty) ys
    else if (ys.isEmpty) xs
    else xs.head #:: ys.head #:: interleave(xs.tail, ys.tail)

  implicit def shrinkJsonNumber: Shrink[JsonNumber] = Shrink { jn =>
    def halfs(n: BigDecimal): Stream[BigDecimal] =
      if (n < minNumberShrink) Stream.empty else n #:: halfs(n / two)

    jn.toBigDecimal match {
      case Some(n) =>
        val ns = if (n == zero) Stream.empty else {
          val hs = halfs(n / two).map(n - _)
          zero #:: interleave(hs, hs.map(h => -h))
        }

        ns.map(Json.bigDecimal(_).asInstanceOf[JsonNumber])
      case None => Stream(jn)
    }
  }

  implicit def shrinkJsonObject: Shrink[JsonObject] = Shrink(o =>
    Shrink.shrinkContainer[IndexedSeq, (String, Json)].shrink(
      o.toList.toIndexedSeq
    ).map(JsonObject.fromIndexedSeq)
  )

  implicit def shrinkJson: Shrink[Json] = Shrink(
    _.fold(
      Stream.empty,
      _ => Stream.empty,
      n => shrinkJsonNumber.shrink(n).map(Json.fromJsonNumber(_)),
      s => Shrink.shrinkString.shrink(s).map(Json.string(_)),
      a => Shrink.shrinkContainer[List, Json].shrink(a).map(Json.array(_:_*)),
      o => shrinkJsonObject.shrink(o).map(Json.fromJsonObject(_))
    )
  )

  implicit val arbitraryJsonNumber: Arbitrary[JsonNumber] = Arbitrary(
    Gen.oneOf(
      Arbitrary.arbitrary[JsonNumberString].map(jns => JsonNumber.unsafeDecimal(jns.value)),
      Arbitrary.arbitrary[BigDecimal].map(Json.bigDecimal(_)),
      Arbitrary.arbitrary[Long].map(Json.long(_)),
      Arbitrary.arbitrary[Double].map(Json.number(_))
    ).map(_.asInstanceOf[JsonNumber])
  )

  implicit def oneAndArbitrary[A, C[_]](implicit
    A: Arbitrary[A], CA: Arbitrary[C[A]]
  ): Arbitrary[OneAnd[C, A]] = Arbitrary(
    for {
      h <- A.arbitrary
      t <- CA.arbitrary
    } yield OneAnd(h, t)
  )
}

object ArbitraryCirceInstances extends ArbitraryCirceInstances

case class JsonNumberString(value: String)

object JsonNumberString {
  implicit val arbitraryJsonNumberString: Arbitrary[JsonNumberString] =
    Arbitrary(
      for {
        sign <- Gen.oneOf("", "-")
        integral <- Gen.oneOf(
          Gen.const("0"),
          for {
            nonZero <- Gen.choose(1, 9).map(_.toString)
            rest <- Gen.numStr
          } yield s"$nonZero$rest"
        )
        fractional <- Gen.oneOf(
          Gen.const(""),
          Gen.nonEmptyListOf(Gen.numChar).map(_.mkString).map("." + _)
        )
        exponent <- Gen.oneOf(
          Gen.const(""),
          for {
            e <- Gen.oneOf("e", "E")
            s <- Gen.oneOf("", "+", "-")
            n <- Gen.nonEmptyListOf(Gen.numChar).map(_.mkString)
          } yield s"$e$s$n"
        )
      } yield JsonNumberString(s"$sign$integral$fractional$exponent")
    )
}
