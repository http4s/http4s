/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package booPickle

import boopickle.Default._
import cats.effect.IO
import cats.Eq
import cats.effect.testkit.TestContext
import org.http4s.headers.`Content-Type`
// import org.http4s.laws.discipline.EntityCodecTests
import org.http4s.MediaType
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

class BoopickleSuite extends Http4sSuite with BooPickleInstances {
  implicit val testContext = TestContext()

  trait Fruit {
    val weight: Double
    def color: String
  }

  case class Banana(weight: Double) extends Fruit {
    def color = "yellow"
  }

  case class Kiwi(weight: Double) extends Fruit {
    def color = "brown"
  }

  case class Carambola(weight: Double) extends Fruit {
    def color = "yellow"
  }

  implicit val fruitPickler: Pickler[Fruit] =
    compositePickler[Fruit].addConcreteType[Banana].addConcreteType[Kiwi].addConcreteType[Carambola]

  implicit val encoder = booEncoderOf[IO, Fruit]
  implicit val decoder = booOf[IO, Fruit]

  implicit val fruitArbitrary: Arbitrary[Fruit] = Arbitrary {
    for {
      w <- Gen.posNum[Double]
      f <- Gen.oneOf(Banana(w), Kiwi(w), Carambola(w))
    } yield f
  }

  implicit val fruitEq: Eq[Fruit] = Eq.fromUniversalEquals

  test("have octet-stream content type") {
    assertEquals(
      encoder.headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.`octet-stream`)))
  }

  test("have octect-stream content type") {
    assertEquals(
      booEncoderOf[IO, Fruit].headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.`octet-stream`)))
  }

  test("decode a class from a boopickle decoder") {
    val result = booOf[IO, Fruit]
      .decode(Request[IO]().withEntity(Banana(10.0): Fruit), strict = true)
    result.value.map(assertEquals(_, Right(Banana(10.0))))
  }

  // checkAll("EntityCodec[IO, Fruit]", EntityCodecTests[IO, Fruit].entityCodec)
}
