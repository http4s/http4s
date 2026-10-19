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

import org.http4s.laws.discipline.arbitrary
import org.scalacheck._
import scodec.bits.ByteVector

class StructuredFieldSuite extends Http4sSuite {

  import StructuredField._

  private val genSfInteger: Gen[String] =
    for {
      s <- Gen.oneOf("-", "")
      n <- Gen.resize(15, Gen.nonEmptyListOf(Gen.numChar)).map(_.mkString)
    } yield s"${s}${n}"

  private val genSfDecimal: Gen[String] =
    for {
      s <- Gen.oneOf("-", "")
      n <- Gen.resize(12, Gen.nonEmptyListOf(Gen.numChar)).map(_.mkString)
      d <- Gen.resize(3, Gen.nonEmptyListOf(Gen.numChar)).map(_.mkString)
    } yield s"${s}${n}.${d}"

  private val genSfString: Gen[String] = {
    val unescaped = Gen
      .oneOf(
        (0x20.toChar to 0x21.toChar) ++ (0x23.toChar to 0x5b.toChar) ++ (0x5d.toChar to 0x7e.toChar)
      )
      .map(_.toString)
    val escaped = Gen.oneOf("\\\"", "\\\\")
    Gen.listOf(Gen.frequency((16, unescaped), (1, escaped))).map(xs => s""""${xs.mkString}"""")
  }

  private val genSfStringUnescaped: Gen[String] =
    Gen.stringOf(Gen.oneOf(0x20.toChar to 0x21.toChar))

  private val genSfToken: Gen[String] =
    for {
      h <- Gen.oneOf(Gen.alphaChar, Gen.const('*'))
      t <- Gen.listOf(Gen.frequency((16, arbitrary.genTchar), (1, Gen.oneOf(':', '/'))))
    } yield (h +: t).mkString

  private val genSfBinary: Gen[String] =
    Gen
      .listOf(Gen.alphaNumChar)
      .map(_.mkString)
      .map(s => ByteVector(s.getBytes("UTF-8")).toBase64)
      .map(s => s":$s:")

  private val genSfBoolean: Gen[String] =
    Gen.oneOf("?0", "?1")

  private val genBareItem: Gen[String] =
    Gen.oneOf(genSfInteger, genSfDecimal, genSfString, genSfToken, genSfBinary, genSfBoolean)

  private val genKey: Gen[String] =
    for {
      h <- Gen.oneOf(Gen.alphaLowerChar, Gen.const('*'))
      t <- Gen.listOf(Gen.oneOf(Gen.alphaLowerChar, Gen.numChar, Gen.oneOf('_', '-', '.', '*')))
    } yield (h +: t).mkString

  private val genParameter: Gen[String] =
    for {
      k <- genKey
      s <- Gen.stringOf(' ')
      v <- Gen.oneOf(genBareItem.map(i => s"=$i"), Gen.const(""))
    } yield s";${s}${k}${v}"

  private val genParameters: Gen[String] =
    Gen.resize(16, Gen.listOf(genParameter)).map(_.mkString)

  private val genSfItem: Gen[String] =
    for {
      b <- genBareItem
      p <- genParameters
    } yield s"${b}${p}"

  private val genInnerList: Gen[String] =
    for {
      pr <- genParameters
      ss <- Gen.resize(8, Gen.stringOf(' '))
      sp <- Gen.resize(8, Gen.nonEmptyListOf(' ')).map(_.mkString)
      ls <- Gen.resize(16, Gen.listOf(genSfItem)).map(_.mkString(ss, sp, ss))
    } yield s"($ls)$pr"

  private val genMember: Gen[String] =
    Gen.oneOf(genSfItem, genInnerList)

  private val genOws: Gen[String] =
    Gen.listOf(Gen.oneOf(" \t".toList)).map(_.mkString)

  private val genSfList: Gen[String] =
    for {
      s <- genOws
      h <- genMember
      t <- Gen.resize(16, Gen.listOf(genMember))
    } yield (h +: t).mkString(s",${s}")

  private val genDictMember: Gen[String] =
    for {
      k <- genKey
      v <- Gen.oneOf(genMember.map(m => s"=$m"), genParameters)
    } yield s"${k}${v}"

  private val genSfDictionary: Gen[String] =
    for {
      s <- genOws
      h <- genDictMember
      t <- Gen.resize(16, Gen.listOf(genDictMember))
    } yield (h +: t).mkString(s",${s}")

  /* SfInteger */

  test("SfInteger.parser should parse valid strings") {
    Prop.forAll(genSfInteger) { s =>
      SfInteger.parser.parseAll(s) match {
        case Right(SfInteger(v)) => v == s.toLong
        case _ => false
      }
    }
  }

  test("SfInteger.parser should fail with invalid strings") {
    assert(SfInteger.parser.parseAll("123abc").isLeft)
  }

  test("SfInteger.render should render values correctly") {
    Prop.forAll(genSfInteger) { s =>
      SfInteger.parser.parseAll(s) match {
        case Right(i1) =>
          SfInteger.parser.parseAll(i1.renderString) match {
            case Right(i2) => i1 == i2
            case _ => false
          }
        case _ => false
      }
    }
  }

  test("SfInteger.fromLong should accept valid values") {
    Prop.forAll(genSfInteger) { s =>
      SfInteger.fromLong(s.toLong) match {
        case Some(SfInteger(_)) => true
        case _ => false
      }
    }
  }

  test("SfInteger.fromLong should fail with invalid values") {
    assertEquals(SfInteger.fromLong(1000000000000000L), None)
  }

  test("SfInteger.fromInt should create values correctly") {
    assertEquals(SfInteger.fromInt(999).value, 999L)
  }

  /* SfDecimal */

  test("SfDecimal.parser should parse valid strings") {
    Prop.forAll(genSfDecimal) { s =>
      SfDecimal.parser.parseAll(s) match {
        case Right(SfDecimal(v)) => v == BigDecimal(s)
        case _ => false
      }
    }
  }

  test("SfDecimal.parser should fail with invalid strings") {
    assert(SfDecimal.parser.parseAll("123abc").isLeft)
  }

  test("SfDecimal.parser should fail with non decimal numbers") {
    assert(SfDecimal.parser.parseAll("123").isLeft)
  }

  test("SfDecimal.render should render values correctly") {
    Prop.forAll(genSfDecimal) { s =>
      SfDecimal.parser.parseAll(s) match {
        case Right(i1) =>
          SfDecimal.parser.parseAll(i1.renderString) match {
            case Right(i2) => i1 == i2
            case _ => false
          }
        case _ => false
      }
    }
  }

  test("SfDecimal.fromBigDecimal should accept valid values") {
    Prop.forAll(genSfDecimal) { s =>
      SfDecimal.fromBigDecimal(BigDecimal(s)) match {
        case Some(SfDecimal(_)) => true
        case _ => false
      }
    }
  }

  test("SfDecimal.fromBigDecimal should fail with invalid values") {
    assertEquals(SfDecimal.fromBigDecimal(BigDecimal("1000000000000.1")), None)
  }

  test("SfDecimal.render should scale numbers correctly") {
    assertEquals(
      SfDecimal.fromBigDecimal(BigDecimal("99.998765")).map(_.renderString),
      Some("99.999"),
    )
    assertEquals(SfDecimal.fromBigDecimal(BigDecimal("99.99")).map(_.renderString), Some("99.99"))
    assertEquals(SfDecimal.fromBigDecimal(BigDecimal("99")).map(_.renderString), Some("99.0"))
  }

  /* SfString */

  test("SfString.parser should parse valid strings") {
    Prop.forAll(genSfString) { s =>
      SfString.parser.parseAll(s) match {
        case Right(SfString(_)) => true
        case _ => false
      }
    }
  }

  test("SfString.parser should fail with invalid strings") {
    assert(SfString.parser.parseAll("123abc").isLeft)
  }

  test("SfString.render should render values correctly") {
    Prop.forAll(genSfString) { s =>
      SfString.parser.parseAll(s) match {
        case Right(s1) =>
          SfString.parser.parseAll(s1.renderString) match {
            case Right(s2) => s1 == s2
            case _ => false
          }
        case _ => false
      }
    }
  }

  test("SfString.fromString should accept valid unescaped strings") {
    Prop.forAll(genSfStringUnescaped) { s =>
      SfString.fromString(s) match {
        case Some(s1) =>
          SfString.parser.parseAll(s1.renderString) match {
            case Right(s2) => s1 == s2
            case _ => false
          }
        case _ => false
      }
    }
  }

  test("SfString.fromString should fail with invalid strings") {
    assertEquals(SfString.fromString("123\tabc"), None)
  }

  /* SfToken */

  test("SfToken.parser should parse valid strings") {
    Prop.forAll(genSfToken) { s =>
      SfToken.parser.parseAll(s) match {
        case Right(SfToken(_)) => true
        case _ => false
      }
    }
  }

  test("SfToken.parser should fail with invalid strings") {
    assert(SfToken.parser.parseAll("123abc").isLeft)
  }

  test("SfToken.render should render values correctly") {
    Prop.forAll(genSfToken) { s =>
      SfToken.parser.parseAll(s) match {
        case Right(t1) =>
          SfToken.parser.parseAll(t1.renderString) match {
            case Right(t2) => t1 == t2
            case _ => false
          }
        case _ => false
      }
    }
  }

  test("SfToken.fromString should accept valid strings") {
    Prop.forAll(genSfToken) { s =>
      SfToken.fromString(s) match {
        case Some(SfToken(_)) => true
        case _ => false
      }
    }
  }

  test("SfToken.fromString should fail with invalid strings") {
    assertEquals(SfToken.fromString("123abc"), None)
  }

  /* SfBinary */

  test("SfBinary.parser should parse valid strings") {
    Prop.forAll(genSfBinary) { s =>
      SfBinary.parser.parseAll(s) match {
        case Right(SfBinary(_)) => true
        case _ => false
      }
    }
  }

  test("SfBinary.parser should fail with invalid strings") {
    assert(SfBinary.parser.parseAll("123abc").isLeft)
  }

  test("SfBinary.parser should fail with invalid base64") {
    assert(SfBinary.parser.parseAll(":F==:").isLeft)
  }

  test("SfBinary.render should render values correctly") {
    Prop.forAll(genSfBinary) { s =>
      SfBinary.parser.parseAll(s) match {
        case Right(b1) =>
          SfBinary.parser.parseAll(b1.renderString) match {
            case Right(b2) => b1 == b2
            case _ => false
          }
        case _ => false
      }
    }
  }

  /* SfBoolean */

  test("SfBoolean.parser should parse valid strings") {
    assertEquals(SfBoolean.parser.parseAll("?0"), Right(SfBoolean(false)))
    assertEquals(SfBoolean.parser.parseAll("?1"), Right(SfBoolean(true)))
  }

  test("SfBoolean.parser should fail with invalid strings") {
    assert(SfBoolean.parser.parseAll("123abc").isLeft)
  }

  test("SfBoolean.render should render values correctly") {
    assertEquals(SfBoolean(false).renderString, "?0")
    assertEquals(SfBoolean(true).renderString, "?1")
  }

  /* BareItem */

  test("BareItem.parser should parse SfInteger") {
    Prop.forAll(genSfInteger) { s =>
      BareItem.parser.parseAll(s) match {
        case Right(SfInteger(_)) => true
        case _ => false
      }
    }
  }

  test("BareItem.parser should parse SfDecimal") {
    Prop.forAll(genSfDecimal) { s =>
      BareItem.parser.parseAll(s) match {
        case Right(SfDecimal(_)) => true
        case _ => false
      }
    }
  }

  test("BareItem.parser should parse SfString") {
    Prop.forAll(genSfString) { s =>
      BareItem.parser.parseAll(s) match {
        case Right(SfString(_)) => true
        case _ => false
      }
    }
  }

  test("BareItem.parser should parse SfToken") {
    Prop.forAll(genSfToken) { s =>
      BareItem.parser.parseAll(s) match {
        case Right(SfToken(_)) => true
        case _ => false
      }
    }
  }

  test("BareItem.parser should parse SfBinary") {
    Prop.forAll(genSfBinary) { s =>
      BareItem.parser.parseAll(s) match {
        case Right(SfBinary(_)) => true
        case _ => false
      }
    }
  }

  test("BareItem.parser should parse SfBoolean") {
    Prop.forAll(genSfBoolean) { s =>
      BareItem.parser.parseAll(s) match {
        case Right(SfBoolean(_)) => true
        case _ => false
      }
    }
  }

  /* Key */

  test("Key.parser should parse valid strings") {
    Prop.forAll(genKey) { s =>
      Key.parser.parseAll(s) match {
        case Right(Key(_)) => true
        case _ => false
      }
    }
  }

  test("Key.parser should fail with invalid strings") {
    assert(Key.parser.parseAll("invalid-Key").isLeft)
  }

  test("Key.render should render values correctly") {
    Prop.forAll(genKey) { s =>
      Key.parser.parseAll(s) match {
        case Right(k1) =>
          Key.parser.parseAll(k1.renderString) match {
            case Right(k2) => k1 == k2
            case _ => false
          }
        case _ => false
      }
    }
  }

  test("Key.fromString should accept valid strings") {
    Prop.forAll(genKey) { s =>
      Key.fromString(s) match {
        case Some(Key(_)) => true
        case _ => false
      }
    }
  }

  test("Key.fromString should fail with invalid strings") {
    assertEquals(Key.fromString("invalid-Key"), None)
  }

  /* Parameters */

  test("Parameters.parser should parse valid strings") {
    Prop.forAll(genParameters) { s =>
      Parameters.parser.parseAll(s) match {
        case Right(Parameters(_)) => true
        case _ => false
      }
    }
  }

  test("Parameters.parser should parse empty values as a boolean true") {
    assert {
      Parameters.parser.parseAll(";p1;p2=?1") match {
        case Right(Parameters(List((_, SfBoolean(true)), (_, SfBoolean(true))))) => true
        case _ => false
      }
    }
  }

  test("Parameters.render should render values correctly") {
    Prop.forAll(genParameters) { s =>
      Parameters.parser.parseAll(s) match {
        case Right(p1) =>
          Parameters.parser.parseAll(p1.renderString) match {
            case Right(p2) => p1 == p2
            case _ => false
          }
        case _ => false
      }
    }
  }

  /* SfItem */

  test("SfItem.parser should parse valid strings") {
    Prop.forAll(genSfItem) { s =>
      SfItem.parser.parseAll(s) match {
        case Right(SfItem(_, _)) => true
        case _ => false
      }
    }
  }

  test("SfItem.render should render values correctly") {
    Prop.forAll(genSfItem) { s =>
      SfItem.parser.parseAll(s) match {
        case Right(i1) =>
          SfItem.parser.parseAll(i1.renderString) match {
            case Right(i2) => i1 == i2
            case _ => false
          }
        case _ => false
      }
    }
  }

  /* InnerList */

  test("InnerList.parser should parse valid strings") {
    Prop.forAll(genInnerList) { s =>
      InnerList.parser.parseAll(s) match {
        case Right(InnerList(_, _)) => true
        case _ => false
      }
    }
  }

  test("InnerList.render should render values correctly") {
    Prop.forAll(genInnerList) { s =>
      InnerList.parser.parseAll(s) match {
        case Right(l1) =>
          InnerList.parser.parseAll(l1.renderString) match {
            case Right(l2) => l1 == l2
            case _ => false
          }
        case _ => false
      }
    }
  }

  /* Member */

  test("Member.parser should parse SfItem") {
    Prop.forAll(genSfItem) { s =>
      Member.parser.parseAll(s) match {
        case Right(SfItem(_, _)) => true
        case _ => false
      }
    }
  }

  test("Member.parser should parse InnerList") {
    Prop.forAll(genInnerList) { s =>
      Member.parser.parseAll(s) match {
        case Right(InnerList(_, _)) => true
        case _ => false
      }
    }
  }

  /* SfList */

  test("SfList.parser should parse valid strings") {
    Prop.forAll(genSfList) { s =>
      SfList.parser.parseAll(s) match {
        case Right(SfList(_)) => true
        case _ => false
      }
    }
  }

  test("SfList.render should render values correctly") {
    Prop.forAll(genSfList) { s =>
      SfList.parser.parseAll(s) match {
        case Right(l1) =>
          SfList.parser.parseAll(l1.renderString) match {
            case Right(l2) => l1 == l2
            case _ => false
          }
        case _ => false
      }
    }
  }

  /* SfDictionary */

  test("SfDictionary.parser should parse valid strings") {
    Prop.forAll(genSfDictionary) { s =>
      SfDictionary.parser.parseAll(s) match {
        case Right(SfDictionary(_)) => true
        case _ => false
      }
    }
  }

  test("SfDictionary.parser should parse empty values as a boolean true") {
    assert {
      SfDictionary.parser.parseAll("k1;p1=1, k2=?1;p2=2") match {
        case Right(
              SfDictionary(List((_, SfItem(SfBoolean(true), _)), (_, SfItem(SfBoolean(true), _))))
            ) =>
          true
        case _ => false
      }
    }
  }

  test("SfDictionary.parser should render values correctly") {
    Prop.forAll(genSfDictionary) { s =>
      SfDictionary.parser.parseAll(s) match {
        case Right(d1) =>
          SfDictionary.parser.parseAll(d1.renderString) match {
            case Right(d2) => d1 == d2
            case _ => false
          }
        case _ => false
      }
    }
  }
}
