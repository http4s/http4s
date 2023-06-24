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

class WebSocketFrameDefragmenterSuite extends Http4sSuite {

  import org.http4s.websocket.WebSocketFrameDefragmenter.defragFragment

  test("WebSocketFrameDefragmenter should not do anything to a single frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("text", true),
          Binary(utf8Bytes"binary", true),
          Ping(utf8Bytes"ping"),
          Close(utf8Bytes"close"),
        )
        .through(defragFragment[SyncIO])

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

  test("WebSocketFrameDefragmenter should defrag fragmented Text frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Text("h", false),
          Continuation(utf8Bytes"e", false),
          Continuation(utf8Bytes"l", false),
          Continuation(utf8Bytes"l", false),
          Continuation(utf8Bytes"o", true),
        )
        .through(defragFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("hello", true)
      ),
    )
  }

  test("WebSocketFrameDefragmenter should defrag fragmented Binary frame") {
    val stream: Stream[SyncIO, WebSocketFrame] =
      Stream
        .apply(
          Binary(utf8Bytes"w", false),
          Continuation(utf8Bytes"o", false),
          Continuation(utf8Bytes"r", false),
          Continuation(utf8Bytes"l", false),
          Continuation(utf8Bytes"d", true),
        )
        .through(defragFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Binary(utf8Bytes"world", true)
      ),
    )
  }

  test("WebSocketFrameDefragmenter properly handles both fragmented and single frame") {
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
        .through(defragFragment[SyncIO])

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("Foo", true),
        Text("Bar", true),
        Text("Baz", true),
      ),
    )
  }

  test("WebSocketFrameDefragmenter handles more practical frame streams appropriately") {
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
        .through(defragFragment[SyncIO])

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

  /* The following test is prepared to demonstrate the behavior of invalid
   * websocket frame sequences that are not specified in the Websocket specification.
   * The websocketFrameDefragmenter does not defrag for such sequences.
   */

  test(
    "invalid sequence where a continuation frame with fin flag true is not placed at the end 1"
  ) {
    // A continuation frame with a fin bit true should always be preceded by
    // a frame with a fin bit false. If an invalid sequence such as the following
    // is received, websocketFrameDefragmenter does not do any defragmentation
    // and outputs the invalid sequence as it is.
    val stream: Stream[SyncIO, WebSocketFrame] = Stream
      .apply(
        Text("text1", false),
        Text("text2", true),
      )
      .through(defragFragment)

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("text1", false),
        Text("text2", true),
      ),
    )

  }

  test(
    "invalid sequence where a continuation frame with fin flag true is not placed at the end 2"
  ) {
    // A sequence of fragmented frames should end with a continuation frame
    // with the fin flag true. If an invalid sequence such as the following
    // is received, websocketFrameDefragmenter does not do any defragmentation
    // and outputs the invalid sequence as it is.
    val stream: Stream[SyncIO, WebSocketFrame] = Stream
      .apply(
        Text("text1", false),
        Continuation(utf8Bytes"text2", false),
        Continuation(utf8Bytes"text3", false),
        Close(utf8Bytes"close"),
      )
      .through(defragFragment)

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("text1", false),
        Continuation(utf8Bytes"text2", false),
        Continuation(utf8Bytes"text3", false),
        Close(utf8Bytes"close"),
      ),
    )

  }

  test(
    "invalid sequence where there is no frame with fin bit false before continuation frame with fin bit true"
  ) {
    // A continuation frame with a fin bit true should always be preceded by
    // a frame with a fin bit false. If an invalid sequence such as the following
    // is received, websocketFrameDefragmenter does not do any defragmentation
    // and outputs the invalid sequence as it is.
    val stream: Stream[SyncIO, WebSocketFrame] = Stream
      .apply(
        Text("text1", true),
        Continuation(utf8Bytes"illegal continuation", true),
        Text("text2", true),
      )
      .through(defragFragment)

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("text1", true),
        Continuation(utf8Bytes"illegal continuation", true),
        Text("text2", true),
      ),
    )

  }

  test("The beginning of the sequence is not text or binary") {
    // The first frame of a fragmented sequence should be text or binary.
    // If an invalid sequence such as the following is received,
    // websocketFrameDefragmenter does not do any defragmentation
    // and outputs the invalid sequence as it is.
    val stream: Stream[SyncIO, WebSocketFrame] = Stream
      .apply(
        Continuation(utf8Bytes"h", false),
        Continuation(utf8Bytes"e", false),
        Continuation(utf8Bytes"l", false),
        Continuation(utf8Bytes"l", false),
        Continuation(utf8Bytes"o", true),
      )
      .through(defragFragment)

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Continuation(utf8Bytes"h", false),
        Continuation(utf8Bytes"e", false),
        Continuation(utf8Bytes"l", false),
        Continuation(utf8Bytes"l", false),
        Continuation(utf8Bytes"o", true),
      ),
    )

  }

  test("WebSocketFrameDefragmenter should defrag normal sequence after illegal sequence") {
    val stream: Stream[SyncIO, WebSocketFrame] = Stream
      .apply(
        Text("text1", true),
        // This frame is invalid
        Continuation(utf8Bytes"illegal continuation", true),
        // Sequece after here is valid
        Text("h", false),
        Continuation(utf8Bytes"e", false),
        Continuation(utf8Bytes"l", false),
        Continuation(utf8Bytes"l", false),
        Continuation(utf8Bytes"o", true),
      )
      .through(defragFragment)

    assertEquals(
      stream.compile.toList.unsafeRunSync(),
      List(
        Text("text1", true),
        Continuation(utf8Bytes"illegal continuation", true),
        Text("hello", true),
      ),
    )

  }

}
