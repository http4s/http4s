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

import cats.Eq
import cats.effect._
import org.scalacheck.Arbitrary
import org.scalacheck.Shrink
import org.scalacheck.effect.PropF

trait EntityCodecTests[F[_], A] extends EntityEncoderTests[F, A] {
  def laws: EntityCodecLaws[F, A]

  def entityCodecF(implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      eqA: Eq[A],
  ): List[(String, PropF[F])] = {
    implicit val F: Concurrent[F] = laws.F
    LawAdapter.isEqPropF("roundTrip", laws.entityCodecRoundTrip _) :: entityEncoderF
  }
}

object EntityCodecTests {
  def apply[F[_], A](implicit
      F: Concurrent[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A],
  ): EntityCodecTests[F, A] =
    new EntityCodecTests[F, A] {
      val laws: EntityCodecLaws[F, A] = EntityCodecLaws[F, A]
    }
}
