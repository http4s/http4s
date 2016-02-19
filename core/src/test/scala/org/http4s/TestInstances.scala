package org.http4s

import java.time.{ZonedDateTime, ZoneId, Instant}
import java.time.temporal.ChronoUnit

import org.http4s.headers.{Allow, Date, `Content-Length`, `Accept-Charset`}
import org.http4s.util.string._

import java.nio.charset.{Charset => NioCharset}

import org.scalacheck.Arbitrary.{arbChar => _, arbString => _, _}
import org.scalacheck.Gen._
import org.scalacheck.util.Pretty
import org.scalacheck.{Shrink, Arbitrary, Gen}

import scala.collection.JavaConverters._
import scala.collection.immutable.BitSet

trait TestInstances {
  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  lazy val tchars: Gen[Char] = oneOf {
    Seq('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~') ++
      ('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z')
  }
  lazy val tokens: Gen[String] =
    nonEmptyListOf(tchars).map(_.mkString)

  lazy val fieldValues: Gen[String] =
    fieldContents

  lazy val fieldContents: Gen[String] =
    for {
      head <- fieldVchars
      tail <- containerOf[Vector, Vector[Char]](
        frequency(
          9 -> fieldVchars.map(Vector(_)),
          1 -> (for {
            spaces <- nonEmptyContainerOf[Vector, Char](oneOf(' ', '\t'))
            fieldVchar <- fieldVchars
          } yield spaces :+ fieldVchar)
        )
      ).map(_.flatten)
    } yield (head +: tail).mkString

  lazy val fieldVchars: Gen[Char] =
    vchars

  lazy val vchars: Gen[Char] =
    oneOf('\u0021' to '\u007e')

  lazy val standardMethods: Gen[Method] =
    oneOf(Method.registered.toSeq)

  implicit lazy val arbitraryMethod: Arbitrary[Method] = Arbitrary(frequency(
    10 -> standardMethods,
    1 -> tokens.map(Method.fromString(_).yolo)
  ))

  lazy val validStatusCodes =
    choose(100, 599)

  lazy val standardStatuses =
    oneOf(Status.registered.toSeq)

  lazy val customStatuses = for {
    code <- validStatusCodes
    reason <- safeString.arbitrary
  } yield Status.fromIntAndReason(code, reason).yolo
  implicit lazy val arbitraryStatus: Arbitrary[Status] = Arbitrary(frequency(
    10 -> standardStatuses,
    1 -> customStatuses
  ))

  ////////////////////////
  // until https://github.com/rickynils/scalacheck/pull/222 is incorporated
  implicit lazy val safeChar: Arbitrary[Char] = Arbitrary {
    // exclude 0xFFFE due to this bug: http://bit.ly/1QryQZy
    val validRangesInclusive = List[(Char, Char)](
      (0x0000, 0xD7FF),
      (0xE000, 0xFFFD),
      (0xFFFF, 0xFFFF)
    )

    Gen.frequency(validRangesInclusive.map {
      case (first, last) => (last + 1 - first, Gen.choose[Char](first, last))
    }: _*)
  }

  implicit lazy val safeString: Arbitrary[String] = Arbitrary(arbitrary[List[Char]] map (_.mkString))
  ////////////////////////

  implicit lazy val arbitraryQueryParam: Arbitrary[(String, Option[String])] =
    Arbitrary { frequency(
      5 -> { for {
                k <- arbitrary[String]
                v <- arbitrary[Option[String]]
              } yield (k, v)
           },
      2 -> const(("foo" -> Some("bar")))  // Want some repeats
    ) }

  implicit lazy val arbitraryFormQuery: Arbitrary[FormQuery] =
    Arbitrary { for {
      n <- size
      vs <- containerOfN[Vector, (String, Option[String])](n % 8, arbitraryQueryParam.arbitrary)
    } yield FormQuery(vs:_*) }

  implicit lazy val arbitraryHttpVersion: Arbitrary[HttpVersion] =
    Arbitrary { for {
      major <- choose(0, 9)
      minor <- choose(0, 9)
    } yield HttpVersion.fromVersion(major, minor).yolo }

  implicit lazy val arbitraryNioCharset: Arbitrary[NioCharset] =
    Arbitrary(oneOf(NioCharset.availableCharsets.values.asScala.toSeq))

  implicit lazy val arbitraryCharset: Arbitrary[Charset] =
    Arbitrary { arbitrary[NioCharset].map(Charset.fromNioCharset) }

  implicit lazy val arbitraryQValue: Arbitrary[QValue] =
    Arbitrary { oneOf(const(0), const(1000), choose(0, 1000)).map(QValue.fromThousandths(_).yolo) }

  implicit lazy val arbitraryCharsetRange: Arbitrary[CharsetRange] =
    Arbitrary { for {
      charsetRange <- charsetRangesNoQuality
      q <- arbitrary[QValue]
    } yield charsetRange.withQValue(q) }

  implicit lazy val arbitraryCharsetAtomRange: Arbitrary[CharsetRange.Atom] =
    Arbitrary { for {
      charset <- arbitrary[Charset]
      q <- arbitrary[QValue]
    } yield charset.withQuality(q) }

  implicit lazy val arbitraryCharsetSplatRange: Arbitrary[CharsetRange.`*`] =
    Arbitrary { arbitrary[QValue].map(CharsetRange.`*`.withQValue(_)) }

  lazy val charsetRangesNoQuality: Gen[CharsetRange] =
    frequency(
      3 -> arbitrary[Charset].map(CharsetRange.fromCharset),
      1 -> const(CharsetRange.`*`)
    )

  implicit lazy val arbitraryAcceptCharset: Arbitrary[`Accept-Charset`] =
    Arbitrary { for {
      // make a set first so we don't have contradictory q-values
      charsetRanges <- nonEmptyContainerOf[Set, CharsetRange](charsetRangesNoQuality).map(_.toVector)
      qValues <- containerOfN[Vector, QValue](charsetRanges.size, arbitraryQValue.arbitrary)
      charsetRangesWithQ = charsetRanges.zip(qValues).map { case (range, q) => range.withQValue(q) }
    } yield `Accept-Charset`(charsetRangesWithQ.head, charsetRangesWithQ.tail:_*) }

  implicit lazy val arbitraryUrlForm: Arbitrary[UrlForm] = Arbitrary {
    arbitrary[Map[String, Seq[String]]].map(UrlForm.apply)
  }

  implicit lazy val arbitraryBitSet: Arbitrary[BitSet] = Arbitrary(
    Arbitrary.arbitrary[Set[Char]].map(_.map(_.toInt)).map(set => BitSet(set.toSeq: _*))
  )

  // this doesn't seem to do anything, or do enough
  implicit val urlFormShrink: Shrink[UrlForm] = Shrink.xmap(UrlForm.apply(_: Map[String, Seq[String]]), _.values)

  implicit def optionPretty[A](implicit A: A => Pretty): Option[A] => Pretty = {
    case None => Pretty { p => "None" }
    case Some(a) => Pretty { p => s"Some(${A(a)(p)})" }
  }

  def containerTraversablePretty[C[_], A](constructor: String)(implicit A: A => Pretty, t: C[A] => Traversable[A]): C[A] => Pretty =
    c => Pretty { p => s"$constructor(${c.map(A(_)(p)).mkString(", ")})" }

  def container2TraversablePretty[C[_,_], A, B](constructor: String)
                                               (implicit A: A => Pretty, B: B => Pretty, t: C[A,B] => Traversable[(A,B)]): C[A,B] => Pretty =
    c => Pretty { p => s"$constructor(${c.map { case (a, b) => s"${A(a)(p)} -> ${B(b)(p)}"}.mkString(", ")})" }


  implicit def queryPretty: Query => Pretty = {
    case FormQuery(pairs) => Pretty { p => "FormQuery(" + containerTraversablePretty[Vector, (String, Option[String])]("Vector").apply(pairs)(p) + ")" }
    case PlainQuery(plain) => Pretty { p => "PlainQuery( " + Pretty.prettyString(plain)(p) + " )" }
    case EmptyQuery => Pretty { p => "EmptyQuery" }
  }

  implicit def formMultiMapPretty: Map[String, Seq[String]] => Pretty =
    container2TraversablePretty[Map, String, Seq[String]]("Map")(implicitly, containerTraversablePretty[Seq,String]("Seq"), implicitly)

  implicit lazy val arbitararyAllow: Arbitrary[Allow] =
    Arbitrary { for {
      methods <- nonEmptyContainerOf[Set, Method](arbitrary[Method]).map(_.toList)
    } yield Allow(methods.head, methods.tail:_*) }

  implicit lazy val arbitararyContentLength: Arbitrary[`Content-Length`] =
    Arbitrary { for {
      long <- arbitrary[Long] if long > 0L
    } yield `Content-Length`(long) }

  lazy val httpDateInstant: Gen[Instant] = {
    // RFC 5322 says 1900 is the minimum year
    val min = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant.toEpochMilli
    val max = ZonedDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneId.of("UTC")).toInstant.toEpochMilli
    choose[Long](min, max).map(Instant.ofEpochMilli(_).truncatedTo(ChronoUnit.SECONDS))
  }

  implicit lazy val arbitraryDateHeader: Arbitrary[headers.Date] =
    Arbitrary { for {
      instant <- httpDateInstant
    } yield headers.Date(instant) }

  implicit lazy val arbitraryRawHeader: Arbitrary[Header.Raw] =
    Arbitrary {
      for {
        token <- tokens
        value <- fieldValues
      } yield Header.Raw(token.ci, value)
    }

  implicit lazy val arbitraryHeader: Arbitrary[Header] =
    Arbitrary {
      oneOf(
        arbitrary[`Accept-Charset`],
        arbitrary[Allow],
        arbitrary[`Content-Length`],
        arbitrary[Date],
        arbitrary[Header.Raw]
      )
    }
}


