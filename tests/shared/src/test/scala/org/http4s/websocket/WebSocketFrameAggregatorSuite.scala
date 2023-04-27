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

package org.http4s.websocket

import cats.effect.SyncIO
import fs2.Stream
import org.http4s.Http4sSuite
import org.http4s.websocket.WebSocketFrame.Binary
import org.http4s.websocket.WebSocketFrame.Close
import org.http4s.websocket.WebSocketFrame.Continuation
import org.http4s.websocket.WebSocketFrame.Ping
import org.http4s.websocket.WebSocketFrame.Text
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets.UTF_8

class WebSocketFrameAggregatorSuite extends Http4sSuite {

  import org.http4s.websocket.WebSocketFrameAggregator.aggregateFragment

  private def byteVector(str: String) = ByteVector.view(str.getBytes(UTF_8))

  test("WebSocketFrameAggregator should not do anything to a single frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("text", true),
          Binary(byteVector("binary"), true),
          Ping(byteVector("ping")),
          Close(byteVector("close")),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("text", true),
        Binary(byteVector("binary"), true),
        Ping(byteVector("ping")),
        Close(byteVector("close")),
      ),
    )
  }

  test("WebSocketFrameAggregator should aggregate fragmented Text frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("h", false),
          Continuation(byteVector("e"), false),
          Continuation(byteVector("l"), false),
          Continuation(byteVector("l"), false),
          Continuation(byteVector("o"), true),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("hello", true)
      ),
    )
  }

  test("WebSocketFrameAggregator should aggregate fragmented Binary frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Binary(byteVector("w"), false),
          Continuation(byteVector("o"), false),
          Continuation(byteVector("r"), false),
          Continuation(byteVector("l"), false),
          Continuation(byteVector("d"), true),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Binary(byteVector("world"), true)
      ),
    )
  }

  test("WebSocketFrameAggregator properly handles both fragmented and single frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("F", false),
          Continuation(byteVector("o"), false),
          Continuation(byteVector("o"), true),
          Text("Bar", true),
          Text("B", false),
          Continuation(byteVector("a"), false),
          Continuation(byteVector("z"), true),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("Foo", true),
        Text("Bar", true),
        Text("Baz", true),
      ),
    )
  }

  test("WebSocketFrameAggregator handles more practical frame streams appropriately") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("F", false),
          Continuation(byteVector("o"), false),
          Continuation(byteVector("o"), false),
          Continuation(byteVector("o"), true),
          Binary(byteVector("binary"), true),
          Text("some text", true),
          Ping(byteVector("ping")),
          Binary(byteVector("bin"), false),
          Continuation(byteVector("Message"), true),
          Ping(byteVector("ping")),
          Text("B", false),
          Continuation(byteVector("a"), false),
          Continuation(byteVector("r"), true),
          Close(byteVector("close")),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList
        .unsafeRunSync(),
      List(
        Text("Fooo", true),
        Binary(byteVector("binary"), true),
        Text("some text", true),
        Ping(byteVector("ping")),
        Binary(byteVector("binMessage"), true),
        Ping(byteVector("ping")),
        Text("Bar", true),
        Close(byteVector("close")),
      ),
    )
  }

}
