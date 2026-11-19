/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server.internal

import cats.effect.SyncIO
import fs2.Chunk
import fs2.Stream
import munit._
import org.http4s.websocket.FrameTranscoder
import org.http4s.websocket.WebSocketFrame
import scodec.bits.ByteVector

class WebSocketDecodeFramesSuite extends FunSuite {

  private val maxFrameSize = 64 * 1024 * 1024
  private val helpers = new WebSocketHelpers(maxFrameSize)
  private val clientTranscoder = new FrameTranscoder(isClient = true, maxFrameSize)

  private def maskedClientBytes(frame: WebSocketFrame): Chunk[Byte] =
    Chunk.byteVector(
      clientTranscoder.frameToBuffer(frame).foldLeft(ByteVector.empty)(_ ++ ByteVector.view(_))
    )

  private def decodeAll(
      bytes: Chunk[Byte],
      readSize: Int,
  ): Either[Throwable, List[WebSocketFrame]] =
    Stream
      .chunk(bytes)
      .chunkLimit(readSize)
      .unchunks
      .through(helpers.decodeFrames[SyncIO])
      .handleErrorWith {
        case _: WebSocketHelpers.EndOfStreamError => Stream.empty
        case other => Stream.raiseError[SyncIO](other)
      }
      .compile
      .toList
      .attempt
      .unsafeRunSync()

  test("decodeFrames decodes frames split across single-byte reads") {
    val frames = List(
      WebSocketFrame.Binary(ByteVector.fill(10)(1)),
      WebSocketFrame.Binary(ByteVector.fill(300)(2)),
      WebSocketFrame.Binary(ByteVector.fill(70000)(3)),
    )
    assertEquals(decodeAll(frames.map(maskedClientBytes).reduce(_ ++ _), 1), Right(frames))
  }

  test("decodeFrames decodes multiple frames delivered in one read") {
    val frames =
      List.tabulate(5)(i => WebSocketFrame.Binary(ByteVector.fill(i * 100L + 1)(i.toByte)))
    val bytes = frames.map(maskedClientBytes).reduce(_ ++ _)
    assertEquals(decodeAll(bytes, bytes.size), Right(frames))
  }

  test("decodeFrames decodes a frame boundary that falls inside a read") {
    val frames = List(
      WebSocketFrame.Binary(ByteVector.fill(5000)(1)),
      WebSocketFrame.Binary(ByteVector.fill(5000)(2)),
      WebSocketFrame.Ping(ByteVector.fill(3)(4)),
    )
    assertEquals(decodeAll(frames.map(maskedClientBytes).reduce(_ ++ _), 3000), Right(frames))
  }

  test("decodeFrames decodes a frame at the frame size limit followed by another frame") {
    val small = new WebSocketHelpers(1024)
    val frames = List(
      WebSocketFrame.Binary(ByteVector.fill(1024)(1)),
      WebSocketFrame.Binary(ByteVector.fill(10)(2)),
    )
    val bytes = frames.map(maskedClientBytes).reduce(_ ++ _)
    val result = Stream
      .chunk(bytes)
      .chunkLimit(100)
      .unchunks
      .through(small.decodeFrames[SyncIO])
      .take(2)
      .compile
      .toList
      .unsafeRunSync()
    assertEquals(result, frames)
  }
}
