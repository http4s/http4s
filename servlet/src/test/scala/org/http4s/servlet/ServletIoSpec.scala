package org.http4s.servlet

import cats.effect.IO
import cats.implicits._
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet._
import javax.servlet.http._
import org.http4s.testing.Http4sAsyncSpec
import org.mockito.Mockito._

class ServletIoSpec extends Http4sAsyncSpec {
  "NonBlockingServletIo" - {
    "decode request body which is smaller than chunk size correctly" in {
      val request = mock(classOf[HttpServletRequest])
      when(request.getInputStream).thenReturn(new TestServletInputStream("test".getBytes(UTF_8)))

      val io = NonBlockingServletIo[IO](10)
      val body = io.reader(request)
      body.compile.toList.asserting { bytes =>
        assert(new String(bytes.toArray, UTF_8) === "test")
      }
    }

    "decode request body which is bigger than chunk size correctly" in {
      val request = mock(classOf[HttpServletRequest])
      when(request.getInputStream)
        .thenReturn(new TestServletInputStream("testtesttest".getBytes(UTF_8)))

      val io = NonBlockingServletIo[IO](10)
      val body = io.reader(request)
      body.compile.toList.asserting { bytes =>
        assert(new String(bytes.toArray, UTF_8) === "testtesttest")
      }
    }
  }

  class TestServletInputStream(body: Array[Byte]) extends ServletInputStream {
    private var readListener: ReadListener = null
    private val in = new ByteArrayInputStream(body)

    override def isReady: Boolean = true

    override def isFinished: Boolean = in.available() == 0

    override def setReadListener(readListener: ReadListener): Unit = {
      this.readListener = readListener
      readListener.onDataAvailable()
    }

    override def read(): Int = {
      val result = in.read()
      if (in.available() == 0) {
        readListener.onAllDataRead()
      }
      result
    }
  }
}
