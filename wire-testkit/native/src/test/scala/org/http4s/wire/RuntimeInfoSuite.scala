/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.IO
import cats.syntax.all._
import munit.CatsEffectSuite
import scodec.bits.ByteVector

import java.io.IOException
import java.net.SocketException

class RuntimeInfoSuite extends CatsEffectSuite {
  import WireCodecsSuite.peer

  private val resets = Vector(
    "macOS errno" -> new IOException("errno(54) Connection reset by peer"),
    "Linux errno" -> new IOException("errno(104) Connection reset by peer"),
    "socket reset" -> new SocketException("Connection reset"),
    "socket reset by peer" -> new SocketException("Connection reset by peer"),
  )

  test("Native recognizes only the specific connection-reset I/O errors and their causes") {
    resets.foreach { case (_, error) =>
      assert(RuntimeInfo.isConnectionReset(error))
      assert(RuntimeInfo.isConnectionReset(new IOException("read failed", error)))
    }
    Vector(
      new IOException("errno(53) Connection reset by peer"),
      new IOException("errno(54) Connection aborted"),
      new IOException("Connection reset by peer"),
      new IOException("prefix errno(54) Connection reset by peer"),
      new IOException("errno(104) Connection reset by peer suffix"),
      new IOException(null: String),
      new SocketException("Broken pipe"),
      new IllegalStateException("errno(54) Connection reset by peer"),
    ).foreach(error => assert(!RuntimeInfo.isConnectionReset(error)))
  }

  resets.foreach { case (name, error) =>
    test(s"raw reads preserve every buffered byte when accepting $name") {
      val bytes = Http1Wire.ascii("abc")
      for {
        p <- peer(bytes, fragment = 1, whenEmpty = IO.raiseError(error))
        rejected <- p._1.readToEnd().attempt
        received <- p._1.readToEnd(allowReset = true)
        closed <- p._1.readToEnd()
      } yield {
        assertEquals(rejected, Left(error))
        assertEquals(received, bytes)
        assertEquals(closed, ByteVector.empty)
      }
    }

    test(s"HTTP/2 accepts $name only before a frame starts") {
      val frame = Http2Wire.data(1, Http1Wire.ascii("abc"))
      for {
        empty <- peer(ByteVector.empty, whenEmpty = IO.raiseError(error))
          .flatMap(p => Http2Wire.readFrameOrClose(p._1))
        p <- peer(Http2Wire.encode(frame), fragment = 1, whenEmpty = IO.raiseError(error))
        received <- Http2Wire.readFrameOrClose(p._1)
        closed <- Http2Wire.readFrameOrClose(p._1)
      } yield {
        assertEquals(empty, None)
        assertEquals(received, Some(frame))
        assertEquals(closed, None)
      }
    }

    test(s"HTTP/2 rejects truncated frames ending with $name") {
      val header = ByteVector.fromValidHex("000009000000000001")
      Vector(header.take(1), header.take(8), header, header ++ Http1Wire.ascii("abc"))
        .traverse_ { bytes =>
          peer(bytes, fragment = 1, whenEmpty = IO.raiseError(error))
            .flatMap(p => Http2Wire.readFrameOrClose(p._1).attempt)
            .map(result => assertEquals(result, Left(error)))
        }
    }
  }
}
