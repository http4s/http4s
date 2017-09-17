package org.http4s

import java.io._
import java.nio.charset.StandardCharsets

import cats.effect.IO

class JVMEntityEncoderSpec extends Http4sSpec {
  "EntityEncoder" should {
    "render files" in {
      val tmpFile = File.createTempFile("http4s-test-", ".txt")
      try {
        val w = new FileWriter(tmpFile)
        try w.write("render files test")
        finally w.close()
        writeToString(tmpFile) must_== "render files test"
      } finally {
        tmpFile.delete()
        ()
      }
    }

    "render input streams" in {
      val inputStream = new ByteArrayInputStream("input stream".getBytes(StandardCharsets.UTF_8))
      writeToString(IO(inputStream)) must_== "input stream"
    }

  }
}
