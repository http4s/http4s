package org.http4s

import org.http4s.headers.`Accept-Charset`

import java.nio.charset.{Charset => NioCharset}

import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.JavaConverters._
import scala.collection.immutable.BitSet
import scalaz.NonEmptyList
import scalaz.scalacheck.ScalazArbitrary._

trait TestInstances {
  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  val tchars: Gen[Char] = Gen.oneOf {
    Seq('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~') ++
      ('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z')
  }
  val tokens: Gen[String] = nonEmptyListOf(tchars).map(_.mkString)

  val standardMethods: Gen[Method] = Gen.oneOf(Method.registered.toSeq)
  implicit val arbitraryMethod: Arbitrary[Method] = Arbitrary(frequency(
    10 -> standardMethods,
    1 -> tokens.map(Method.fromString(_).yolo)
  ))

  val validStatusCodes = Gen.choose(100, 599)
  val standardStatuses = Gen.oneOf(Status.registered.toSeq)
  val customStatuses = for {
    code <- validStatusCodes
    reason <- arbString.arbitrary
  } yield Status.fromIntAndReason(code, reason).yolo
  implicit val arbitraryStatuses: Arbitrary[Status] = Arbitrary(frequency(
    10 -> standardStatuses,
    1 -> customStatuses
  ))


  val validQueryChars = {
    val mark    =  Seq('-', '_', '.', '!', '~', '*', '\'', '(', ')')
    val alphanum = ('0' to '9') ++ ('a' to 'z') ++ ('A' to 'Z')
    val reserved    = Seq('/', '?', ':', '@', '$', ',')
    val unreserved  =  alphanum ++ mark

    //val droppsed = Seq(';', '&', '=', '+') // Not valid in a query key or value, must be % encoded
    (reserved ++ unreserved).toVector
  }

  val genQueryString: Gen[String] =
    Gen.sized(length => containerOfN[Seq, Char](length, oneOf(validQueryChars)).map(_.mkString))

  implicit val arbitraryQueryParam: Arbitrary[(String, Option[String])] =
    Arbitrary { frequency(
      5 -> { for {
                k <- genQueryString
                v <- option(genQueryString)
              } yield (k, v)
           },
      2 -> const(("foo" -> Some("bar")))  // Want some repeats
    ) }

  implicit val arbitraryQuery: Arbitrary[Query] =
    Arbitrary { for {
      n <- Gen.size
      vs <- containerOfN[Vector, (String, Option[String])](n % 8, arbitraryQueryParam.arbitrary)
    } yield Query(vs:_*) }

  implicit val arbitraryHttpVersion: Arbitrary[HttpVersion] =
    Arbitrary { for {
      major <- choose(0, 9)
      minor <- choose(0, 9)
    } yield HttpVersion.fromVersion(major, minor).yolo }

  implicit val arbitraryNioCharset: Arbitrary[NioCharset] =
    Arbitrary(oneOf(NioCharset.availableCharsets.values.asScala.toSeq))

  implicit val arbitraryCharset: Arbitrary[Charset] =
    Arbitrary { arbitrary[NioCharset].map(Charset.fromNioCharset) }

  implicit val qValues: Arbitrary[QValue] =
    Arbitrary { Gen.oneOf(const(0), const(1000), choose(0, 1000)).map(QValue.fromThousandths(_).yolo) }

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

  implicit val urlFormArb: Arbitrary[UrlForm] = Arbitrary {
    // new String("\ufffe".getBytes("UTF-16"), "UTF-16") != "\ufffe".
    // Ain't nobody got time for that.
    arbitrary[Map[String, Seq[String]]].map(UrlForm.apply)
      .suchThat(!_.toString.contains('\ufffe'))
  }
 
  implicit val bitSetArb: Arbitrary[BitSet] = Arbitrary(
    Arbitrary.arbitrary[Set[Char]].map(_.map(_.toInt)).map(set => BitSet(set.toSeq: _*))
  )
}


