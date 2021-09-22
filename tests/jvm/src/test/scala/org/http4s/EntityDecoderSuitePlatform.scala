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

import cats.effect._
import cats.syntax.all._
import org.http4s.Status.Ok
import java.io.File
import java.util.Arrays

trait EntityDecoderSuitePlatform { self: EntityDecoderSuite =>
  test("A File EntityDecoder should write a text file from a byte string") {
    Resource
      .make(IO(File.createTempFile("foo", "bar")))(f => IO(f.delete()).void)
      .use { tmpFile =>
        val response = mockServe(Request()) { req =>
          req.decodeWith(EntityDecoder.textFile[IO](tmpFile), strict = false) { _ =>
            Response(Ok).withEntity("Hello").pure[IO]
          }
        }
        response.flatMap { response =>
          assertEquals(response.status, Status.Ok)
          readTextFile(tmpFile).assertEquals(new String(binData)) *>
            response.as[String].assertEquals("Hello")
        }
      }
  }

  test("A File EntityDecoder should write a binary file from a byte string") {
    Resource
      .make(IO(File.createTempFile("foo", "bar")))(f => IO(f.delete()).void)
      .use { tmpFile =>
        val response = mockServe(Request()) { case req =>
          req.decodeWith(EntityDecoder.binFile[IO](tmpFile), strict = false) { _ =>
            Response(Ok).withEntity("Hello").pure[IO]
          }
        }

        response.flatMap { response =>
          assertEquals(response.status, Status.Ok)
          response.body.compile.toVector
            .map(_.toArray)
            .map(Arrays.equals(_, "Hello".getBytes))
            .assert *>
            readFile(tmpFile).map(Arrays.equals(_, binData)).assert
        }
      }
  }

}
