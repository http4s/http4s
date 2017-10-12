package org.http4s
package testing

import cats._
import cats.data.NonEmptyList
import cats.implicits.{catsSyntaxEither => _, _}
import java.nio.charset.{Charset => NioCharset}
import java.time._
import java.util.Locale
import org.http4s.headers._
import org.http4s.syntax.string._
import org.http4s.util.CaseInsensitiveString
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import scala.collection.JavaConverters._
import scala.concurrent.duration._

trait ArbitraryInstances {
  private implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  implicit val arbitraryCaseInsensitiveString: Arbitrary[CaseInsensitiveString] =
    Arbitrary(arbitrary[String].map(_.ci))
  implicit val cogenCaseInsensitiveString: Cogen[CaseInsensitiveString] =
    Cogen[String].contramap(_.value.toLowerCase(Locale.ROOT))

  implicit def arbitraryNonEmptyList[A: Arbitrary]: Arbitrary[NonEmptyList[A]] =
    Arbitrary {
      for {
        a <- arbitrary[A]
        list <- arbitrary[List[A]]
      } yield NonEmptyList(a, list)
    }

  val genTchar: Gen[Char] = oneOf {
    Seq('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~') ++
      ('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z')
  }

  val genToken: Gen[String] =
    nonEmptyListOf(genTchar).map(_.mkString)

  val genVchar: Gen[Char] =
    oneOf('\u0021' to '\u007e')

  val genFieldVchar: Gen[Char] =
    genVchar

  val genFieldContent: Gen[String] =
    for {
      head <- genFieldVchar
      tail <- containerOf[Vector, Vector[Char]](
        frequency(
          9 -> genFieldVchar.map(Vector(_)),
          1 -> (for {
            spaces <- nonEmptyContainerOf[Vector, Char](oneOf(' ', '\t'))
            fieldVchar <- genFieldVchar
          } yield spaces :+ fieldVchar)
        )
      ).map(_.flatten)
    } yield (head +: tail).mkString

  val genFieldValue: Gen[String] =
    genFieldContent

  val genStandardMethod: Gen[Method] =
    oneOf(Method.all)

  implicit val arbitraryMethod: Arbitrary[Method] = Arbitrary(
    frequency(
      10 -> genStandardMethod,
      1 -> genToken.map(Method.fromString(_).yolo)
    ))
  implicit val cogenMethod: Cogen[Method] =
    Cogen[Int].contramap(_.##)

  val genValidStatusCode =
    choose(100, 599)

  val genStandardStatus =
    oneOf(Status.registered.toSeq)

  val genCustomStatus = for {
    code <- genValidStatusCode
    reason <- arbitrary[String]
  } yield Status.fromIntAndReason(code, reason).yolo

  implicit val arbitraryStatus: Arbitrary[Status] = Arbitrary(
    frequency(
      10 -> genStandardStatus,
      1 -> genCustomStatus
    ))
  implicit val cogenStatus: Cogen[Status] =
    Cogen[Int].contramap(_.code)

  implicit val arbitraryQueryParam: Arbitrary[(String, Option[String])] =
    Arbitrary {
      frequency(
        5 -> {
          for {
            k <- arbitrary[String]
            v <- arbitrary[Option[String]]
          } yield (k, v)
        },
        2 -> const(("foo" -> Some("bar"))) // Want some repeats
      )
    }

  implicit val arbitraryQuery: Arbitrary[Query] =
    Arbitrary {
      for {
        n <- size
        vs <- containerOfN[Vector, (String, Option[String])](n % 8, arbitraryQueryParam.arbitrary)
      } yield Query(vs: _*)
    }

  implicit val arbitraryHttpVersion: Arbitrary[HttpVersion] =
    Arbitrary {
      for {
        major <- choose(0, 9)
        minor <- choose(0, 9)
      } yield HttpVersion.fromVersion(major, minor).yolo
    }

  implicit val cogenHttpVersion: Cogen[HttpVersion] =
    Cogen[(Int, Int)].contramap(v => (v.major, v.minor))

  implicit val arbitraryNioCharset: Arbitrary[NioCharset] =
    Arbitrary(oneOf(NioCharset.availableCharsets.values.asScala.toSeq))

  implicit val cogenNioCharset: Cogen[NioCharset] =
    Cogen[String].contramap(_.name)

  implicit val arbitraryCharset: Arbitrary[Charset] =
    Arbitrary { arbitrary[NioCharset].map(Charset.fromNioCharset) }

  implicit val cogenCharset: Cogen[Charset] =
    Cogen[NioCharset].contramap(_.nioCharset)

  implicit val arbitraryQValue: Arbitrary[QValue] =
    Arbitrary {
      oneOf(const(0), const(1000), choose(0, 1000))
        .map(QValue.fromThousandths(_).yolo)
    }
  implicit val cogenQValue: Cogen[QValue] =
    Cogen[Int].contramap(_.thousandths)

  implicit val arbitraryCharsetRange: Arbitrary[CharsetRange] =
    Arbitrary {
      for {
        charsetRange <- genCharsetRangeNoQuality
        q <- arbitrary[QValue]
      } yield charsetRange.withQValue(q)
    }

  implicit val cogenCharsetRange: Cogen[CharsetRange] =
    Cogen[Either[(Charset, QValue), QValue]].contramap {
      case CharsetRange.Atom(charset, qValue) =>
        Left((charset, qValue))
      case CharsetRange.`*`(qValue) =>
        Right(qValue)
    }

  implicit val arbitraryCharsetAtomRange: Arbitrary[CharsetRange.Atom] =
    Arbitrary {
      for {
        charset <- arbitrary[Charset]
        q <- arbitrary[QValue]
      } yield charset.withQuality(q)
    }

  implicit val arbitraryCharsetSplatRange: Arbitrary[CharsetRange.`*`] =
    Arbitrary { arbitrary[QValue].map(CharsetRange.`*`.withQValue(_)) }

  def genCharsetRangeNoQuality: Gen[CharsetRange] =
    frequency(
      3 -> arbitrary[Charset].map(CharsetRange.fromCharset),
      1 -> const(CharsetRange.`*`)
    )

  @deprecated("Use genCharsetRangeNoQuality. This one may cause deadlocks.", "0.15.7")
  val charsetRangesNoQuality: Gen[CharsetRange] =
    genCharsetRangeNoQuality

  implicit val arbitraryAcceptCharset: Arbitrary[`Accept-Charset`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        charsetRanges <- nonEmptyContainerOf[Set, CharsetRange](genCharsetRangeNoQuality)
          .map(_.toVector)
        qValues <- containerOfN[Vector, QValue](charsetRanges.size, arbitraryQValue.arbitrary)
        charsetRangesWithQ = charsetRanges.zip(qValues).map {
          case (range, q) => range.withQValue(q)
        }
      } yield `Accept-Charset`(charsetRangesWithQ.head, charsetRangesWithQ.tail: _*)
    }
  def genContentCodingNoQuality: Gen[ContentCoding] =
    oneOf(ContentCoding.registered.toSeq)

  implicit val arbitraryContentCoding: Arbitrary[ContentCoding] =
    Arbitrary {
      for {
        cc <- genContentCodingNoQuality
        q <- arbitrary[QValue]
      } yield cc.withQValue(q)
    }

  implicit val arbitraryAcceptEncoding: Arbitrary[`Accept-Encoding`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        contentCodings <- nonEmptyContainerOf[Set, ContentCoding](genContentCodingNoQuality)
          .map(_.toVector)
        qValues <- containerOfN[Vector, QValue](contentCodings.size, arbitraryQValue.arbitrary)
        contentCodingsWithQ = contentCodings.zip(qValues).map {
          case (coding, q) => coding.withQValue(q)
        }
      } yield `Accept-Encoding`(contentCodingsWithQ.head, contentCodingsWithQ.tail: _*)
    }

  def genLanguageTagNoQuality: Gen[LanguageTag] =
    frequency(
      3 -> (for {
        primaryTag <- genToken
        subTags <- frequency(4 -> Nil, 1 -> listOf(genToken))
      } yield LanguageTag(primaryTag, subTags = subTags)),
      1 -> const(LanguageTag.`*`)
    )

  implicit val arbitraryLanguageTag: Arbitrary[LanguageTag] =
    Arbitrary {
      for {
        lt <- genLanguageTagNoQuality
        q <- arbitrary[QValue]
      } yield lt.copy(q = q)
    }

  implicit val arbitraryAcceptLanguage: Arbitrary[`Accept-Language`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        languageTags <- nonEmptyContainerOf[Set, LanguageTag](genLanguageTagNoQuality)
          .map(_.toVector)
        qValues <- containerOfN[Vector, QValue](languageTags.size, arbitraryQValue.arbitrary)
        tagsWithQ = languageTags.zip(qValues).map { case (tag, q) => tag.copy(q = q) }
      } yield `Accept-Language`(tagsWithQ.head, tagsWithQ.tail: _*)
    }

  implicit val arbitraryUrlForm: Arbitrary[UrlForm] = Arbitrary {
    // new String("\ufffe".getBytes("UTF-16"), "UTF-16") != "\ufffe".
    // Ain't nobody got time for that.
    arbitrary[Map[String, Seq[String]]]
      .map(UrlForm.apply)
      .suchThat(!_.toString.contains('\ufffe'))
  }

  implicit val arbitraryAllow: Arbitrary[Allow] =
    Arbitrary {
      for {
        methods <- nonEmptyContainerOf[Set, Method](arbitrary[Method]).map(_.toList)
      } yield Allow(methods.head, methods.tail: _*)
    }

  implicit val arbitraryContentLength: Arbitrary[`Content-Length`] =
    Arbitrary {
      for {
        long <- arbitrary[Long] if long > 0L
      } yield `Content-Length`.unsafeFromLong(long)
    }

  implicit val arbitraryXB3TraceId: Arbitrary[`X-B3-TraceId`] =
    Arbitrary {
      for {
        long <- arbitrary[Long]
      } yield `X-B3-TraceId`(long)
    }

  implicit val arbitraryXB3SpanId: Arbitrary[`X-B3-SpanId`] =
    Arbitrary {
      for {
        long <- arbitrary[Long]
      } yield `X-B3-SpanId`(long)
    }

  implicit val arbitraryXB3ParentSpanId: Arbitrary[`X-B3-ParentSpanId`] =
    Arbitrary {
      for {
        long <- arbitrary[Long]
      } yield `X-B3-ParentSpanId`(long)
    }

  implicit val arbitraryXB3Flags: Arbitrary[`X-B3-Flags`] =
    Arbitrary {
      for {
        flags <- Gen.listOfN(
          3,
          Gen.oneOf(
            `X-B3-Flags`.Flag.Debug,
            `X-B3-Flags`.Flag.Sampled,
            `X-B3-Flags`.Flag.SamplingSet))
      } yield `X-B3-Flags`(flags.toSet)
    }

  implicit val arbitraryXB3Sampled: Arbitrary[`X-B3-Sampled`] =
    Arbitrary {
      for {
        boolean <- arbitrary[Boolean]
      } yield `X-B3-Sampled`(boolean)
    }

  val genHttpDate: Gen[HttpDate] = {
    val min = ZonedDateTime
      .of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
      .toInstant
      .toEpochMilli / 1000
    val max = ZonedDateTime
      .of(9999, 12, 31, 23, 59, 59, 0, ZoneId.of("UTC"))
      .toInstant
      .toEpochMilli / 1000
    choose[Long](min, max).map(HttpDate.unsafeFromEpochSecond)
  }

  implicit val arbitraryDateHeader: Arbitrary[headers.Date] =
    Arbitrary {
      for {
        httpDate <- genHttpDate
      } yield headers.Date(httpDate)
    }

  val genHttpExpireDate: Gen[HttpDate] = {
    // RFC 2616 says Expires should be between now and 1 year in the future, though other values are allowed
    val min = ZonedDateTime.of(LocalDateTime.now, ZoneId.of("UTC")).toInstant.toEpochMilli / 1000
    val max = ZonedDateTime
      .of(LocalDateTime.now.plusYears(1), ZoneId.of("UTC"))
      .toInstant
      .toEpochMilli / 1000
    choose[Long](min, max).map(HttpDate.unsafeFromEpochSecond)
  }

  val genFiniteDuration: Gen[FiniteDuration] =
    // Only consider positive durations
    Gen.posNum[Long].map(_.seconds)

  implicit val arbitraryExpiresHeader: Arbitrary[headers.Expires] =
    Arbitrary {
      for {
        date <- genHttpExpireDate
      } yield headers.Expires(date)
    }

  implicit val arbitraryRetryAfterHeader: Arbitrary[headers.`Retry-After`] =
    Arbitrary {
      for {
        retry <- Gen.oneOf(genHttpExpireDate.map(Left(_)), Gen.posNum[Long].map(Right(_)))
      } yield
        retry.fold(
          headers.`Retry-After`.apply,
          headers.`Retry-After`.unsafeFromLong
        )
    }

  implicit val arbitraryAgeHeader: Arbitrary[headers.Age] =
    Arbitrary {
      for {
        // age is always positive
        age <- genFiniteDuration
      } yield headers.Age.unsafeFromDuration(age)
    }

  implicit val arbitrarySTS: Arbitrary[headers.`Strict-Transport-Security`] =
    Arbitrary {
      for {
        // age is always positive
        age <- genFiniteDuration
        includeSubDomains <- Gen.oneOf(true, false)
        preload <- Gen.oneOf(true, false)
      } yield
        headers.`Strict-Transport-Security`.unsafeFromDuration(age, includeSubDomains, preload)
    }

  implicit val arbitraryRawHeader: Arbitrary[Header.Raw] =
    Arbitrary {
      for {
        token <- genToken
        value <- genFieldValue
      } yield Header.Raw(token.ci, value)
    }

  implicit val arbitraryHeader: Arbitrary[Header] =
    Arbitrary {
      oneOf(
        arbitrary[`Accept-Charset`],
        arbitrary[Allow],
        arbitrary[`Content-Length`],
        arbitrary[Date],
        arbitrary[Header.Raw]
      )
    }

  implicit val arbitraryServerSentEvent: Arbitrary[ServerSentEvent] = {
    import ServerSentEvent._
    def singleLineString: Gen[String] =
      arbitrary[String].suchThat { s =>
        !s.contains("\r") && !s.contains("\n")
      }
    Arbitrary(for {
      data <- singleLineString
      event <- frequency(
        4 -> None,
        1 -> singleLineString.map(Some.apply)
      )
      id <- frequency(
        8 -> None,
        1 -> Some(EventId.reset),
        1 -> singleLineString.suchThat(_.nonEmpty).map(id => Some(EventId(id)))
      )
      retry <- frequency(
        4 -> None,
        1 -> posNum[Long].map(Some.apply)
      )
    } yield ServerSentEvent(data, event, id, retry))
  }

  // https://tools.ietf.org/html/rfc2234#section-6
  val genHexDigit: Gen[Char] = oneOf(
    Seq('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'))

  private implicit def semigroupGen[T: Semigroup]: Semigroup[Gen[T]] = new Semigroup[Gen[T]] {
    def combine(g1: Gen[T], g2: Gen[T]): Gen[T] = for { t1 <- g1; t2 <- g2 } yield t1 |+| t2
  }

  private def timesBetween[T: Monoid](min: Int, max: Int, g: Gen[T]): Gen[T] =
    for {
      n <- choose(min, max)
      l <- listOfN(n, g).suchThat(_.length == n)
    } yield l.fold(Monoid[T].empty)(_ |+| _)

  private def times[T: Monoid](n: Int, g: Gen[T]): Gen[T] =
    listOfN(n, g).suchThat(_.length == n).map(_.reduce(_ |+| _))

  private def atMost[T: Monoid](n: Int, g: Gen[T]): Gen[T] =
    timesBetween(min = 0, max = n, g)

  private def opt[T](g: Gen[T])(implicit ev: Monoid[T]): Gen[T] =
    oneOf(g, const(ev.empty))

  // https://tools.ietf.org/html/rfc3986#appendix-A
  implicit val arbitraryIPv4: Arbitrary[Uri.IPv4] = Arbitrary {
    val num = numChar.map(_.toString)
    def range(min: Int, max: Int) = choose(min.toChar, max.toChar).map(_.toString)
    val genDecOctet = oneOf(
      num,
      range(49, 57) |+| num,
      const("1") |+| num |+| num,
      const("2") |+| range(48, 52) |+| num,
      const("25") |+| range(48, 51)
    )
    listOfN(4, genDecOctet).map(_.mkString(".")).map(Uri.IPv4.apply)
  }

  // https://tools.ietf.org/html/rfc3986#appendix-A
  implicit val arbitraryIPv6: Arbitrary[Uri.IPv6] = Arbitrary {
    val h16 = timesBetween(min = 1, max = 4, genHexDigit.map(_.toString))
    val ls32 = oneOf(h16 |+| const(":") |+| h16, arbitraryIPv4.arbitrary.map(_.address.value))
    val h16colon = h16 |+| const(":")
    val :: = const("::")

    oneOf(
      times(6, h16colon) |+| ls32,
      :: |+| times(5, h16colon) |+| ls32,
      opt(h16) |+| :: |+| times(4, h16colon) |+| ls32,
      opt(atMost(1, h16colon) |+| h16) |+| :: |+| times(3, h16colon) |+| ls32,
      opt(atMost(2, h16colon) |+| h16) |+| :: |+| times(2, h16colon) |+| ls32,
      opt(atMost(3, h16colon) |+| h16) |+| :: |+| opt(h16colon) |+| ls32,
      opt(atMost(4, h16colon) |+| h16) |+| :: |+| ls32,
      opt(atMost(5, h16colon) |+| h16) |+| :: |+| h16,
      opt(atMost(6, h16colon) |+| h16) |+| ::
    ).map(Uri.IPv6.apply)
  }

  implicit val arbitraryUriHost: Arbitrary[Uri.Host] = Arbitrary {
    val genRegName =
      listOf(oneOf(genUnreserved, genPctEncoded, genSubDelims)).map(rn => Uri.RegName(rn.mkString))
    oneOf(arbitraryIPv4.arbitrary, arbitraryIPv6.arbitrary, genRegName)
  }

  implicit val arbitraryAuthority: Arbitrary[Uri.Authority] = Arbitrary {
    for {
      userInfo <- identifier
      maybeUserInfo <- Gen.option(userInfo)
      host <- arbitraryUriHost.arbitrary
      maybePort <- Gen.option(posNum[Int].suchThat(port => port >= 0 && port <= 65536))
    } yield Uri.Authority(maybeUserInfo, host, maybePort)
  }

  val genPctEncoded: Gen[String] = const("%") |+| genHexDigit.map(_.toString) |+| genHexDigit.map(
    _.toString)
  val genUnreserved: Gen[Char] =
    oneOf(alphaChar, numChar, const('-'), const('.'), const('_'), const('~'))
  val genSubDelims: Gen[Char] = oneOf(Seq('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='))

  /** https://tools.ietf.org/html/rfc3986 */
  implicit val arbitraryUri: Arbitrary[Uri] = Arbitrary {
    val genSegmentNzNc =
      nonEmptyListOf(oneOf(genUnreserved, genPctEncoded, genSubDelims, const("@"))).map(_.mkString)
    val genPChar = oneOf(genUnreserved, genPctEncoded, genSubDelims, const(":"), const("@"))
    val genSegmentNz = nonEmptyListOf(genPChar).map(_.mkString)
    val genSegment = listOf(genPChar).map(_.mkString)
    val genPathEmpty = const("")
    val genPathAbEmpty = listOf(const("/") |+| genSegment).map(_.mkString)
    val genPathRootless = genSegmentNz |+| genPathAbEmpty
    val genPathNoScheme = genSegmentNzNc |+| genPathAbEmpty
    val genPathAbsolute = const("/") |+| opt(genPathRootless)
    val genScheme = oneOf("http", "https").map(CaseInsensitiveString.apply)
    val genPath =
      oneOf(genPathAbEmpty, genPathAbsolute, genPathNoScheme, genPathRootless, genPathEmpty)
    val genFragment: Gen[Uri.Fragment] =
      listOf(oneOf(genPChar, const("/"), const("?"))).map(_.mkString)

    for {
      scheme <- Gen.option(genScheme)
      authority <- Gen.option(arbitraryAuthority.arbitrary)
      path <- genPath
      query <- arbitraryQuery.arbitrary
      fragment <- Gen.option(genFragment)
    } yield Uri(scheme, authority, path, query, fragment)
  }
}

object ArbitraryInstances extends ArbitraryInstances {
  // This were introduced after .0 and need to be kept out of the
  // trait.  We can move them back into the trait in the next .0.

}
