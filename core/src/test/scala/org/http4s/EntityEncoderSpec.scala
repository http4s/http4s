package org.http4s

import java.io.{StringReader, ByteArrayInputStream, FileWriter, File}
import java.nio.charset.StandardCharsets

import org.specs2.mutable.Specification
import scodec.bits.ByteVector

import scala.concurrent.Future
import scalaz.Rope
import scalaz.concurrent.Task
import scalaz.stream.text.utf8Decode
import scalaz.stream.Process

object EntityEncoderSpec {

  implicit val byteVectorMonoid: scalaz.Monoid[ByteVector] = scalaz.Monoid.instance(_ ++ _, ByteVector.empty)

  def writeToString[A](a: A)(implicit W: EntityEncoder[A]): String =
    Process.eval(W.toEntity(a))
      .collect { case EntityEncoder.Entity(body, _ ) => body }
      .flatMap(identity)
      .fold1Monoid
      .pipe(utf8Decode)
      .runLastOr("")
      .run
}

class EntityEncoderSpec extends Specification with Http4s {
  import EntityEncoderSpec.writeToString

  "EntityEncoder" should {
    "render strings" in {
      writeToString("pong") must_== "pong"
    }

    "calculate the content length of strings" in {
      implicitly[EntityEncoder[String]].toEntity("pong").run.length must_== Some(4)
    }

    "render integers" in {
      writeToString(1) must_== "1"
    }

    "render html" in {
      val html = <html><body>Hello</body></html>
      writeToString(html) must_== "<html><body>Hello</body></html>"
    }

    "render byte arrays" in {
      val hello = "hello"
      writeToString(hello.getBytes(StandardCharsets.UTF_8)) must_== hello
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

    "give the media type" in {
      implicitly[EntityEncoder[String]].contentType must_== Some(MediaType.`text/plain`)
      implicitly[EntityEncoder[ByteVector]].contentType must_== Some(MediaType.`application/octet-stream`)
      implicitly[EntityEncoder[Array[Byte]]].contentType must_== Some(MediaType.`application/octet-stream`)
    }

    "work with local defined EntityEncoders" in {
      import scodec.bits.ByteVector

      case class ModelA(name: String, color: Int)
      case class ModelB(name: String, id: Long)

      implicit val w1: EntityEncoder[ModelA] = EntityEncoder.simple[ModelA](_ => ByteVector.view("A".getBytes))
      implicit val w2: EntityEncoder[ModelB] = EntityEncoder.simple[ModelB](_ => ByteVector.view("B".getBytes))

      implicitly[EntityEncoder[ModelA]] must_== w1
      implicitly[EntityEncoder[ModelB]] must_== w2
    }
  }
}

