package org.http4s
package testing

import cats._
import cats.data.{Chain, NonEmptyList}
import cats.laws.discipline.arbitrary.catsLawsArbitraryForChain
import cats.effect.{Effect, IO}
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.util.TestContext
import cats.implicits.{catsSyntaxEither => _, _}
import fs2.{Pure, Stream}
import java.nio.charset.{Charset => NioCharset}
import java.time._
import java.util.Locale
import org.http4s.headers._
import org.http4s.syntax.literals._
import org.http4s.syntax.string._
import org.http4s.util.CaseInsensitiveString
import org.scalacheck._
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.rng.Seed
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Try

trait ArbitraryInstances {
  private implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  implicit val http4sTestingArbitraryForCaseInsensitiveString: Arbitrary[CaseInsensitiveString] =
    Arbitrary(arbitrary[String].map(_.ci))

  implicit val http4sTestingCogenForCaseInsensitiveString: Cogen[CaseInsensitiveString] =
    Cogen[String].contramap(_.value.toLowerCase(Locale.ROOT))

  implicit def http4sTestingArbitraryForNonEmptyList[A: Arbitrary]: Arbitrary[NonEmptyList[A]] =
    Arbitrary {
      for {
        a <- arbitrary[A]
        list <- arbitrary[List[A]]
      } yield NonEmptyList(a, list)
    }

  val genChar: Gen[Char] = choose('\u0000', '\u007F')

  val ctlChar: List[Char] = ('\u007F' +: ('\u0000' to '\u001F')).toList

  val lws: List[Char] = " \t".toList

  val genCrLf: Gen[String] = const("\r\n")

  val genRightLws: Gen[String] = nonEmptyListOf(oneOf(lws)).map(_.mkString)

  val genLws: Gen[String] =
    oneOf(sequence[List[String], String](List(genCrLf, genRightLws)).map(_.mkString), genRightLws)

  val octets: List[Char] = ('\u0000' to '\u00FF').toList

  val genOctet: Gen[Char] = oneOf(octets)

  val allowedText: List[Char] = octets.diff(ctlChar)

  val genText: Gen[String] = oneOf(nonEmptyListOf(oneOf(allowedText)).map(_.mkString), genLws)

  // TODO Fix Rfc2616BasicRules.QuotedString to support the backslash character
  val allowedQDText: List[Char] = allowedText.filterNot(c => c == '"' || c == '\\')

  val genQDText: Gen[String] = nonEmptyListOf(oneOf(allowedQDText)).map(_.mkString)

  val genQuotedPair: Gen[String] =
    genChar.map(c => s"\\$c")

  val genQuotedString: Gen[String] = oneOf(genQDText, genQuotedPair).map(s => s"""\"$s\"""")

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

  implicit val http4sTestingArbitraryForMethod: Arbitrary[Method] = Arbitrary(
    frequency(
      10 -> genStandardMethod,
      1 -> genToken.map(Method.fromString(_).yolo)
    ))
  implicit val http4sTestingCogenForMethod: Cogen[Method] =
    Cogen[Int].contramap(_.##)

  val genValidStatusCode =
    choose(Status.MinCode, Status.MaxCode)

  val genStandardStatus =
    oneOf(Status.registered)

  val genCustomStatus = for {
    code <- genValidStatusCode
    reason <- arbitrary[String]
  } yield Status.fromIntAndReason(code, reason).yolo

  implicit val http4sTestingArbitraryForStatus: Arbitrary[Status] = Arbitrary(
    frequency(
      10 -> genStandardStatus,
      1 -> genCustomStatus
    ))
  implicit val http4sTestingCogenForStatus: Cogen[Status] =
    Cogen[Int].contramap(_.code)

  implicit val http4sTestingArbitraryForQueryParam: Arbitrary[(String, Option[String])] =
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

  implicit val http4sTestingArbitraryForQuery: Arbitrary[Query] =
    Arbitrary {
      for {
        n <- size
        vs <- containerOfN[Vector, (String, Option[String])](
          n % 8,
          http4sTestingArbitraryForQueryParam.arbitrary)
      } yield Query(vs: _*)
    }

  implicit val http4sTestingArbitraryForHttpVersion: Arbitrary[HttpVersion] =
    Arbitrary {
      for {
        major <- choose(0, 9)
        minor <- choose(0, 9)
      } yield HttpVersion.fromVersion(major, minor).yolo
    }

  implicit val http4sTestingCogenForHttpVersion: Cogen[HttpVersion] =
    Cogen[(Int, Int)].contramap(v => (v.major, v.minor))

  implicit val http4sTestingArbitraryForNioCharset: Arbitrary[NioCharset] =
    Arbitrary(oneOf(NioCharset.availableCharsets.values.asScala.toSeq))

  implicit val http4sTestingCogenForNioCharset: Cogen[NioCharset] =
    Cogen[String].contramap(_.name)

  implicit val http4sTestingArbitraryForCharset: Arbitrary[Charset] =
    Arbitrary { arbitrary[NioCharset].map(Charset.fromNioCharset) }

  implicit val http4sTestingCogenForCharset: Cogen[Charset] =
    Cogen[NioCharset].contramap(_.nioCharset)

  implicit val http4sTestingArbitraryForQValue: Arbitrary[QValue] =
    Arbitrary {
      oneOf(const(0), const(1000), choose(0, 1000))
        .map(QValue.fromThousandths(_).yolo)
    }
  implicit val http4sTestingCogenForQValue: Cogen[QValue] =
    Cogen[Int].contramap(_.thousandths)

  implicit val http4sTestingArbitraryForCharsetRange: Arbitrary[CharsetRange] =
    Arbitrary {
      for {
        charsetRange <- genCharsetRangeNoQuality
        q <- arbitrary[QValue]
      } yield charsetRange.withQValue(q)
    }

  implicit val http4sTestingCogenForCharsetRange: Cogen[CharsetRange] =
    Cogen[Either[(Charset, QValue), QValue]].contramap {
      case CharsetRange.Atom(charset, qValue) =>
        Left((charset, qValue))
      case CharsetRange.`*`(qValue) =>
        Right(qValue)
    }

  implicit val http4sTestingArbitraryForCharsetAtomRange: Arbitrary[CharsetRange.Atom] =
    Arbitrary {
      for {
        charset <- arbitrary[Charset]
        q <- arbitrary[QValue]
      } yield charset.withQuality(q)
    }

  implicit val http4sTestingArbitraryForCharsetSplatRange: Arbitrary[CharsetRange.`*`] =
    Arbitrary { arbitrary[QValue].map(CharsetRange.`*`.withQValue(_)) }

  def genCharsetRangeNoQuality: Gen[CharsetRange] =
    frequency(
      3 -> arbitrary[Charset].map(CharsetRange.fromCharset),
      1 -> const(CharsetRange.`*`)
    )

  @deprecated("Use genCharsetRangeNoQuality. This one may cause deadlocks.", "0.15.7")
  val charsetRangesNoQuality: Gen[CharsetRange] =
    genCharsetRangeNoQuality

  implicit val http4sTestingArbitraryForAcceptCharset: Arbitrary[`Accept-Charset`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        charsetRanges <- nonEmptyContainerOf[Set, CharsetRange](genCharsetRangeNoQuality)
          .map(_.toVector)
        qValues <- containerOfN[Vector, QValue](
          charsetRanges.size,
          http4sTestingArbitraryForQValue.arbitrary)
        charsetRangesWithQ = charsetRanges.zip(qValues).map {
          case (range, q) => range.withQValue(q)
        }
      } yield `Accept-Charset`(charsetRangesWithQ.head, charsetRangesWithQ.tail: _*)
    }

  def genContentCodingNoQuality: Gen[ContentCoding] =
    Gen.frequency(
      (10, oneOf(ContentCoding.standard.values.toSeq)),
      (2, genToken.map(ContentCoding.unsafeFromString))
    )

  implicit val http4sTrstingArbitraryForContentCoding: Arbitrary[ContentCoding] =
    Arbitrary {
      for {
        cc <- genContentCodingNoQuality
        q <- arbitrary[QValue]
      } yield cc.withQValue(q)
    }

  implicit val http4sTestingCogenForContentCoding: Cogen[ContentCoding] =
    Cogen[String].contramap(_.coding)

  // MediaRange exepects the quoted pair without quotes
  val http4sGenUnquotedPair = genQuotedPair.map { c =>
    c.substring(1, c.length - 1)
  }

  val http4sGenMediaRangeExtension: Gen[(String, String)] =
    for {
      token <- genToken
      value <- oneOf(http4sGenUnquotedPair, genQDText)
    } yield (token, value)

  val http4sGenMediaRangeExtensions: Gen[Map[String, String]] =
    Gen.listOf(http4sGenMediaRangeExtension).map(_.toMap)

  implicit val http4sArbitraryMediaType: Arbitrary[MediaType] =
    Arbitrary(oneOf(MediaType.all.values.toSeq))

  implicit val http4sTestingCogenForMediaType: Cogen[MediaType] =
    Cogen[(String, String, Map[String, String])].contramap(m =>
      (m.mainType, m.subType, m.extensions))

  val http4sGenMediaRange: Gen[MediaRange] =
    for {
      `type` <- genToken.map(_.toLowerCase)
      extensions <- http4sGenMediaRangeExtensions
    } yield new MediaRange(`type`, extensions)

  implicit val http4sTestingArbitraryForMediaRange: Arbitrary[MediaRange] =
    Arbitrary(http4sGenMediaRange)

  implicit val http4sTestingCogenForMediaRange: Cogen[MediaRange] =
    Cogen[(String, String, Map[String, String])].contramap { m =>
      val effectiveSubtype = m match {
        case mt: MediaType => mt.subType
        case _ => "*"
      }
      (m.mainType, effectiveSubtype, m.extensions)
    }

  implicit val http4sTestingArbitraryForAcceptEncoding: Arbitrary[`Accept-Encoding`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        contentCodings <- nonEmptyContainerOf[Set, ContentCoding](genContentCodingNoQuality)
          .map(_.toVector)
        qValues <- containerOfN[Vector, QValue](
          contentCodings.size,
          http4sTestingArbitraryForQValue.arbitrary)
        contentCodingsWithQ = contentCodings.zip(qValues).map {
          case (coding, q) => coding.withQValue(q)
        }
      } yield `Accept-Encoding`(contentCodingsWithQ.head, contentCodingsWithQ.tail: _*)
    }

  implicit val http4sTestingArbitraryForContentEncoding: Arbitrary[`Content-Encoding`] =
    Arbitrary {
      for {
        contentCoding <- genContentCodingNoQuality
      } yield `Content-Encoding`(contentCoding)
    }

  implicit val http4sTestingArbitraryForContentType: Arbitrary[`Content-Type`] =
    Arbitrary {
      for {
        mediaType <- arbitrary[MediaType]
        charset <- arbitrary[Charset]
      } yield `Content-Type`(mediaType, charset)
    }

  def genLanguageTagNoQuality: Gen[LanguageTag] =
    frequency(
      3 -> (for {
        primaryTag <- genToken
        subTags <- frequency(4 -> Nil, 1 -> listOf(genToken))
      } yield LanguageTag(primaryTag, subTags = subTags)),
      1 -> const(LanguageTag.`*`)
    )

  implicit val http4sTestingArbitraryForLanguageTag: Arbitrary[LanguageTag] =
    Arbitrary {
      for {
        lt <- genLanguageTagNoQuality
        q <- arbitrary[QValue]
      } yield lt.copy(q = q)
    }

  implicit val http4sTestingArbitraryForAcceptLanguage: Arbitrary[`Accept-Language`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        languageTags <- nonEmptyContainerOf[Set, LanguageTag](genLanguageTagNoQuality)
          .map(_.toVector)
        qValues <- containerOfN[Vector, QValue](
          languageTags.size,
          http4sTestingArbitraryForQValue.arbitrary)
        tagsWithQ = languageTags.zip(qValues).map { case (tag, q) => tag.copy(q = q) }
      } yield `Accept-Language`(tagsWithQ.head, tagsWithQ.tail: _*)
    }

  implicit val http4sTestingArbitraryForUrlForm: Arbitrary[UrlForm] = Arbitrary {
    // new String("\ufffe".getBytes("UTF-16"), "UTF-16") != "\ufffe".
    // Ain't nobody got time for that.
    arbitrary[Map[String, Chain[String]]]
      .map(UrlForm.apply)
      .suchThat(!_.toString.contains('\ufffe'))
  }

  implicit val http4sTestingArbitraryForAllow: Arbitrary[Allow] =
    Arbitrary {
      for {
        methods <- nonEmptyContainerOf[Set, Method](arbitrary[Method]).map(_.toList)
      } yield Allow(methods.head, methods.tail: _*)
    }

  implicit val http4sTestingArbitraryForContentLength: Arbitrary[`Content-Length`] =
    Arbitrary {
      for {
        long <- Gen.chooseNum(0L, Long.MaxValue)
      } yield `Content-Length`.unsafeFromLong(long)
    }

  implicit val http4sTestingArbitraryForXB3TraceId: Arbitrary[`X-B3-TraceId`] =
    Arbitrary {
      for {
        msb <- arbitrary[Long]
        lsb <- Gen.option(arbitrary[Long])
      } yield `X-B3-TraceId`(msb, lsb)
    }

  implicit val http4sTestingArbitraryForXB3SpanId: Arbitrary[`X-B3-SpanId`] =
    Arbitrary {
      for {
        long <- arbitrary[Long]
      } yield `X-B3-SpanId`(long)
    }

  implicit val http4sTestingArbitraryForXB3ParentSpanId: Arbitrary[`X-B3-ParentSpanId`] =
    Arbitrary {
      for {
        long <- arbitrary[Long]
      } yield `X-B3-ParentSpanId`(long)
    }

  implicit val http4sTestingArbitraryForXB3Flags: Arbitrary[`X-B3-Flags`] =
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

  implicit val http4sTestingArbitraryForXB3Sampled: Arbitrary[`X-B3-Sampled`] =
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

  implicit val http4sTestingArbitraryForDateHeader: Arbitrary[headers.Date] =
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

  implicit val http4sTestingArbitraryForExpiresHeader: Arbitrary[headers.Expires] =
    Arbitrary {
      for {
        date <- genHttpExpireDate
      } yield headers.Expires(date)
    }

  val http4sGenMediaRangeAndQValue: Gen[MediaRangeAndQValue] =
    for {
      mediaRange <- http4sGenMediaRange
      qValue <- arbitrary[QValue]
    } yield MediaRangeAndQValue(mediaRange, qValue)

  implicit val http4sTestingArbitraryForAcceptHeader: Arbitrary[headers.Accept] =
    Arbitrary {
      for {
        values <- nonEmptyListOf(http4sGenMediaRangeAndQValue)
      } yield headers.Accept(NonEmptyList.of(values.head, values.tail: _*))
    }

  implicit val http4sTestingArbitraryForRetryAfterHeader: Arbitrary[headers.`Retry-After`] =
    Arbitrary {
      for {
        retry <- Gen.oneOf(genHttpExpireDate.map(Left(_)), Gen.posNum[Long].map(Right(_)))
      } yield
        retry.fold(
          headers.`Retry-After`.apply,
          headers.`Retry-After`.unsafeFromLong
        )
    }

  implicit val http4sTestingArbitraryForAgeHeader: Arbitrary[headers.Age] =
    Arbitrary {
      for {
        // age is always positive
        age <- genFiniteDuration
      } yield headers.Age.unsafeFromDuration(age)
    }

  implicit val http4sTestingArbitraryForSTS: Arbitrary[headers.`Strict-Transport-Security`] =
    Arbitrary {
      for {
        // age is always positive
        age <- genFiniteDuration
        includeSubDomains <- Gen.oneOf(true, false)
        preload <- Gen.oneOf(true, false)
      } yield
        headers.`Strict-Transport-Security`.unsafeFromDuration(age, includeSubDomains, preload)
    }

  implicit val http4sTestingArbitraryForTransferEncoding: Arbitrary[`Transfer-Encoding`] =
    Arbitrary {
      for {
        codings <- arbitrary[NonEmptyList[TransferCoding]]
      } yield `Transfer-Encoding`(codings)
    }

  implicit val http4sTestingArbitraryForRawHeader: Arbitrary[Header.Raw] =
    Arbitrary {
      for {
        token <- genToken
        value <- genFieldValue
      } yield Header.Raw(token.ci, value)
    }

  implicit val http4sTestingArbitraryForHeader: Arbitrary[Header] =
    Arbitrary {
      oneOf(
        arbitrary[`Accept-Charset`],
        arbitrary[Allow],
        arbitrary[`Content-Length`],
        arbitrary[Date],
        arbitrary[Header.Raw]
      )
    }

  implicit val http4sTestingArbitraryForHeaders: Arbitrary[Headers] =
    Arbitrary(listOf(arbitrary[Header]).map(Headers(_: _*)))

  implicit val http4sTestingArbitraryForServerSentEvent: Arbitrary[ServerSentEvent] = {
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

  private implicit def http4sTestingSemigroupForGen[T: Semigroup]: Semigroup[Gen[T]] =
    new Semigroup[Gen[T]] {
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
  implicit val http4sTestingArbitraryForIPv4: Arbitrary[Uri.IPv4] = Arbitrary {
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
  implicit val http4sTestingArbitraryForIPv6: Arbitrary[Uri.IPv6] = Arbitrary {
    val h16 = timesBetween(min = 1, max = 4, genHexDigit.map(_.toString))
    val ls32 = oneOf(
      h16 |+| const(":") |+| h16,
      http4sTestingArbitraryForIPv4.arbitrary.map(_.address.value))
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

  implicit val http4sTestingArbitraryForUriHost: Arbitrary[Uri.Host] = Arbitrary {
    val genRegName =
      listOf(oneOf(genUnreserved, genPctEncoded, genSubDelims)).map(rn => Uri.RegName(rn.mkString))
    oneOf(
      http4sTestingArbitraryForIPv4.arbitrary,
      http4sTestingArbitraryForIPv6.arbitrary,
      genRegName)
  }

  implicit val http4sTestingArbitraryForAuthority: Arbitrary[Uri.Authority] = Arbitrary {
    for {
      userInfo <- identifier
      maybeUserInfo <- Gen.option(userInfo)
      host <- http4sTestingArbitraryForUriHost.arbitrary
      maybePort <- Gen.option(posNum[Int].suchThat(port => port >= 0 && port <= 65536))
    } yield Uri.Authority(maybeUserInfo, host, maybePort)
  }

  val genPctEncoded: Gen[String] = const("%") |+| genHexDigit.map(_.toString) |+| genHexDigit.map(
    _.toString)
  val genUnreserved: Gen[Char] =
    oneOf(alphaChar, numChar, const('-'), const('.'), const('_'), const('~'))
  val genSubDelims: Gen[Char] = oneOf(Seq('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='))

  implicit val http4sTestingArbitraryForScheme: Arbitrary[Uri.Scheme] = Arbitrary {
    frequency(
      5 -> Uri.Scheme.http,
      5 -> Uri.Scheme.https,
      1 -> scheme"HTTP",
      1 -> scheme"HTTPS",
      3 -> (for {
        head <- alphaChar
        tail <- listOf(
          frequency(
            36 -> alphaNumChar,
            1 -> const('+'),
            1 -> const('-'),
            1 -> const('.')
          )
        )
      } yield HttpCodec[Uri.Scheme].parseOrThrow(tail.mkString(head.toString, "", "")))
    )
  }

  implicit val http4sTestingCogenForScheme: Cogen[Uri.Scheme] =
    Cogen[String].contramap(_.value.toLowerCase(Locale.ROOT))

  implicit val http4sTestingArbitraryForTransferCoding: Arbitrary[TransferCoding] = Arbitrary {
    Gen.oneOf(
      TransferCoding.chunked,
      TransferCoding.compress,
      TransferCoding.deflate,
      TransferCoding.gzip,
      TransferCoding.identity)
  }

  implicit val http4sTestingCogenForTransferCoding: Cogen[TransferCoding] =
    Cogen[String].contramap(_.coding.toLowerCase(Locale.ROOT))

  /** https://tools.ietf.org/html/rfc3986 */
  implicit val http4sTestingArbitraryForUri: Arbitrary[Uri] = Arbitrary {
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
    val genScheme = oneOf(Uri.Scheme.http, Uri.Scheme.https)
    val genPath =
      oneOf(genPathAbEmpty, genPathAbsolute, genPathNoScheme, genPathRootless, genPathEmpty)
    val genFragment: Gen[Uri.Fragment] =
      listOf(oneOf(genPChar, const("/"), const("?"))).map(_.mkString)

    for {
      scheme <- Gen.option(genScheme)
      authority <- Gen.option(http4sTestingArbitraryForAuthority.arbitrary)
      path <- genPath
      query <- http4sTestingArbitraryForQuery.arbitrary
      fragment <- Gen.option(genFragment)
    } yield Uri(scheme, authority, path, query, fragment)
  }

  implicit val http4sTestingArbitraryForLink: Arbitrary[Link] = Arbitrary {
    for {
      uri <- http4sTestingArbitraryForUri.arbitrary
    } yield Link(uri)
  }

  implicit val http4sTestingCogenForUri: Cogen[Uri] =
    Cogen[String].contramap(_.renderString)

  // TODO This could be a lot more interesting.
  // See https://github.com/functional-streams-for-scala/fs2/blob/fd3d0428de1e71c10d1578f2893ee53336264ffe/core/shared/src/test/scala/fs2/TestUtil.scala#L42
  implicit def http4sTestingGenForPureByteStream[F[_]]: Gen[Stream[Pure, Byte]] = Gen.sized {
    size =>
      Gen.listOfN(size, arbitrary[Byte]).map(Stream.emits)
  }

  // Borrowed from cats-effect tests for the time being
  def cogenFuture[A](implicit ec: TestContext, cg: Cogen[Try[A]]): Cogen[Future[A]] =
    Cogen { (seed: Seed, fa: Future[A]) =>
      ec.tick()

      fa.value match {
        case None => seed
        case Some(ta) => cg.perturb(seed, ta)
      }
    }

  implicit def http4sTestingCogenForEntityBody[F[_]](implicit F: Effect[F]): Cogen[EntityBody[F]] =
    catsEffectLawsCogenForIO[Vector[Byte]].contramap { stream =>
      var bytes: Vector[Byte] = null
      val readBytes = IO(bytes)
      F.runAsync(stream.compile.toVector) {
          case Right(bs) => IO { bytes = bs }
          case Left(t) => IO.raiseError(t)
        }
        .toIO *> readBytes
    }

  implicit def http4sTestingArbitraryForEntity[F[_]]: Arbitrary[Entity[F]] =
    Arbitrary(Gen.sized { size =>
      for {
        body <- http4sTestingGenForPureByteStream
        length <- Gen.oneOf(Some(size.toLong), None)
      } yield Entity(body.covary[F], length)
    })

  implicit def http4sTestingCogenForEntity[F[_]](implicit F: Effect[F]): Cogen[Entity[F]] =
    Cogen[(EntityBody[F], Option[Long])].contramap(entity => (entity.body, entity.length))

  implicit def http4sTestingArbitraryForEntityEncoder[F[_], A](
      implicit CA: Cogen[A]): Arbitrary[EntityEncoder[F, A]] =
    Arbitrary(for {
      f <- arbitrary[A => Entity[F]]
      hs <- arbitrary[Headers]
    } yield EntityEncoder.encodeBy(hs)(f))

  implicit def http4sTestingArbitraryForEntityDecoder[F[_], A](
      implicit
      F: Effect[F],
      g: Arbitrary[DecodeResult[F, A]]) =
    Arbitrary(
      for {
        f <- arbitrary[(Message[F], Boolean) => DecodeResult[F, A]]
        mrs <- arbitrary[Set[MediaRange]]
      } yield
        new EntityDecoder[F, A] {
          def decode(msg: Message[F], strict: Boolean): DecodeResult[F, A] = f(msg, strict)
          def consumes = mrs
        })

  implicit def http4sTestingCogenForMessage[F[_]](implicit F: Effect[F]): Cogen[Message[F]] =
    Cogen[(Headers, EntityBody[F])].contramap(m => (m.headers, m.body))

  implicit def http4sTestingCogenForHeaders: Cogen[Headers] =
    Cogen[List[Header]].contramap(_.toList)

  implicit def http4sTestingCogenForHeader: Cogen[Header] =
    Cogen[(CaseInsensitiveString, String)].contramap(h => (h.name, h.value))

  implicit def http4sTestingArbitraryForDecodeFailure: Arbitrary[DecodeFailure] =
    Arbitrary(
      oneOf(
        http4sTestingGenForMalformedMessageBodyFailure,
        http4sTestingGenForInvalidMessageBodyFailure,
        http4sTestingGenForMediaTypeMissing,
        http4sTestingGenForMediaTypeMismatch
      ))

  implicit val http4sTestingGenForMalformedMessageBodyFailure: Gen[MalformedMessageBodyFailure] =
    for {
      details <- arbitrary[String]
      cause <- arbitrary[Option[Throwable]]
    } yield MalformedMessageBodyFailure(details, cause)

  implicit val http4sTestingGenForInvalidMessageBodyFailure: Gen[InvalidMessageBodyFailure] =
    for {
      details <- arbitrary[String]
      cause <- arbitrary[Option[Throwable]]
    } yield InvalidMessageBodyFailure(details, cause)

  implicit val http4sTestingGenForMediaTypeMissing: Gen[MediaTypeMissing] =
    arbitrary[Set[MediaRange]].map(MediaTypeMissing(_))

  implicit val http4sTestingGenForMediaTypeMismatch: Gen[MediaTypeMismatch] =
    for {
      messageType <- arbitrary[MediaType]
      expected <- arbitrary[Set[MediaRange]]
    } yield MediaTypeMismatch(messageType, expected)

  // These instances are private because they're half-baked and I don't want to encourage external use yet.
  private[http4s] implicit val http4sTestingCogenForDecodeFailure: Cogen[DecodeFailure] =
    Cogen { (seed: Seed, df: DecodeFailure) =>
      df match {
        case MalformedMessageBodyFailure(d, t) =>
          Cogen[(String, Option[Throwable])].perturb(seed, (d, t))
        case InvalidMessageBodyFailure(d, t) =>
          Cogen[(String, Option[Throwable])].perturb(seed, (d, t))
        case MediaTypeMissing(mrs) => Cogen[Set[MediaRange]].perturb(seed, mrs)
        case MediaTypeMismatch(mt, e) => Cogen[(MediaType, Set[MediaRange])].perturb(seed, (mt, e))
        // TODO What if it's not one of these?
      }
    }

  private[http4s] implicit def http4sTestingArbitraryForMessage[F[_]]: Arbitrary[Message[F]] =
    // TODO this is bad because the underlying generators are bad
    Arbitrary(Gen.oneOf(arbitrary[Request[F]], arbitrary[Response[F]]))

  private[http4s] implicit def http4sTestingArbitraryForRequest[F[_]]: Arbitrary[Request[F]] =
    Arbitrary {
      // TODO some methods don't take bodies
      // TODO some arbitrary headers are mutually exclusive
      // TODO some headers need to be reflective of the body
      // TODO some things are illegal per HTTP version
      for {
        method <- arbitrary[Method]
        uri <- arbitrary[Uri]
        httpVersion <- arbitrary[HttpVersion]
        headers <- arbitrary[Headers]
        body <- http4sTestingGenForPureByteStream
      } yield
        try { Request(method, uri, httpVersion, headers, body) } catch {
          case t: Throwable => t.printStackTrace(); throw t
        }
    }

  private[http4s] implicit def http4sTestingArbitraryForResponse[F[_]]: Arbitrary[Response[F]] =
    Arbitrary {
      // TODO some statuses don't take bodies
      // TODO some arbitrary headers are mutually exclusive
      // TODO some headers need to be reflective of the body
      // TODO some things are illegal per HTTP version
      for {
        status <- arbitrary[Status]
        httpVersion <- arbitrary[HttpVersion]
        headers <- arbitrary[Headers]
        body <- http4sTestingGenForPureByteStream
      } yield Response(status, httpVersion, headers, body)
    }
}

object ArbitraryInstances extends ArbitraryInstances
