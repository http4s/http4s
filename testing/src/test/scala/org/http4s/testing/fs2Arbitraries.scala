package org.http4s.testing

import fs2.Chunk
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

/** Arbitraries for fs2 types that aren't ours to publish. */
object fs2Arbitraries {
  implicit val http4sArbitraryForFs2ChunkOfBytes: Arbitrary[Chunk[Byte]] =
    Arbitrary(Gen.containerOf[Array, Byte](arbitrary[Byte]).map(Chunk.bytes))
}
