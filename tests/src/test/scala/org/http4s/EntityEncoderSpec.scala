package org.http4s

import cats.Eq
import cats.effect.IO
import cats.implicits._
import cats.laws.discipline.ContravariantTests
import cats.laws.discipline.eq._
import fs2._
import java.io._
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException
import org.http4s.headers._
import org.scalacheck.Arbitrary
import scala.concurrent.duration._

class EntityEncoderSpec extends Http4sSpec {
  "EntityEncoder" should {

    "render streams" in {
      val helloWorld: Stream[IO, String] = Stream("hello", "world")
      writeToString(helloWorld) must_== "helloworld"
    }

    "render streams with chunked transfer encoding" in {
      EntityEncoder[IO, Stream[IO, String]].headers.get(`Transfer-Encoding`) must beLike {
        case Some(coding) => coding.hasChunked must beTrue
      }
    }

    "render streams with chunked transfer encoding without wiping out other encodings" in {
      trait Foo
      implicit val FooEncoder: EntityEncoder[IO, Foo] =
        EntityEncoder.encodeBy[IO, Foo](`Transfer-Encoding`(TransferCoding.gzip))(_ => Entity.empty)
      implicitly[EntityEncoder[IO, Stream[IO, Foo]]].headers.get(`Transfer-Encoding`) must beLike {
        case Some(coding) =>
          coding must_== `Transfer-Encoding`(TransferCoding.gzip, TransferCoding.chunked)
      }
    }

    "render streams with chunked transfer encoding without duplicating chunked transfer encoding" in {
      trait Foo
      implicit val FooEncoder =
        EntityEncoder.encodeBy[IO, Foo](`Transfer-Encoding`(TransferCoding.chunked))(_ =>
          Entity.empty)
      EntityEncoder[IO, Stream[IO, Foo]].headers.get(`Transfer-Encoding`) must beLike {
        case Some(coding) => coding must_== `Transfer-Encoding`(TransferCoding.chunked)
      }
    }

    "render entity bodies with chunked transfer encoding" in {
      EntityEncoder[IO, EntityBody[IO]].headers.get(`Transfer-Encoding`) must beSome(
        `Transfer-Encoding`(TransferCoding.chunked))
    }

    "render files" in {
      val tmpFile = File.createTempFile("http4s-test-", ".txt")
      try {
        val w = new FileWriter(tmpFile)
        try w.write("render files test")
        finally w.close()
        writeToString(tmpFile)(EntityEncoder.fileEncoder(testBlockingExecutionContext)) must_== "render files test"
      } finally {
        tmpFile.delete()
        ()
      }
    }

    "render input streams" in {
      val inputStream = new ByteArrayInputStream("input stream".getBytes(StandardCharsets.UTF_8))
      writeToString(IO(inputStream))(EntityEncoder.inputStreamEncoder(testBlockingExecutionContext)) must_== "input stream"
    }

    "render readers" in {
      val reader = new StringReader("string reader")
      writeToString(IO(reader))(EntityEncoder.readerEncoder(testBlockingExecutionContext)) must_== "string reader"
    }

    "render very long readers" in {
      skipped
      // This tests is very slow. Debugging seems to indicate that the issue is at fs2
      // This is reproducible on input streams
      val longString = "string reader" * 5000
      val reader = new StringReader(longString)
      writeToString[IO[Reader]](IO(reader))(
        EntityEncoder.readerEncoder(testBlockingExecutionContext)) must_== longString
    }

    "render readers with UTF chars" in {
      val utfString = "A" + "\u08ea" + "\u00f1" + "\u72fc" + "C"
      val reader = new StringReader(utfString)
      writeToString[IO[Reader]](IO(reader))(
        EntityEncoder.readerEncoder(testBlockingExecutionContext)) must_== utfString
    }

    "give the content type" in {
      EntityEncoder[IO, String].contentType must beSome(
        `Content-Type`(MediaType.text.plain, Charset.`UTF-8`))
      EntityEncoder[IO, Array[Byte]].contentType must beSome(
        `Content-Type`(MediaType.application.`octet-stream`))
    }

    "work with local defined EntityEncoders" in {
      sealed case class ModelA(name: String, color: Int)
      sealed case class ModelB(name: String, id: Long)

      implicit val w1: EntityEncoder[IO, ModelA] =
        EntityEncoder.simple[IO, ModelA]()(_ => Chunk.bytes("A".getBytes))
      implicit val w2: EntityEncoder[IO, ModelB] =
        EntityEncoder.simple[IO, ModelB]()(_ => Chunk.bytes("B".getBytes))

      EntityEncoder[IO, ModelA] must_== w1
      EntityEncoder[IO, ModelB] must_== w2
    }
  }

  {
    implicit val throwableEq: Eq[Throwable] =
      Eq.fromUniversalEquals

    implicit def entityEq: Eq[IO[Entity[IO]]] =
      Eq.by[IO[Entity[IO]], Either[Throwable, (Option[Long], Vector[Byte])]](
        _.flatMap {
          case Entity(body, length) =>
            body.compile.toVector.map { bytes =>
              (length, bytes)
            }
        }.attempt.unsafeRunTimed(1.second).getOrElse(throw new TimeoutException)
      )

    implicit def entityEncoderEq[A: Arbitrary]: Eq[EntityEncoder[IO, A]] =
      Eq.by[EntityEncoder[IO, A], (Headers, A => IO[Entity[IO]])](enc =>
        (enc.headers, a => IO.pure(enc.toEntity(a))))

    checkAll(
      "Contravariant[EntityEncoder[F, ?]]",
      ContravariantTests[EntityEncoder[IO, ?]].contravariant[Int, Int, Int])
  }
}
