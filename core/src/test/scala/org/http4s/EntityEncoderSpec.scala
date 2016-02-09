package org.http4s

import java.io.{StringReader, ByteArrayInputStream, FileWriter, File}
import java.nio.charset.StandardCharsets

import org.http4s.EntityEncoder.Entity
import org.http4s.headers.{`Transfer-Encoding`, `Content-Type`}
import org.specs2.mutable.Specification
import scodec.bits.ByteVector

import scala.concurrent.Future
import scalaz.concurrent.Task
import scalaz.stream.text.utf8Decode
import scalaz.stream.Process

import util.byteVector._

object EntityEncoderSpec {
  def writeToString[A](a: A)(implicit W: EntityEncoder[A]): String =
    Process.eval(W.toEntity(a))
      .collect { case EntityEncoder.Entity(body, _ ) => body }
      .flatMap(identity)
      .fold1Monoid
      .pipe(utf8Decode)
      .runLastOr("")
      .run

  def writeToByteVector[A](a: A)(implicit W: EntityEncoder[A]): ByteVector =
    Process.eval(W.toEntity(a))
      .collect { case EntityEncoder.Entity(body, _ ) => body }
      .flatMap(identity)
      .fold1Monoid
      .runLastOr(ByteVector.empty)
      .run
}

class EntityEncoderSpec extends Http4sSpec {
  import EntityEncoderSpec._

  "EntityEncoder" should {
    "render strings" in {
      writeToString("pong") must_== "pong"
    }

    "render single characters" in {
      prop { char: Char => writeToString(char) must_== Character.toString(char) }
    }

    "calculate the content length of strings" in {
      implicitly[EntityEncoder[String]].toEntity("pong").run.length must_== Some(4)
    }

    "render byte arrays" in {
      val hello = "hello"
      writeToString(hello.getBytes(StandardCharsets.UTF_8)) must_== hello
    }

    "render bytes" in {
      prop { byte: Byte => writeToByteVector(byte) must_== ByteVector(byte) }
    }

    "render futures" in {
      import scala.concurrent.ExecutionContext.Implicits.global
      val hello = "Hello"
      writeToString(Future(hello)) must_== hello
    }

    "render Tasks" in {
      val hello = "Hello"
      writeToString(Task.now(hello)) must_== hello
    }

    "render processes" in {
      val helloWorld = Process("hello", "world")
      writeToString(helloWorld) must_== "helloworld"
    }

    "render processes with chunked transfer encoding" in {
      implicitly[EntityEncoder[Process[Task, String]]].headers.get(`Transfer-Encoding`) must beLike {
        case Some(coding) => coding.hasChunked must beTrue
      }
    }

    "render processes with chunked transfer encoding without wiping out other encodings" in {
      trait Foo
      implicit val FooEncoder: EntityEncoder[Foo] =
        EntityEncoder.encodeBy(`Transfer-Encoding`(TransferCoding.gzip)) { _ => Task.now(Entity.empty) }
      implicitly[EntityEncoder[Process[Task, Foo]]].headers.get(`Transfer-Encoding`) must beLike {
        case Some(coding) => coding must_== `Transfer-Encoding`(TransferCoding.gzip, TransferCoding.chunked)
      }
    }

    "render processes with chunked transfer encoding without duplicating chunked transfer encoding" in {
      trait Foo
      implicit val FooEncoder: EntityEncoder[Foo] =
        EntityEncoder.encodeBy(`Transfer-Encoding`(TransferCoding.chunked)) { _ => Task.now(Entity.empty) }
      implicitly[EntityEncoder[Process[Task, Foo]]].headers.get(`Transfer-Encoding`) must beLike {
        case Some(coding) => coding must_== `Transfer-Encoding`(TransferCoding.chunked)
      }
    }

    "render files" in {
      val tmpFile = File.createTempFile("http4s-test-", ".txt")
      try {
        val w = new FileWriter(tmpFile)
        try w.write("render files test")
        finally w.close()
        writeToString(tmpFile) must_== "render files test"
      }
      finally tmpFile.delete()
    }

    "render input streams" in {
      val inputStream = new ByteArrayInputStream(("input stream").getBytes(StandardCharsets.UTF_8))
      writeToString(inputStream) must_== "input stream"
    }

    "render readers" in {
      val reader = new StringReader("string reader")
      writeToString(reader) must_== "string reader"
    }

    "give the content type" in {
      EntityEncoder[String].contentType must_== Some(`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`))
      EntityEncoder[ByteVector].contentType must_== Some(`Content-Type`(MediaType.`application/octet-stream`))
      EntityEncoder[Array[Byte]].contentType must_== Some(`Content-Type`(MediaType.`application/octet-stream`))
    }

    "work with local defined EntityEncoders" in {
      import scodec.bits.ByteVector

      case class ModelA(name: String, color: Int)
      case class ModelB(name: String, id: Long)

      implicit val w1: EntityEncoder[ModelA] = EntityEncoder.simple[ModelA]()(_ => ByteVector.view("A".getBytes))
      implicit val w2: EntityEncoder[ModelB] = EntityEncoder.simple[ModelB]()(_ => ByteVector.view("B".getBytes))

      EntityEncoder[ModelA] must_== w1
      EntityEncoder[ModelB] must_== w2
    }
  }
}

