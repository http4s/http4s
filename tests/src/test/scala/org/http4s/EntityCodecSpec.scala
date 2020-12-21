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

import cats.Eq
import cats.effect.IO
import cats.effect.laws.util.TestContext
import cats.effect.laws.util.TestInstances._
import fs2.Chunk
import org.http4s.laws.discipline.EntityCodecTests
import org.http4s.testing.fs2Arbitraries._

class EntityCodecSpec extends Http4sSpec {
  implicit val testContext: TestContext = TestContext()

  implicit def eqArray[A](implicit ev: Eq[Vector[A]]): Eq[Array[A]] =
    Eq.by(_.toVector)

  implicit def eqChunk[A](implicit ev: Eq[Vector[A]]): Eq[Chunk[A]] =
    Eq.by(_.toVector)

  checkAll("EntityCodec[IO, String]", EntityCodecTests[IO, String].entityCodec)
  checkAll("EntityCodec[IO, Array[Char]]", EntityCodecTests[IO, Array[Char]].entityCodec)

  checkAll("EntityCodec[IO, Chunk[Byte]]", EntityCodecTests[IO, Chunk[Byte]].entityCodec)
  checkAll("EntityCodec[IO, Array[Byte]]", EntityCodecTests[IO, Array[Byte]].entityCodec)

  checkAll("EntityCodec[IO, Unit]", EntityCodecTests[IO, Unit].entityCodec)
}
