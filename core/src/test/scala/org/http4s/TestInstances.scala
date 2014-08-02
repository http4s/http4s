package org.http4s

import java.lang.Enum

import org.http4s.Header.`Accept-Charset`

import scala.collection.JavaConverters._
import java.nio.charset.{Charset => NioCharset}
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.http4s.scalacheck.ScalazArbitrary._

import scalaz.NonEmptyList

trait TestInstances {
  implicit val aribtraryNioCharset: Arbitrary[NioCharset] =
    Arbitrary(oneOf(NioCharset.availableCharsets.values.asScala.toSeq))

  implicit val arbitraryCharset: Arbitrary[Charset] =
    Arbitrary { arbitrary[NioCharset].map(Charset.fromNioCharset) }

  implicit val qualities: Arbitrary[QValue] =
    Arbitrary { Gen.oneOf(oneOf(0, 1000), choose(0, 1000)).map(QValue.fromThousandths) }

  implicit val arbitraryCharsetRange: Arbitrary[CharsetRange] =
    Arbitrary { frequency((10, arbitrary[CharsetRange.Atom]), (1, arbitrary[CharsetRange.`*`])) }
  implicit val arbitraryCharsetAtomRange: Arbitrary[CharsetRange.Atom] =
    Arbitrary { for {
      charset <- arbitrary[Charset]
      q <- arbitrary[QValue]
    } yield charset.withQuality(q) }
  implicit val arbitraryCharsetSplatRange: Arbitrary[CharsetRange.`*`] =
    Arbitrary { arbitrary[QValue].map(CharsetRange.`*`.withQValue(_)) }

  implicit val arbitraryAcceptCharset: Arbitrary[`Accept-Charset`] =
    Arbitrary { arbitrary[NonEmptyList[CharsetRange.`*`]].map(`Accept-Charset`(_)) }
}


