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

import java.io.File
import java.io.FileWriter
import cats.effect.IO
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

trait EntityEncoderSpecPlatform { self: EntityEncoderSpec =>

  test("EntityEncoder should render files") {
    val tmpFile = File.createTempFile("http4s-test-", ".txt")
    val w = new FileWriter(tmpFile)
    try w.write("render files test")
    finally w.close()
    writeToString(tmpFile)(EntityEncoder.fileEncoder[IO])
      .guarantee(IO.delay(tmpFile.delete()).void)
      .assertEquals("render files test")

  }

  test("EntityEncoder should render input streams") {
    val inputStream = new ByteArrayInputStream("input stream".getBytes(StandardCharsets.UTF_8))
    writeToString(IO(inputStream))(EntityEncoder.inputStreamEncoder)
      .assertEquals("input stream")
  }

}
