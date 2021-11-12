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

import cats._
import cats.laws.discipline.ContravariantTests
import org.http4s.Uri.Path.Segment
import org.http4s.Uri.Path.SegmentEncoder
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen

import scala.annotation.nowarn

final class SegmentEncoderSuite extends Http4sSuite {
  private val equalityCheckCount: Int = 100

  implicit private def arbitrarySegmentEncoder[A: Cogen]: Arbitrary[SegmentEncoder[A]] = Arbitrary(
    Arbitrary.arbitrary[A => Segment].map(SegmentEncoder.instance)
  )

  @nowarn("cat=deprecation")
  implicit private def eqSegmentEncoder[A](implicit A: Arbitrary[A]): Eq[SegmentEncoder[A]] =
    Eq.instance { (e1, e2) =>
      Stream
        .continually(A.arbitrary.sample)
        .flatten
        .take(equalityCheckCount)
        .forall(a => Eq[Segment].eqv(e1.toSegment(a), e2.toSegment(a)))
    }

  checkAll(
    "Contravariant[SegmentEncoder]",
    ContravariantTests[SegmentEncoder].contravariant[Long, Int, Char],
  )
}
