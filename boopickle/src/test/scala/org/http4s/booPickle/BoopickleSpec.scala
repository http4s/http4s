/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package booPickle

import boopickle.Default._
import cats.effect.IO
import cats.implicits._
import cats.Eq
import cats.effect.laws.util.TestContext
import cats.effect.laws.util.TestInstances._
import org.http4s.laws.discipline.EntityCodecTests
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

class BoopickleSpec extends Http4sSpec with BooPickleInstances {
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

  checkAll("EntityCodec[IO, Fruit]", EntityCodecTests[IO, Fruit].entityCodec)
}
