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
