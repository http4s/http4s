package org.http4s

import cats.effect.IO
import java.io._
import java.nio.charset.StandardCharsets

class JVMEntityEncoderSpec extends Http4sSpec with PlatformHttp4sSpec with PlatformEntityDecoderInstances with PlatformEntityEncoderInstances {
  "EntityEncoder" should {
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

  }
}
