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

package org.http4s.testing

import fs2.Chunk
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

/** Arbitraries for fs2 types that aren't ours to publish. */
object fs2Arbitraries {
  implicit val http4sArbitraryForFs2ChunkOfBytes: Arbitrary[Chunk[Byte]] =
    Arbitrary(Gen.containerOf[Array, Byte](arbitrary[Byte]).map(Chunk.array[Byte]))
}
