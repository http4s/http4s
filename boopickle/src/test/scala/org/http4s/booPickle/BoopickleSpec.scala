package org.http4s
package booPickle

import boopickle.Default._
import cats.effect.IO
import cats.Eq
import cats.effect.laws.util.TestContext
import org.http4s.headers.`Content-Type`
import org.http4s.testing.EntityCodecTests
import org.http4s.MediaType
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

class BoopickleSpec extends Http4sSpec with BooPickleInstances {
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

  implicit val testContext = TestContext()

  "boopickle encoder" should {
    "have octet-stream content type" in {
      encoder.headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.application.`octet-stream`))
    }
  }

  "booEncoderOf" should {
    "have octect-stream content type" in {
      booEncoderOf[IO, Fruit].headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.application.`octet-stream`))
    }
  }

  "booOf" should {
    "decode a class from a boopickle decoder" in {
      val result = booOf[IO, Fruit]
        .decode(Request[IO]().withEntity(Banana(10.0): Fruit), strict = true)
      result.value.unsafeRunSync must_== Right(Banana(10.0))
    }
  }

  checkAll("EntityCodec[IO, Fruit]", EntityCodecTests[IO, Fruit].entityCodec)
}
