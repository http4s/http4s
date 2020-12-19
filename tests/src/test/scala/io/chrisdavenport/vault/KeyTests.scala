package io.chrisdavenport.vault

import org.scalacheck._
import cats.effect.SyncIO
import org.specs2.mutable.Specification
import org.typelevel.discipline.specs2.mutable.Discipline
import cats.kernel.laws.discipline.{EqTests, HashTests}

class KeyTests extends Specification with Discipline {

  implicit def functionArbitrary[B, A: Arbitrary]: Arbitrary[B => A] = Arbitrary {
    for {
      a <- Arbitrary.arbitrary[A]
    } yield { (_: B) => a }
  }

  implicit def uniqueKey[A]: Arbitrary[Key[A]] = Arbitrary {
    Arbitrary.arbitrary[Unit].map(_ => Key.newKey[SyncIO, A].unsafeRunSync())
  }

  checkAll("Key", HashTests[Key[Int]].hash)
  checkAll("Key", EqTests[Key[Int]].eqv)
}
