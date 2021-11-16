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
import cats.Eq
import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.http4s.MediaType
import org.http4s.booPickle.implicits._
import org.http4s.headers.`Content-Type`
import org.http4s.laws.discipline.EntityCodecTests
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

class BoopickleSuite extends Http4sSuite with Http4sLawSuite {
  implicit val testContext: TestContext = TestContext()

  trait Fruit {
    val weight: Double
    def color: String
  }

  private case class Banana(weight: Double) extends Fruit {
    def color = "yellow"
  }

  private case class Kiwi(weight: Double) extends Fruit {
    def color = "brown"
  }

  private case class Carambola(weight: Double) extends Fruit {
    def color = "yellow"
  }

  implicit val fruitPickler: Pickler[Fruit] =
    compositePickler[Fruit].addConcreteType[Banana].addConcreteType[Kiwi].addConcreteType[Carambola]

  implicit val encoder: EntityEncoder[IO, Fruit] = booEncoderOf[IO, Fruit]
  implicit val decoder: EntityDecoder[IO, Fruit] = booOf[IO, Fruit]

  implicit val fruitArbitrary: Arbitrary[Fruit] = Arbitrary {
    for {
      w <- Gen.posNum[Double]
      f <- Gen.oneOf(Banana(w), Kiwi(w), Carambola(w))
    } yield f
  }

  implicit val fruitEq: Eq[Fruit] = Eq.fromUniversalEquals

  test("have octet-stream content type") {
    assertEquals(
      encoder.headers.get[`Content-Type`],
      Some(`Content-Type`(MediaType.application.`octet-stream`)),
    )
  }

  test("have octect-stream content type") {
    assertEquals(
      booEncoderOf[IO, Fruit].headers.get[`Content-Type`],
      Some(`Content-Type`(MediaType.application.`octet-stream`)),
    )
  }

  test("decode a class from a boopickle decoder") {
    val result = booOf[IO, Fruit]
      .decode(Request[IO]().withEntity(Banana(10.0): Fruit), strict = true)
    result.value.map(assertEquals(_, Right(Banana(10.0))))
  }

  checkAllF("EntityCodec[IO, Fruit]", EntityCodecTests[IO, Fruit].entityCodecF)
}
