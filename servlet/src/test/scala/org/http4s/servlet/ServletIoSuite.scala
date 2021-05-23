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
package servlet

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet._

import cats.effect.IO

class ServletIoSuite extends Http4sSuite {

  test(
    "NonBlockingServletIo should decode request body which is smaller than chunk size correctly") {
    val request =
      HttpServletRequestStub(inputStream = new TestServletInputStream("test".getBytes(UTF_8)))

    val io = NonBlockingServletIo[IO](10)
    val body = io.reader(request)
    body.compile.toList.map(bytes => new String(bytes.toArray, UTF_8)).assertEquals("test")
  }

  test(
    "NonBlockingServletIo should decode request body which is bigger than chunk size correctly") {
    val request = HttpServletRequestStub(inputStream =
      new TestServletInputStream("testtesttest".getBytes(UTF_8)))

    val io = NonBlockingServletIo[IO](10)
    val body = io.reader(request)
    body.compile.toList.map(bytes => new String(bytes.toArray, UTF_8)).assertEquals("testtesttest")
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
      if (in.available() == 0)
        readListener.onAllDataRead()
      result
    }
  }
}
