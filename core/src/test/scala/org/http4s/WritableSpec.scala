package org.http4s

import java.io.{FileWriter, File}
import java.nio.charset.StandardCharsets

import org.specs2.mutable.Specification

import scala.concurrent.Future
import scalaz.concurrent.Task
import scalaz.stream.text.utf8Decode
import scalaz.stream.Process

class WritableSpec extends Specification with Http4s {
  "Writable" should {

    def writeString[A](a: A)(implicit w: Writable[A]): String = {
      Process.eval(w.toEntity(a))
        .collect { case Writable.Entity(body, _ ) => body }
        .flatMap(identity)
        .fold1Monoid
        .pipe(utf8Decode)
        .runLastOr("")
        .run
    }

    "render strings" in {
      writeString("pong") must_== "pong"
    }

    "calculate the content length of strings" in {
      implicitly[Writable[String]].toEntity("pong").run.length must_== Some(4)
    }

    "render indexed sequences of strings" in {
      val range = (0 until 10).map(_.toString)
      writeString(range) must_== "0123456789"
    }

    "render ranges of ints" in {
      val range = (0 until 10)
      writeString(range) must_== "0123456789"
    }

    "render integers" in {
      writeString(1) must_== "1"
    }

    "render html" in {
      val html = <html><body>Hello</body></html>
      writeString(html) must_== "<html><body>Hello</body></html>"
    }

    "render byte arrays" in {
      val hello = "hello"
      writeString(hello.getBytes(StandardCharsets.UTF_8)) must_== hello
    }

    "render futures" in {
      import scala.concurrent.ExecutionContext.Implicits.global
      val hello = "Hello"
      writeString(Future(hello)) must_== hello
    }

    "render Tasks" in {
      val hello = "Hello"
      writeString(Task.now(hello)) must_== hello
    }

    "render processes" in {
      val helloWorld = Process("hello", "world")
      writeString(helloWorld) must_== "helloworld"
    }

    "render files" in {
      val tmpFile = File.createTempFile("http4s-test-", ".txt")
      try {
        val w = new FileWriter(tmpFile)
        try w.write("render files test")
        finally w.close()
        writeString(tmpFile) must_== "render files test"
      }
      finally tmpFile.delete()
    }
  }
}

