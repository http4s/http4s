/*
 * Copyright 2018 http4s.org
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
package booPickle

import boopickle.Default._
import cats.effect.IO
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
