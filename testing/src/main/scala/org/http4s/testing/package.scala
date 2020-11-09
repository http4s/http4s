/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

// import cats.effect.IO
// // import cats.effect.laws.util.TestContext
// import org.scalacheck.Prop
// import scala.util.Success

package object testing {
  // Media types used for testing
  @deprecated("Will be removed in a future version.", "0.21.0-M2")
  val `text/asp`: MediaType =
    new MediaType("text", "asp", MediaType.Compressible, MediaType.NotBinary, List("asp"))
  @deprecated("Will be removed in a future version.", "0.21.0-M2")
  val `text/x-h` = new MediaType("text", "x-h")
  @deprecated("Will be removed in a future version.", "0.21.0-M2")
  val `application/excel`: MediaType =
    new MediaType("application", "excel", true, false, List("xls"))
  @deprecated("Will be removed in a future version.", "0.21.0-M2")
  val `application/gnutar`: MediaType =
    new MediaType("application", "gnutar", true, false, List("tar"))
  @deprecated("Will be removed in a future version.", "0.21.0-M2")
  val `audio/aiff`: MediaType =
    new MediaType(
      "audio",
      "aiff",
      MediaType.Compressible,
      MediaType.Binary,
      List("aif", "aiff", "aifc"))
  @deprecated("Will be removed in a future version.", "0.21.0-M2")
  val `application/soap+xml`: MediaType =
    new MediaType("application", "soap+xml", MediaType.Compressible, MediaType.NotBinary)
  @deprecated("Will be removed in a future version.", "0.21.0-M2")
  val `audio/mod`: MediaType =
    new MediaType("audio", "mod", MediaType.Uncompressible, MediaType.Binary, List("mod"))

  // @deprecated("Will be removed in a future version. Prefer IsEq[F[Boolean]].", "0.21.0-M2")
  // def ioBooleanToProp(iob: IO[Boolean])(implicit ec: TestContext): Prop = {
  //   val f = iob.unsafeToFuture()
  //   ec.tick()
  //   f.value match {
  //     case Some(Success(true)) => true
  //     case _ => false
  //   }
  // }

  @deprecated("Import from org.http4s.laws.discipline.arbitrary._.", "0.21.0-M2")
  type ArbitraryInstances

  @deprecated("Moved to org.http4s.laws.discipline.arbitrary.", "0.21.0-M2")
  val ArbitraryInstances = org.http4s.laws.discipline.arbitrary

  @deprecated("Moved to org.http4s.laws.discipline.arbitrary.", "0.21.0-M2")
  val instances = org.http4s.laws.discipline.arbitrary

  @deprecated("Moved to org.http4s.laws.EntityEncoderLaws.", "0.21.0-M2")
  type EntityEncoderLaws[F[_], A] = org.http4s.laws.EntityEncoderLaws[F, A]

  @deprecated("Moved to org.http4s.laws.EntityEncoderLaws.", "0.21.0-M2")
  val EntityEncoderLaws = org.http4s.laws.EntityEncoderLaws

  @deprecated("Moved to org.http4s.laws.discipline.EntityEncoderTests.", "0.21.0-M2")
  type EntityEncoderTests[F[_], A] = org.http4s.laws.discipline.EntityEncoderTests[F, A]

  @deprecated("Moved to org.http4s.laws.discipline.EntityEncoderTests.", "0.21.0-M2")
  val EntityEncoderTests = org.http4s.laws.discipline.EntityEncoderTests

  @deprecated("Moved to org.http4s.laws.EntityCodecLaws.", "0.21.0-M2")
  type EntityCodecLaws[F[_], A] = org.http4s.laws.EntityCodecLaws[F, A]

  @deprecated("Moved to org.http4s.laws.EntityCodecLaws.", "0.21.0-M2")
  val EntityCodecLaws = org.http4s.laws.EntityCodecLaws

  @deprecated("Moved to org.http4s.laws.discipline.EntityCodecTests.", "0.21.0-M2")
  type EntityCodecTests[F[_], A] = org.http4s.laws.discipline.EntityCodecTests[F, A]

  @deprecated("Moved to org.http4s.laws.discipline.EntityCodecTests.", "0.21.0-M2")
  val EntityCodecTests = org.http4s.laws.discipline.EntityCodecTests

  @deprecated("Moved to org.http4s.laws.HttpCodecLaws.", "0.21.0-M2")
  type HttpCodecLaws[A] = org.http4s.laws.HttpCodecLaws[A]

  @deprecated("Moved to org.http4s.laws.HttpCodecLaws.", "0.21.0-M2")
  val HttpCodecLaws = org.http4s.laws.EntityCodecLaws

  @deprecated("Moved to org.http4s.laws.discipline.HttpCodecTests.", "0.21.0-M2")
  type HttpCodecTests[A] = org.http4s.laws.discipline.HttpCodecTests[A]

  @deprecated("Moved to org.http4s.laws.discipline.HttpCodecTests.", "0.21.0-M2")
  val HttpCodecTests = org.http4s.laws.discipline.HttpCodecTests
}
