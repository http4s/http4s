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
import cats.effect.IO
import cats.laws.discipline.{ContravariantTests, ExhaustiveCheck, MiniInt}
import cats.laws.discipline.eq._
import fs2._
import cats.laws.discipline.arbitrary._

import java.io._
import java.nio.charset.StandardCharsets
import org.http4s.headers._
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Arbitrary

class EntityEncoderSpec extends Http4sSuite {
  {
    test("EntityEncoder should render streams") {
      val helloWorld: Stream[IO, String] = Stream("hello", "world")
      writeToString(helloWorld).assertEquals("helloworld")
    }

    test("EntityEncoder should render streams with chunked transfer encoding") {
      EntityEncoder[IO, Stream[IO, String]].headers.get[`Transfer-Encoding`] match {
        case Some(coding: `Transfer-Encoding`) => assert(coding.hasChunked)
        case _ => fail("Match failed")
      }
    }

    test(
      "EntityEncoder should render streams with chunked transfer encoding without wiping out other encodings") {
      trait Foo
      implicit val FooEncoder: EntityEncoder[IO, Foo] =
        EntityEncoder.encodeBy[IO, Foo](`Transfer-Encoding`(TransferCoding.gzip))(_ => Entity.empty)
      implicitly[EntityEncoder[IO, Stream[IO, Foo]]].headers.get[`Transfer-Encoding`] match {
        case Some(coding: `Transfer-Encoding`) =>
          assertEquals(coding, `Transfer-Encoding`(TransferCoding.gzip, TransferCoding.chunked))
        case _ => fail("Match failed")
      }
    }

    test(
      "EntityEncoder should render streams with chunked transfer encoding without duplicating chunked transfer encoding") {
      trait Foo
      implicit val FooEncoder: EntityEncoder[IO, Foo] =
        EntityEncoder.encodeBy[IO, Foo](`Transfer-Encoding`(TransferCoding.chunked))(_ =>
          Entity.empty)
      EntityEncoder[IO, Stream[IO, Foo]].headers.get[`Transfer-Encoding`] match {
        case Some(coding: `Transfer-Encoding`) =>
          assertEquals(coding, `Transfer-Encoding`(TransferCoding.chunked))
        case _ => fail("Match failed")
      }
    }

    test("EntityEncoder should render entity bodies with chunked transfer encoding") {
      assert(
        EntityEncoder[IO, EntityBody[IO]].headers.get[`Transfer-Encoding`] == Some(
          `Transfer-Encoding`(TransferCoding.chunked)))
    }

    test("EntityEncoder should render files") {
      val tmpFile = File.createTempFile("http4s-test-", ".txt")
      val w = new FileWriter(tmpFile)
      try w.write("render files test")
      finally w.close()
      writeToString(tmpFile)(EntityEncoder.fileEncoder(testBlocker))
        .guarantee(IO.delay(tmpFile.delete()).void)
        .assertEquals("render files test")

    }

    test("EntityEncoder should render input streams") {
      val inputStream = new ByteArrayInputStream("input stream".getBytes(StandardCharsets.UTF_8))
      writeToString(IO(inputStream))(EntityEncoder.inputStreamEncoder(testBlocker))
        .assertEquals("input stream")
    }

    test("EntityEncoder should render readers") {
      val reader = new StringReader("string reader")
      writeToString(IO(reader))(EntityEncoder.readerEncoder(testBlocker))
        .assertEquals("string reader")
    }

    test("EntityEncoder should render very long readers".ignore) {
      // This tests is very slow. Debugging seems to indicate that the issue is at fs2
      // This is reproducible on input streams
      val longString = "string reader" * 5000
      val reader = new StringReader(longString)
      writeToString[IO[Reader]](IO(reader))(EntityEncoder.readerEncoder(testBlocker))
        .assertEquals(longString)
    }

    test("EntityEncoder should render readers with UTF chars") {
      val utfString = "A" + "\u08ea" + "\u00f1" + "\u72fc" + "C"
      val reader = new StringReader(utfString)
      writeToString[IO[Reader]](IO(reader))(EntityEncoder.readerEncoder(testBlocker))
        .assertEquals(utfString)
    }

    test("EntityEncoder should give the content type") {
      assertEquals(
        EntityEncoder[IO, String].contentType,
        Some(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`)))
      assertEquals(
        EntityEncoder[IO, Array[Byte]].contentType,
        Some(`Content-Type`(MediaType.application.`octet-stream`)))
    }

    test("EntityEncoder should work with local defined EntityEncoders") {
      sealed case class ModelA(name: String, color: Int)
      sealed case class ModelB(name: String, id: Long)

      implicit val w1: EntityEncoder[IO, ModelA] =
        EntityEncoder.simple[IO, ModelA]()(_ => Chunk.bytes("A".getBytes))
      implicit val w2: EntityEncoder[IO, ModelB] =
        EntityEncoder.simple[IO, ModelB]()(_ => Chunk.bytes("B".getBytes))

      assertEquals(EntityEncoder[IO, ModelA], w1)
      assertEquals(EntityEncoder[IO, ModelB], w2)
    }
  }

  {
    implicit def entityEq: Eq[Entity[Id]] =
      Eq.by[Entity[Id], (Option[Long], Vector[Byte])] { entity =>
        (entity.length, entity.body.compile.toVector)
      }

    implicit def entityEncoderEq[A: ExhaustiveCheck]: Eq[EntityEncoder[Id, A]] =
      Eq.by[EntityEncoder[Id, A], (Headers, A => Entity[Id])] { enc =>
        (enc.headers, enc.toEntity)
      }

    // todo this is needed for scala 2.12, remove once we no longer support it
    implicit def contravariant : Contravariant[EntityEncoder[Id, *]] = EntityEncoder.entityEncoderContravariant[Id]
    implicit def es[A : org.scalacheck.Cogen] : Arbitrary[EntityEncoder[Id, A]] = http4sTestingArbitraryForEntityEncoder[Id, A]

    checkAll(
      "Contravariant[EntityEncoder[F, *]]",
      ContravariantTests[EntityEncoder[Id, *]].contravariant[MiniInt, MiniInt, MiniInt])
  }
}
