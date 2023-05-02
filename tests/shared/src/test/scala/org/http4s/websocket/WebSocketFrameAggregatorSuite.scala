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
import scodec.bits._

class WebSocketFrameAggregatorSuite extends Http4sSuite {

  import org.http4s.websocket.WebSocketFrameAggregator.aggregateFragment

  test("WebSocketFrameAggregator should not do anything to a single frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("text", true),
          Binary(utf8Bytes"binary", true),
          Ping(utf8Bytes"ping"),
          Close(utf8Bytes"close"),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("text", true),
        Binary(utf8Bytes"binary", true),
        Ping(utf8Bytes"ping"),
        Close(utf8Bytes"close"),
      ),
    )
  }

  test("WebSocketFrameAggregator should aggregate fragmented Text frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("h", false),
          Continuation(utf8Bytes"e", false),
          Continuation(utf8Bytes"l", false),
          Continuation(utf8Bytes"l", false),
          Continuation(utf8Bytes"o", true),
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
          Binary(utf8Bytes"w", false),
          Continuation(utf8Bytes"o", false),
          Continuation(utf8Bytes"r", false),
          Continuation(utf8Bytes"l", false),
          Continuation(utf8Bytes"d", true),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Binary(utf8Bytes"world", true)
      ),
    )
  }

  test("WebSocketFrameAggregator properly handles both fragmented and single frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("F", false),
          Continuation(utf8Bytes"o", false),
          Continuation(utf8Bytes"o", true),
          Text("Bar", true),
          Text("B", false),
          Continuation(utf8Bytes"a", false),
          Continuation(utf8Bytes"z", true),
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
          Continuation(utf8Bytes"o", false),
          Continuation(utf8Bytes"o", false),
          Continuation(utf8Bytes"o", true),
          Binary(utf8Bytes"binary", true),
          Text("some text", true),
          Ping(utf8Bytes"ping"),
          Binary(utf8Bytes"bin", false),
          Continuation(utf8Bytes"Message", true),
          Ping(utf8Bytes"ping"),
          Text("B", false),
          Continuation(utf8Bytes"a", false),
          Continuation(utf8Bytes"r", true),
          Close(utf8Bytes"close"),
        )
        .through(aggregateFragment[SyncIO])

    assertEquals(
      stream.compile.toList
        .unsafeRunSync(),
      List(
        Text("Fooo", true),
        Binary(utf8Bytes"binary", true),
        Text("some text", true),
        Ping(utf8Bytes"ping"),
        Binary(utf8Bytes"binMessage", true),
        Ping(utf8Bytes"ping"),
        Text("Bar", true),
        Close(utf8Bytes"close"),
      ),
    )
  }

}
