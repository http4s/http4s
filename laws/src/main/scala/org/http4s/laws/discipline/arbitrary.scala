/*
 * Copyright 2019 http4s.org
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
package laws
package discipline

import cats._
import cats.data.Chain
import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.effect.std.Dispatcher
import cats.effect.testkit._
import cats.instances.order._
import cats.laws.discipline.arbitrary.catsLawsArbitraryForChain
import cats.syntax.all._
import com.comcast.ip4s
import com.comcast.ip4s.Arbitraries._
import fs2.Pure
import fs2.Stream
import org.http4s.headers._
import org.http4s.syntax.literals._
import org.scalacheck.Arbitrary.{arbitrary => getArbitrary}
import org.scalacheck.Gen._
import org.scalacheck._
import org.scalacheck.rng.Seed
import org.typelevel.ci.CIString
import org.typelevel.ci.testing.arbitraries._
import scodec.bits.ByteVector

import java.nio.charset.{Charset => NioCharset}
import java.time._
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import scala.annotation.nowarn
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object arbitrary extends ArbitraryInstancesBinCompat0

@deprecated(
  "Use `arbitrary` instead. They were redundant, and that one is consistent with Cats.",
  "0.22.6",
)
object ArbitraryInstances extends ArbitraryInstancesBinCompat0

private[discipline] trait ArbitraryInstances {
  this: ArbitraryInstancesBinCompat0 =>

  implicit private class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  implicit def http4sTestingArbitraryForNonEmptyList[A: Arbitrary]: Arbitrary[NonEmptyList[A]] =
    Arbitrary {
      for {
        a <- getArbitrary[A]
        list <- getArbitrary[List[A]]
      } yield NonEmptyList(a, list)
    }

  val genChar: Gen[Char] = choose('\u0000', '\u007F')

  val ctlChar: List[Char] = ('\u007F' +: ('\u0000' to '\u001F')).toList

  val lws: List[Char] = " \t".toList

  val genCrLf: Gen[String] = const("\r\n")

  val genRightLws: Gen[String] =
    // No need to produce very long whitespaces
    resize(5, nonEmptyListOf(oneOf(lws)).map(_.mkString))

  val genLws: Gen[String] =
    oneOf(sequence[List[String], String](List(genCrLf, genRightLws)).map(_.mkString), genRightLws)

  val octets: List[Char] = ('\u0000' to '\u00FF').toList

  val genOctet: Gen[Char] = oneOf(octets)

  val allowedText: List[Char] = octets.diff(ctlChar)

  val genText: Gen[String] = oneOf(nonEmptyListOf(oneOf(allowedText)).map(_.mkString), genLws)

  // TODO Fix Rfc2616BasicRules.QuotedString to support the backslash character
  val allowedQDText: List[Char] = allowedText.filterNot(c => c == '"' || c == '\\')

  val genQDText: Gen[String] = nonEmptyListOf(oneOf(allowedQDText)).map(_.mkString)

  @deprecated(
    "Generates encoded values instead of the expected unencoded values that arbitraries should.  Use genQDText instead.",
    "0.22.5",
  )
  val genQuotedPair: Gen[String] =
    genChar.map(c => s"\\$c")

  @deprecated(
    "Generates encoded values instead of the expected unencoded values that arbitraries should.  Use genQDText instead.",
    "0.22.5",
  )
  val genQuotedString: Gen[String] = oneOf(genQDText, genQuotedPair).map(s => s"""\"$s\"""")

  private val tchars =
    Set('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~') ++
      ('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z')

  val genTchar: Gen[Char] = oneOf(tchars)

  val genToken: Gen[String] =
    nonEmptyListOf(genTchar).map(_.mkString)

  val genNonTchar: Gen[Char] = frequency(
    4 -> oneOf(Set(0x00.toChar to 0x7f.toChar: _*) -- tchars),
    1 -> oneOf(0x100.toChar to Char.MaxValue),
  )

  val genNonToken: Gen[String] = for {
    a <- stringOf(genTchar)
    b <- nonEmptyListOf(genNonTchar).map(_.mkString)
    c <- stringOf(genChar)
  } yield a + b + c

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
          } yield spaces :+ fieldVchar),
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
      1 -> genToken.map(Method.fromString(_).yolo),
    )
  )
  implicit val http4sTestingCogenForMethod: Cogen[Method] =
    Cogen[Int].contramap(_.##)

  val genValidStatusCode: Gen[Int] =
    choose(Status.MinCode, Status.MaxCode)

  val genStandardStatus: Gen[Status] =
    oneOf(Status.registered)

  @deprecated(
    "Custom status phrases will be removed in 1.0. They are an optional feature, pose a security risk, and already unsupported on some backends.",
    "0.22.6",
  )
  val genCustomStatus: Gen[Status] = for {
    code <- genValidStatusCode
    reason <- genCustomStatusReason
  } yield Status.fromInt(code).yolo.withReason(reason)

  @nowarn("cat=deprecation")
  implicit val http4sTestingArbitraryForStatus: Arbitrary[Status] = Arbitrary(
    frequency(
      4 -> genStandardStatus,
      1 -> genCustomStatus,
    )
  )
  implicit val http4sTestingCogenForStatus: Cogen[Status] =
    Cogen[Int].contramap(_.code)

  implicit val http4sTestingArbitraryForQueryParam: Arbitrary[(String, Option[String])] =
    Arbitrary {
      frequency(
        5 -> {
          for {
            k <- getArbitrary[String]
            v <- getArbitrary[Option[String]]
          } yield (k, v)
        },
        2 -> const("foo" -> Some("bar")), // Want some repeats
      )
    }

  implicit val http4sTestingArbitraryForQuery: Arbitrary[Query] =
    Arbitrary {
      for {
        n <- size
        vs <- containerOfN[Vector, (String, Option[String])](
          n % 8,
          http4sTestingArbitraryForQueryParam.arbitrary,
        )
      } yield Query(vs: _*)
    }

  implicit val http4sTestingArbitraryForHttpVersion: Arbitrary[HttpVersion] = {
    val genSpecified = Gen.oneOf(HttpVersion.specified)
    val genAll = for {
      major <- Gen.chooseNum(0, 9)
      minor <- Gen.chooseNum(0, 9)
    } yield HttpVersion.fromVersion(major, minor).yolo
    Arbitrary(Gen.oneOf(genSpecified, genAll))
  }

  implicit val http4sTestingCogenForHttpVersion: Cogen[HttpVersion] =
    Cogen[(Int, Int)].contramap(v => (v.major, v.minor))

  implicit val http4sTestingArbitraryForNioCharset: Arbitrary[NioCharset] =
    Arbitrary(oneOf(Charset.availableCharsets))

  implicit val http4sTestingCogenForNioCharset: Cogen[NioCharset] =
    Cogen[String].contramap(_.name)

  implicit val http4sTestingArbitraryForCharset: Arbitrary[Charset] =
    Arbitrary(getArbitrary[NioCharset].map(Charset.fromNioCharset))

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
        q <- getArbitrary[QValue]
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
        charset <- getArbitrary[Charset]
        q <- getArbitrary[QValue]
      } yield charset.withQuality(q)
    }

  implicit val http4sTestingArbitraryForCharsetSplatRange: Arbitrary[CharsetRange.`*`] =
    Arbitrary(getArbitrary[QValue].map(CharsetRange.`*`.withQValue(_)))

  def genCharsetRangeNoQuality: Gen[CharsetRange] =
    frequency(
      3 -> getArbitrary[Charset].map(CharsetRange.fromCharset),
      1 -> const(CharsetRange.`*`),
    )

  implicit val http4sTestingArbitraryForAcceptCharset: Arbitrary[`Accept-Charset`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        charsetRanges <- nonEmptyContainerOf[Set, CharsetRange](genCharsetRangeNoQuality)
          .map(_.toVector)
        qValues <- containerOfN[Vector, QValue](
          charsetRanges.size,
          http4sTestingArbitraryForQValue.arbitrary,
        )
        charsetRangesWithQ = charsetRanges.zip(qValues).map { case (range, q) =>
          range.withQValue(q)
        }
      } yield `Accept-Charset`(charsetRangesWithQ.head, charsetRangesWithQ.tail: _*)
    }

  def genContentCodingNoQuality: Gen[ContentCoding] =
    Gen.frequency(
      (10, oneOf(ContentCoding.standard.values.toSeq)),
      (2, genToken.map(ContentCoding.unsafeFromString)),
    )

  implicit val http4sTrstingArbitraryForContentCoding: Arbitrary[ContentCoding] =
    Arbitrary {
      for {
        cc <- genContentCodingNoQuality
        q <- getArbitrary[QValue]
      } yield cc.withQValue(q)
    }

  implicit val http4sTestingCogenForContentCoding: Cogen[ContentCoding] =
    Cogen[String].contramap(cc => augmentString(cc.coding).map(_.toUpper.toLower))

  // MediaRange expects the quoted pair without quotes
  val http4sGenUnquotedPair: Gen[String] = genQDText

  val http4sGenMediaRangeExtension: Gen[(String, String)] =
    for {
      token <- genToken
      value <- oneOf(http4sGenUnquotedPair, genQDText)
    } yield (token, value)

  val http4sGenMediaRangeExtensions: Gen[Map[String, String]] =
    Gen.listOf(http4sGenMediaRangeExtension).map(_.toMap)

  val http4sGenMediaType: Gen[MediaType] = oneOf(MediaType.all.values.toSeq)
  implicit val http4sArbitraryMediaType: Arbitrary[MediaType] = Arbitrary(http4sGenMediaType)

  implicit val http4sTestingCogenForMediaType: Cogen[MediaType] =
    Cogen[(String, String, Map[String, String])].contramap(m =>
      (m.mainType, m.subType, m.extensions)
    )

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
          http4sTestingArbitraryForQValue.arbitrary,
        )
        contentCodingsWithQ = contentCodings.zip(qValues).map { case (coding, q) =>
          coding.withQValue(q)
        }
      } yield `Accept-Encoding`(NonEmptyList.fromListUnsafe(contentCodingsWithQ.toList))
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
        mediaType <- getArbitrary[MediaType]
        charset <- getArbitrary[Charset]
      } yield `Content-Type`(mediaType, charset)
    }

  def genLanguageTagNoQuality: Gen[LanguageTag] =
    frequency(
      3 -> (for {
        primaryTag <- genAlphaToken
        subTags <- frequency(4 -> Nil, 1 -> listOf(genAlphaToken))
      } yield LanguageTag(primaryTag, subTags = subTags)),
      1 -> const(LanguageTag.`*`),
    )

  implicit val http4sTestingArbitraryForLanguageTag: Arbitrary[LanguageTag] =
    Arbitrary {
      for {
        lt <- genLanguageTagNoQuality
        q <- getArbitrary[QValue]
      } yield lt.copy(q = q)
    }

  implicit val http4sTestingArbitraryForAcceptLanguage: Arbitrary[`Accept-Language`] =
    Arbitrary {
      for {
        // make a set first so we don't have contradictory q-values
        languageTags <- nonEmptyContainerOf[Set, LanguageTag](genLanguageTagNoQuality)
          .map(_.toVector)
        qValues <-
          containerOfN[Vector, QValue](languageTags.size, http4sTestingArbitraryForQValue.arbitrary)
        tagsWithQ = languageTags.zip(qValues).map { case (tag, q) => tag.copy(q = q) }
      } yield `Accept-Language`(tagsWithQ.head, tagsWithQ.tail: _*)
    }

  implicit val http4sTestingArbitraryForUrlForm: Arbitrary[UrlForm] = Arbitrary {
    // new String("\ufffe".getBytes("UTF-16"), "UTF-16") != "\ufffe".
    // Ain't nobody got time for that.
    getArbitrary[Map[String, Chain[String]]]
      .map(s =>
        s.map { case (k, v) =>
          k.replace('\ufffe', 'a') -> v.map(_.replace('\ufffe', 'a'))
        }
      )
      .map(UrlForm.apply)
  }

  implicit val http4sTestingArbitraryForAllow: Arbitrary[Allow] =
    Arbitrary {
      for {
        methods <- containerOf[Set, Method](getArbitrary[Method])
      } yield Allow(methods)
    }

  implicit val http4sTestingArbitraryForContentLength: Arbitrary[`Content-Length`] =
    Arbitrary {
      for {
        long <- Gen.chooseNum(0L, Long.MaxValue)
      } yield `Content-Length`.unsafeFromLong(long)
    }

  implicit val http4sTestingArbitraryForMaxForwards: Arbitrary[`Max-Forwards`] =
    Arbitrary {
      for {
        value <- Gen.chooseNum(0L, Long.MaxValue)
      } yield `Max-Forwards`.unsafeFromLong(value)
    }

  implicit val http4sTestingArbitraryForXB3TraceId: Arbitrary[`X-B3-TraceId`] =
    Arbitrary {
      for {
        msb <- getArbitrary[Long]
        lsb <- Gen.option(getArbitrary[Long])
      } yield `X-B3-TraceId`(msb, lsb)
    }

  implicit val http4sTestingArbitraryForXB3SpanId: Arbitrary[`X-B3-SpanId`] =
    Arbitrary {
      for {
        long <- getArbitrary[Long]
      } yield `X-B3-SpanId`(long)
    }

  implicit val http4sTestingArbitraryForXB3ParentSpanId: Arbitrary[`X-B3-ParentSpanId`] =
    Arbitrary {
      for {
        long <- getArbitrary[Long]
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
            `X-B3-Flags`.Flag.SamplingSet,
          ),
        )
      } yield `X-B3-Flags`(flags.toSet)
    }

  implicit val http4sTestingArbitraryForXB3Sampled: Arbitrary[`X-B3-Sampled`] =
    Arbitrary {
      for {
        boolean <- getArbitrary[Boolean]
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

  implicit val http4sTestingArbitraryForDeprecationHeader: Arbitrary[headers.Deprecation] =
    Arbitrary {
      for {
        httpDate <- genHttpDate
      } yield headers.Deprecation(httpDate)
    }

  implicit val http4sTestingArbitraryForSunsetHeader: Arbitrary[headers.Sunset] =
    Arbitrary {
      for {
        httpDate <- genHttpDate
      } yield headers.Sunset(httpDate)
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
      qValue <- getArbitrary[QValue]
    } yield MediaRangeAndQValue(mediaRange, qValue)

  implicit val http4sTestingArbitraryForAceesContrlolAllowedCredentials
      : Arbitrary[headers.`Access-Control-Allow-Credentials`] =
    Arbitrary {
      Gen.const(`Access-Control-Allow-Credentials`())
    }

  implicit val http4sTestingArbitraryForAcceptHeader: Arbitrary[headers.Accept] =
    Arbitrary {
      for {
        values <- nonEmptyListOf(http4sGenMediaRangeAndQValue)
      } yield headers.Accept(NonEmptyList.of(values.head, values.tail: _*))
    }

  implicit val http4sTestingArbitraryForAccessControlAllowHeaders
      : Arbitrary[headers.`Access-Control-Allow-Headers`] =
    Arbitrary {
      for {
        values <- nonEmptyListOf(genToken.map(CIString(_)))
      } yield headers.`Access-Control-Allow-Headers`(NonEmptyList.of(values.head, values.tail: _*))
    }

  implicit val http4sTestingArbitraryForAccessControlExposeHeaders
      : Arbitrary[headers.`Access-Control-Expose-Headers`] =
    Arbitrary {
      for {
        values <- nonEmptyListOf(genToken.map(CIString(_)))
      } yield headers.`Access-Control-Expose-Headers`(NonEmptyList.of(values.head, values.tail: _*))
    }

  implicit val http4sTestingArbitraryForRetryAfterHeader: Arbitrary[headers.`Retry-After`] =
    Arbitrary {
      for {
        retry <- Gen.oneOf(genHttpExpireDate.map(Left(_)), Gen.posNum[Long].map(Right(_)))
      } yield retry.fold(
        headers.`Retry-After`.apply,
        headers.`Retry-After`.unsafeFromLong,
      )
    }

  implicit val http4sTestingArbitraryForAcceptPatchHeader: Arbitrary[headers.`Accept-Patch`] =
    Arbitrary {
      for {
        media <- getArbitrary[NonEmptyList[MediaType]]
      } yield headers.`Accept-Patch`(media)
    }

  implicit val http4sTestingArbitraryForAgeHeader: Arbitrary[headers.Age] =
    Arbitrary {
      for {
        // age is always positive
        age <- genFiniteDuration
      } yield headers.Age.unsafeFromDuration(age)
    }

  implicit val http4sTestingArbitraryForAccessControlAllowMethodsHeader
      : Arbitrary[headers.`Access-Control-Allow-Methods`] =
    Arbitrary {
      for {
        methods <- containerOf[Set, Method](getArbitrary[Method])
      } yield `Access-Control-Allow-Methods`(methods)
    }

  implicit val http4sTestingArbitraryForSTS: Arbitrary[headers.`Strict-Transport-Security`] =
    Arbitrary {
      for {
        // age is always positive
        age <- genFiniteDuration
        includeSubDomains <- Gen.oneOf(true, false)
        preload <- Gen.oneOf(true, false)
      } yield headers.`Strict-Transport-Security`.unsafeFromDuration(
        age,
        includeSubDomains,
        preload,
      )
    }

  implicit val http4sTestingArbitraryForTransferEncoding: Arbitrary[`Transfer-Encoding`] =
    Arbitrary {
      for {
        codings <- getArbitrary[NonEmptyList[TransferCoding]]
      } yield `Transfer-Encoding`(codings)
    }

  implicit val http4sTestingArbitraryForSecWebSocketAcceptHeader
      : Arbitrary[`Sec-WebSocket-Accept`] =
    Arbitrary {
      Gen
        .containerOfN[Array, Byte](20, getArbitrary[Byte])
        .map(Base64.getEncoder().encode)
        .map(ByteVector(_))
        .map(`Sec-WebSocket-Accept`(_))
    }

  implicit val http4sTestingArbitraryForSecWebSocketKeyHeader: Arbitrary[`Sec-WebSocket-Key`] =
    Arbitrary {
      Gen
        .containerOfN[Array, Byte](16, getArbitrary[Byte])
        .map(Base64.getEncoder().encode)
        .map(ByteVector(_))
        .map(`Sec-WebSocket-Key`(_))
    }

  implicit val http4sTestingArbitraryForRawHeader: Arbitrary[Header.Raw] =
    Arbitrary {
      for {
        token <- frequency(8 -> genToken, 1 -> asciiStr, 1 -> getArbitrary[String])
        value <- genFieldValue
      } yield Header.Raw(CIString(token), value)
    }

  implicit val http4sTestingArbitraryForHeaders: Arbitrary[Headers] =
    Arbitrary(listOf(getArbitrary[Header.Raw]).map(Headers(_)))

  implicit val http4sTestingArbitraryForServerSentEvent: Arbitrary[ServerSentEvent] = {
    import ServerSentEvent._
    def singleLineString: Gen[String] =
      getArbitrary[String].suchThat { s =>
        !s.contains("\r") && !s.contains("\n")
      }

    Arbitrary(
      for {
        data <- frequency(
          8 -> singleLineString.map(Some.apply),
          1 -> None,
        )
        comment <- frequency(
          1 -> singleLineString.map(Some.apply),
          8 -> None,
        )
        event <- frequency(
          4 -> None,
          1 -> singleLineString.map(Some.apply),
        )
        id <- frequency(
          8 -> None,
          1 -> Some(EventId.reset),
          1 -> singleLineString.suchThat(_.nonEmpty).map(id => Some(EventId(id))),
        )
        retry <- frequency(
          4 -> None,
          1 -> posNum[Long].map(Some.apply),
        )
      } yield ServerSentEvent(
        data,
        event,
        id,
        retry.map(FiniteDuration(_, TimeUnit.MILLISECONDS)),
        comment,
      )
    )
  }

  // https://datatracker.ietf.org/doc/html/rfc2234#section-6
  val genHexDigit: Gen[Char] = oneOf(
    List('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
  )

  val genPctEncoded: Gen[String] =
    const("%") |+| genHexDigit.map(_.toString) |+| genHexDigit.map(_.toString)
  val genUnreserved: Gen[Char] =
    oneOf(alphaChar, numChar, const('-'), const('.'), const('_'), const('~'))
  val genSubDelims: Gen[Char] = oneOf(List('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='))

  implicit private def http4sTestingSemigroupForGen[T: Semigroup]: Semigroup[Gen[T]] =
    new Semigroup[Gen[T]] {
      def combine(g1: Gen[T], g2: Gen[T]): Gen[T] = for {
        t1 <- g1
        t2 <- g2
      } yield t1 |+| t2
    }

  private def opt[T](g: Gen[T])(implicit ev: Monoid[T]): Gen[T] =
    oneOf(g, const(ev.empty))

  implicit val http4sTestingArbitraryForIpv4Address: Arbitrary[Uri.Ipv4Address] =
    Arbitrary(ip4s.Arbitraries.ipv4Generator.map(Uri.Ipv4Address.apply))

  implicit val http4sTestingCogenForIpv4Address: Cogen[Uri.Ipv4Address] =
    Cogen[Array[Byte]].contramap(_.address.toBytes)

  implicit val http4sTestingArbitraryForIpv6Address: Arbitrary[Uri.Ipv6Address] =
    Arbitrary(ip4s.Arbitraries.ipv6Generator.map(Uri.Ipv6Address.apply))

  implicit val http4sTestingCogenForIpv6Address: Cogen[Uri.Ipv6Address] =
    Cogen[Array[Byte]].contramap(_.address.toBytes)

  /* TODO Everything generated here resembles a plausible hostname,
   * whereas the spec allows weird corner cases of arbitrary,
   * percent-encoded UTF-8 strings.  This is a practical generator,
   * but dragons be here for full spec compliance.
   */
  implicit val http4sTestingArbitraryForUriHost: Arbitrary[Uri.Host] =
    Arbitrary(
      oneOf(
        getArbitrary[Uri.Ipv4Address],
        getArbitrary[Uri.Ipv6Address],
        getArbitrary[ip4s.Hostname].map(Uri.RegName.fromHostname),
      )
    )

  implicit val http4sTestingArbitraryForUserInfo: Arbitrary[Uri.UserInfo] =
    Arbitrary(
      for {
        username <- Gen.frequency(
          9 -> Gen.stringOf(Gen.alphaNumChar),
          1 -> getArbitrary[String],
        )
        password <- getArbitrary[Option[String]]
      } yield Uri.UserInfo(username, password)
    )

  implicit val http4sTestingCogenForUserInfo: Cogen[Uri.UserInfo] =
    Cogen.tuple2[String, Option[String]].contramap(u => (u.username, u.password))

  implicit val http4sTestingArbitraryForAuthority: Arbitrary[Uri.Authority] = Arbitrary {
    for {
      maybeUserInfo <- Gen.frequency(
        4 -> None,
        1 -> getArbitrary[Uri.UserInfo].map(Some(_)),
      )
      host <- http4sTestingArbitraryForUriHost.arbitrary
      maybePort <- Gen.option(posNum[Int].suchThat(port => port >= 0 && port <= 65536))
    } yield Uri.Authority(maybeUserInfo, host, maybePort)
  }

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
            1 -> const('.'),
          )
        )
      } yield HttpCodec[Uri.Scheme].parseOrThrow(tail.mkString(head.toString, "", ""))),
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
      TransferCoding.identity,
    )
  }

  implicit val http4sTestingCogenForTransferCoding: Cogen[TransferCoding] =
    Cogen[String].contramap(_.coding.toLowerCase(Locale.ROOT))

  implicit val http4sTestingAbitraryForPath: Arbitrary[Uri.Path] = Arbitrary {
    val genSegmentNzNc =
      nonEmptyListOf[Any](oneOf(genUnreserved, genPctEncoded, genSubDelims, const("@")))
        .map(_.mkString)
    val genPChar = oneOf[Any](genUnreserved, genPctEncoded, genSubDelims, const(":"), const("@"))
    val genSegmentNz = nonEmptyListOf(genPChar).map(_.mkString)
    val genSegment = listOf(genPChar).map(_.mkString)
    val genPathEmpty = const("")
    val genPathAbEmpty = listOf(const("/") |+| genSegment).map(_.mkString) |+| opt("/")
    val genPathRootless = genSegmentNz |+| genPathAbEmpty |+| opt("/")
    val genPathNoScheme = genSegmentNzNc |+| genPathAbEmpty |+| opt("/")
    val genPathAbsolute = const("/") |+| opt(genPathRootless) |+| opt("/")

    oneOf(genPathAbEmpty, genPathAbsolute, genPathNoScheme, genPathRootless, genPathEmpty).map(
      Uri.Path.unsafeFromString
    )
  }

  implicit val http4sTestingCogenForPath: Cogen[Uri.Path] =
    Cogen[String].contramap(_.renderString)

  /** https://datatracker.ietf.org/doc/html/rfc3986 */
  implicit val http4sTestingArbitraryForUri: Arbitrary[Uri] = Arbitrary {
    val genPChar = oneOf[Any](genUnreserved, genPctEncoded, genSubDelims, const(":"), const("@"))
    val genScheme = oneOf(Uri.Scheme.http, Uri.Scheme.https)

    val genFragment: Gen[Uri.Fragment] =
      listOf(oneOf(genPChar, const("/"), const("?"))).map(_.mkString)

    for {
      scheme <- Gen.option(genScheme)
      authority <- Gen.option(http4sTestingArbitraryForAuthority.arbitrary)
      path <- http4sTestingAbitraryForPath.arbitrary
      query <- http4sTestingArbitraryForQuery.arbitrary
      fragment <- Gen.option(genFragment)
    } yield Uri(scheme, authority, path, query, fragment)
  }

  /** Creates an `Gen[Url]` with checking that a URI is converting to String
    * and back to URI safely.
    * Use this Gen with cautions - it may lead to tests performance degradation.
    */
  def createGenUri: Gen[Uri] =
    http4sTestingArbitraryForUri.arbitrary.filter { uri =>
      // Uri.renderString encode special chars in the fragment
      // and after converting the Uri to Json, the fragment will be encoded
      val convertedBackToUriWithFragment =
        (f: Uri.Fragment) => Uri.fromString(uri.withoutFragment.toString).map(_.withFragment(f))
      val parsedUri =
        uri.fragment.fold(Uri.fromString(uri.toString))(convertedBackToUriWithFragment)

      parsedUri == Right(uri)
    }

  implicit val http4sTestingArbitraryForLink: Arbitrary[LinkValue] = Arbitrary {
    for {
      uri <- http4sTestingArbitraryForUri.arbitrary
    } yield LinkValue(uri)
  }

  implicit val http4sTestingCogenForUri: Cogen[Uri] =
    Cogen[String].contramap(_.renderString)

  // TODO This could be a lot more interesting.
  // See https://github.com/functional-streams-for-scala/fs2/blob/fd3d0428de1e71c10d1578f2893ee53336264ffe/core/shared/src/test/scala/fs2/TestUtil.scala#L42
  implicit def http4sTestingGenForPureByteStream[F[_]]: Gen[Stream[Pure, Byte]] =
    Gen.sized { size =>
      Gen.listOfN(size, getArbitrary[Byte]).map(Stream.emits)
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

  implicit def http4sTestingCogenForEntityBody[F[_]](implicit
      F: Concurrent[F],
      dispatcher: Dispatcher[F],
      testContext: TestContext,
  ): Cogen[EntityBody[F]] =
    cogenFuture[Vector[Byte]].contramap { stream =>
      dispatcher.unsafeToFuture(stream.compile.toVector)
    }

  implicit def http4sTestingArbitraryForEntity[F[_]]: Arbitrary[Entity[F]] =
    Arbitrary(Gen.sized { size =>
      for {
        body <- http4sTestingGenForPureByteStream
        length <- Gen.oneOf(Some(size.toLong), None)
      } yield Entity(body, length)
    })

  implicit def http4sTestingCogenForEntity[F[_]](implicit
      F: Concurrent[F],
      dispatcher: Dispatcher[F],
      testContext: TestContext,
  ): Cogen[Entity[F]] =
    Cogen[(EntityBody[F], Option[Long])].contramap(entity => (entity.body, entity.length))

  implicit def http4sTestingArbitraryForEntityEncoder[F[_], A](implicit
      CA: Cogen[A]
  ): Arbitrary[EntityEncoder[F, A]] =
    Arbitrary(for {
      f <- getArbitrary[A => Entity[F]]
      hs <- getArbitrary[Headers]
    } yield EntityEncoder.encodeBy(hs)(f))

  implicit def http4sTestingArbitraryForEntityDecoder[F[_], A](implicit
      F: Concurrent[F],
      dispatcher: Dispatcher[F],
      testContext: TestContext,
      g: Arbitrary[DecodeResult[F, A]],
  ): Arbitrary[EntityDecoder[F, A]] =
    Arbitrary(for {
      f <- getArbitrary[(Media[F], Boolean) => DecodeResult[F, A]]
      mrs <- getArbitrary[Set[MediaRange]]
    } yield new EntityDecoder[F, A] {
      def decode(m: Media[F], strict: Boolean): DecodeResult[F, A] = f(m, strict)

      def consumes = mrs
    })

  implicit def http4sTestingCogenForMedia[F[_]](implicit
      F: Concurrent[F],
      dispatcher: Dispatcher[F],
      testContext: TestContext,
  ): Cogen[Media[F]] =
    Cogen[(Headers, EntityBody[F])].contramap(m => (m.headers, m.body))

  implicit def http4sTestingCogenForMessage[F[_]](implicit
      F: Concurrent[F],
      dispatcher: Dispatcher[F],
      testContext: TestContext,
  ): Cogen[Message[F]] =
    Cogen[(Headers, EntityBody[F])].contramap(m => (m.headers, m.body))

  implicit def http4sTestingCogenForHeaders: Cogen[Headers] =
    Cogen[List[Header.Raw]].contramap(_.headers)

  implicit def http4sTestingCogenForHeader: Cogen[Header.Raw] =
    Cogen[(CIString, String)].contramap(h => (h.name, h.value))

  implicit def http4sTestingArbitraryForDecodeFailure: Arbitrary[DecodeFailure] =
    Arbitrary(
      oneOf(
        http4sTestingGenForMalformedMessageBodyFailure,
        http4sTestingGenForInvalidMessageBodyFailure,
        http4sTestingGenForMediaTypeMissing,
        http4sTestingGenForMediaTypeMismatch,
      )
    )

  implicit val http4sTestingGenForMalformedMessageBodyFailure: Gen[MalformedMessageBodyFailure] =
    for {
      details <- getArbitrary[String]
      cause <- getArbitrary[Option[Throwable]]
    } yield MalformedMessageBodyFailure(details, cause)

  implicit val http4sTestingGenForInvalidMessageBodyFailure: Gen[InvalidMessageBodyFailure] =
    for {
      details <- getArbitrary[String]
      cause <- getArbitrary[Option[Throwable]]
    } yield InvalidMessageBodyFailure(details, cause)

  implicit val http4sTestingGenForMediaTypeMissing: Gen[MediaTypeMissing] =
    getArbitrary[Set[MediaRange]].map(MediaTypeMissing(_))

  implicit val http4sTestingGenForMediaTypeMismatch: Gen[MediaTypeMismatch] =
    for {
      messageType <- getArbitrary[MediaType]
      expected <- getArbitrary[Set[MediaRange]]
    } yield MediaTypeMismatch(messageType, expected)

  // These instances are private because they're half-baked and I don't want to encourage external use yet.
  implicit private[http4s] val http4sTestingCogenForDecodeFailure: Cogen[DecodeFailure] =
    Cogen { (seed: Seed, df: DecodeFailure) =>
      (df: @unchecked) match {
        case MalformedMessageBodyFailure(d, t) =>
          Cogen[(String, Option[Throwable])].perturb(seed, (d, t))
        case InvalidMessageBodyFailure(d, t) =>
          Cogen[(String, Option[Throwable])].perturb(seed, (d, t))
        case MediaTypeMissing(mrs) => Cogen[Set[MediaRange]].perturb(seed, mrs)
        case MediaTypeMismatch(mt, e) => Cogen[(MediaType, Set[MediaRange])].perturb(seed, (mt, e))
        // TODO What if it's not one of these?
      }
    }

  implicit private[http4s] def http4sTestingArbitraryForMessage[F[_]]: Arbitrary[Message[F]] =
    // TODO this is bad because the underlying generators are bad
    Arbitrary(Gen.oneOf(getArbitrary[Request[F]], getArbitrary[Response[F]]))

  implicit private[http4s] def http4sTestingArbitraryForRequest[F[_]]: Arbitrary[Request[F]] =
    Arbitrary {
      // TODO some methods don't take bodies
      // TODO some arbitrary headers are mutually exclusive
      // TODO some headers need to be reflective of the body
      // TODO some things are illegal per HTTP version
      for {
        method <- getArbitrary[Method]
        uri <- getArbitrary[Uri]
        httpVersion <- getArbitrary[HttpVersion]
        headers <- getArbitrary[Headers]
        body <- http4sTestingGenForPureByteStream
      } yield try Request(method, uri, httpVersion, headers, body)
      catch {
        case t: Throwable => t.printStackTrace(); throw t
      }
    }

  implicit private[http4s] def http4sTestingArbitraryForContextRequest[F[_], A: Arbitrary]
      : Arbitrary[ContextRequest[F, A]] =
    // TODO this is bad because the underlying generators are bad
    Arbitrary {
      for {
        a <- getArbitrary[A]
        request <- getArbitrary[Request[F]]
      } yield new ContextRequest(a, request)
    }

  implicit private[http4s] def http4sTestingArbitraryForResponse[F[_]]: Arbitrary[Response[F]] =
    Arbitrary {
      // TODO some statuses don't take bodies
      // TODO some arbitrary headers are mutually exclusive
      // TODO some headers need to be reflective of the body
      // TODO some things are illegal per HTTP version
      for {
        status <- getArbitrary[Status]
        httpVersion <- getArbitrary[HttpVersion]
        headers <- getArbitrary[Headers]
        body <- http4sTestingGenForPureByteStream
      } yield Response(status, httpVersion, headers, body)
    }

  implicit val http4sTestingArbitraryForSegment: Arbitrary[Uri.Path.Segment] =
    Arbitrary(getArbitrary[String].map(Uri.Path.Segment.apply))

  implicit val http4sTestingCogenForSegment: Cogen[Uri.Path.Segment] =
    Cogen[String].contramap(_.encoded)
}

private[discipline] trait ArbitraryInstancesBinCompat0 extends ArbitraryInstances {
  val genAlphaToken: Gen[String] =
    nonEmptyListOf(alphaChar).map(_.mkString)

  val genOptWs: Gen[String] = option(genLws).map(_.orEmpty)

  val genListSep: Gen[String] =
    sequence[List[String], String](List(genOptWs, const(","), genOptWs)).map(_.mkString)

  implicit val http4sTestingCogenForQuery: Cogen[Query] =
    Cogen[Vector[(String, Option[String])]].contramap(_.toVector)

  implicit val http4sTestingArbitraryForRegName: Arbitrary[Uri.RegName] =
    Arbitrary(
      listOf(oneOf[Any](genUnreserved, genPctEncoded, genSubDelims)).map(rn =>
        Uri.RegName(rn.mkString)
      )
    )

  implicit val http4sTestingCogenForRegName: Cogen[Uri.RegName] =
    Cogen[CIString].contramap(_.host)

  implicit val http4sTestingCogenForUriHost: Cogen[Uri.Host] =
    Cogen[Either[Uri.RegName, Either[Uri.Ipv4Address, Uri.Ipv6Address]]].contramap {
      case value: Uri.RegName => Left(value)
      case value: Uri.Ipv4Address => Right(Left(value))
      case value: Uri.Ipv6Address => Right(Right(value))
    }

  implicit val http4sTestingCogenForAuthority: Cogen[Uri.Authority] =
    Cogen
      .tuple3[Option[Uri.UserInfo], Uri.Host, Option[Int]]
      .contramap(a => (a.userInfo, a.host, a.port))

  implicit val http4sTestingArbitraryForRequestPrelude: Arbitrary[RequestPrelude] =
    Arbitrary(
      for {
        headers <- Arbitrary.arbitrary[Headers]
        httpVersion <- Arbitrary.arbitrary[HttpVersion]
        method <- Arbitrary.arbitrary[Method]
        uri <- Arbitrary.arbitrary[Uri]
      } yield RequestPrelude(headers, httpVersion, method, uri)
    )

  implicit val http4sTestingCogenForRequestPrelude: Cogen[RequestPrelude] =
    Cogen[(Headers, HttpVersion, Method, Uri)].contramap(value =>
      (value.headers, value.httpVersion, value.method, value.uri)
    )

  implicit val http4sTestingArbitraryForResponsePrelude: Arbitrary[ResponsePrelude] =
    Arbitrary(
      for {
        headers <- Arbitrary.arbitrary[Headers]
        httpVersion <- Arbitrary.arbitrary[HttpVersion]
        status <- Arbitrary.arbitrary[Status]
      } yield ResponsePrelude(headers, httpVersion, status)
    )

  implicit val http4sTestingCogenForResponsePrelude: Cogen[ResponsePrelude] =
    Cogen[(Headers, HttpVersion, Status)].contramap(value =>
      (value.headers, value.httpVersion, value.status)
    )

  implicit val http4sTestingArbitraryForKeepAlive: Arbitrary[`Keep-Alive`] = Arbitrary {
    val genExtension = for {
      extName <- genToken.filterNot(t => t === "timeout" || t === "max")
      quotedStringEquivWithoutQuotes =
        genQDText // The string parsed out does not have quotes around it.  QuotedPair was generating invalid as well.
      extValue <- Gen.option(Gen.oneOf(quotedStringEquivWithoutQuotes, genToken))
    } yield extName -> extValue

    for {
      timeout <- Gen.option(Gen.chooseNum(0L, Long.MaxValue))
      max <- Gen.option(Gen.chooseNum(0L, Long.MaxValue))
      l <- Gen.listOf(genExtension)
      if timeout.isDefined || max.isDefined || l.nonEmpty // One of these fields is necessary to be valid.
    } yield `Keep-Alive`.unsafeApply(timeout, max, l)
  }

  val genCustomStatusReason: Gen[String] = {
    val word = poisson(5).flatMap(stringOfN(_, alphaChar))
    val normal = poisson(3).flatMap(listOfN(_, word)).map(_.mkString(" "))
    val exotic = stringOf(
      frequency(
        1 -> '\t',
        1 -> const(' '),
        94 -> asciiPrintableChar,
      )
    )
    val unsanitizedAscii = asciiStr
    val unsanitized = getArbitrary[String]
    oneOf(
      normal,
      exotic,
      unsanitizedAscii,
      unsanitized,
    )
  }
  val dntGen: Gen[DNT] = Gen.oneOf(DNT.AllowTracking, DNT.DisallowTracking, DNT.NoPreference)
  implicit val arbDnt: Arbitrary[DNT] = Arbitrary[DNT](dntGen)

  implicit val arbUpgradeInsecureRequests: Arbitrary[`Upgrade-Insecure-Requests`] =
    Arbitrary[`Upgrade-Insecure-Requests`](`Upgrade-Insecure-Requests`)

  implicit val arbitraryAcceptPost: Arbitrary[`Accept-Post`] = Arbitrary {
    for {
      values <- listOf(http4sGenMediaType)
    } yield headers.`Accept-Post`(values)
  }

  implicit val arbitraryCrossOriginResourcePolicy: Arbitrary[`Cross-Origin-Resource-Policy`] =
    Arbitrary[`Cross-Origin-Resource-Policy`](
      Gen.oneOf(
        `Cross-Origin-Resource-Policy`.SameSite,
        `Cross-Origin-Resource-Policy`.SameOrigin,
        `Cross-Origin-Resource-Policy`.CrossOrigin,
      )
    )

  implicit val arbitraryContentTransferEncoding: Arbitrary[`Content-Transfer-Encoding`] =
    Arbitrary[`Content-Transfer-Encoding`](
      Gen.oneOf(
        `Content-Transfer-Encoding`.`7bit`,
        `Content-Transfer-Encoding`.`8bit`,
        `Content-Transfer-Encoding`.Binary,
        `Content-Transfer-Encoding`.QuotedPrintable,
        `Content-Transfer-Encoding`.Base64,
        `Content-Transfer-Encoding`.IetfToken,
        `Content-Transfer-Encoding`.XToken,
      )
    )

  val genObsText: Gen[String] = Gen.stringOf(Gen.choose(0x80.toChar, 0xff.toChar))
  val genVcharExceptDquote: Gen[Char] = genVchar.filter(_ != 0x22.toChar)

  val genEntityTag: Gen[EntityTag] =
    for {
      tag <- Gen.oneOf(genObsText, genVcharExceptDquote.map(_.toString))
      strength <- Gen.oneOf(EntityTag.Weak, EntityTag.Strong)
    } yield EntityTag(tag, strength)

  implicit val http4sTestingArbitraryForIfRangeLastModified: Arbitrary[`If-Range`] = Arbitrary {
    Gen.oneOf(
      genHttpDate.map(`If-Range`.LastModified(_)),
      genEntityTag.map(`If-Range`.ETag(_)),
    )
  }

  implicit val http4sTestingArbitraryTrailer: Arbitrary[Trailer] = Arbitrary(
    nonEmptyListOf(genToken.map(CIString(_))).map(headers =>
      Trailer(NonEmptyList.of(headers.head, headers.tail: _*))
    )
  )
}
