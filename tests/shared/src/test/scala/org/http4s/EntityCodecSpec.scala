package org.http4s

import cats.Eq
import cats.effect.IO
import cats.effect.laws.util.TestContext
import cats.implicits._
import fs2.Chunk
import org.http4s.testing.EntityCodecTests

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
