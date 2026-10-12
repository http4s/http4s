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

import munit.FunSuite
import scodec.bits.ByteVector

class Http2RejectionChecksSuite extends FunSuite {
  import Http2Wire._
  import Http2RejectionChecks._

  private def bytes(hex: String): ByteVector = ByteVector.fromValidHex(hex)

  // Literal RFC field encodings do not depend on the control-frame constructors.
  private val vectors = Vector(
    ("settings-empty", Frame(Settings, 0, 0, bytes("")), true, true),
    ("settings-ack", Frame(Settings, Ack, 0, bytes("")), true, true),
    ("settings-undefined-flags", Frame(Settings, 0xfe, 0, bytes("")), true, true),
    ("settings-stream", Frame(Settings, 0, 1, bytes("")), false, false),
    ("settings-short", Frame(Settings, 0, 0, bytes("0001000000")), false, false),
    ("settings-ack-payload", Frame(Settings, Ack, 0, bytes("000100000000")), false, false),
    ("push-zero", Frame(Settings, 0, 0, bytes("000200000000")), true, true),
    ("push-one", Frame(Settings, 0, 0, bytes("000200000001")), false, true),
    ("push-two", Frame(Settings, 0, 0, bytes("000200000002")), false, false),
    (
      "push-invalid-then-valid",
      Frame(Settings, 0, 0, bytes("000200000002000200000000")),
      false,
      false,
    ),
    ("window-setting-zero", Frame(Settings, 0, 0, bytes("000400000000")), true, true),
    ("window-setting-max", Frame(Settings, 0, 0, bytes("00047fffffff")), true, true),
    ("window-setting-overflow", Frame(Settings, 0, 0, bytes("000480000000")), false, false),
    ("frame-size-min", Frame(Settings, 0, 0, bytes("000500004000")), true, true),
    ("frame-size-max", Frame(Settings, 0, 0, bytes("000500ffffff")), true, true),
    ("frame-size-low", Frame(Settings, 0, 0, bytes("000500003fff")), false, false),
    ("frame-size-high", Frame(Settings, 0, 0, bytes("000501000000")), false, false),
    (
      "unbounded-settings",
      Frame(Settings, 0, 0, bytes("0001ffffffff0003ffffffff0006fffffffffffffffffffe")),
      true,
      true,
    ),
    ("ping", Frame(Ping, 0xfe, 0, bytes("0001020304050607")), true, true),
    ("ping-ack", Frame(Ping, 0xff, 0, bytes("0001020304050607")), true, true),
    ("ping-short", Frame(Ping, 0, 0, bytes("00010203040506")), false, false),
    ("ping-long", Frame(Ping, 0, 0, bytes("000102030405060708")), false, false),
    ("ping-stream", Frame(Ping, 0, 1, bytes("0001020304050607")), false, false),
    ("window-connection", Frame(WindowUpdate, 0xff, 0, bytes("00000001")), true, true),
    ("window-stream", Frame(WindowUpdate, 0, 7, bytes("7fffffff")), true, true),
    ("window-reserved-bit", Frame(WindowUpdate, 0, 0, bytes("80000001")), true, true),
    ("window-zero", Frame(WindowUpdate, 0, 0, bytes("00000000")), false, false),
    ("window-reserved-zero", Frame(WindowUpdate, 0, 1, bytes("80000000")), false, false),
    ("window-short", Frame(WindowUpdate, 0, 0, bytes("000001")), false, false),
    ("window-long", Frame(WindowUpdate, 0, 0, bytes("0000000001")), false, false),
    ("priority", Frame(Priority, 0xff, 1, bytes("0000000010")), true, true),
    ("priority-self-dependency", Frame(Priority, 0, 3, bytes("8000000300")), true, true),
    ("priority-zero-stream", Frame(Priority, 0, 0, bytes("0000000010")), false, false),
    ("priority-short", Frame(Priority, 0, 1, bytes("00000000")), false, false),
    ("priority-long", Frame(Priority, 0, 1, bytes("000000001000")), false, false),
    ("reset-no-error", Frame(RstStream, 0xff, 1, bytes("00000000")), true, true),
    ("reset-unknown-code", Frame(RstStream, 0, 9, bytes("ffffffff")), true, true),
    ("reset-zero-stream", Frame(RstStream, 0, 0, bytes("00000001")), false, false),
    ("reset-short", Frame(RstStream, 0, 1, bytes("000001")), false, false),
    ("reset-long", Frame(RstStream, 0, 1, bytes("0000000001")), false, false),
    ("goaway", Frame(GoAway, 0xff, 0, bytes("0000000300000000")), true, false),
    (
      "goaway-reserved-bit-debug",
      Frame(GoAway, 0, 0, bytes("8000000300000009aabbcc")),
      true,
      false,
    ),
    ("goaway-unknown-code", Frame(GoAway, 0, 0, bytes("00000003ffffffff")), true, false),
    ("goaway-stream", Frame(GoAway, 0, 1, bytes("0000000300000009")), false, false),
    ("goaway-short", Frame(GoAway, 0, 0, bytes("00000003000009")), false, false),
    ("extension", Frame(255, 255, 17, bytes("abcdef")), true, true),
  )

  for {
    (name, frame, serverValid, clientValid) <- vectors
    fromServer <- Vector(false, true)
  } test(s"control envelope: $name/fromServer=$fromServer") {
    val result = validate(frame, fromServer)
    assertEquals(result.isRight, if (fromServer) serverValid else clientValid)
    result.left.foreach(error => assert(error.isInstanceOf[ProtocolFailure]))
  }

  for (fromServer <- Vector(false, true)) {
    test(s"GOAWAY masked identifiers may stay equal or decrease: fromServer=$fromServer") {
      val sequence =
        if (fromServer)
          Vector("ffffffff00000000", "8000000300000000", "0000000300000001", "0000000000000009")
        else Vector("fffffffe00000000", "8000000200000000", "0000000200000001", "0000000000000009")
      val result = sequence.foldLeft[Either[ProtocolFailure, State]](Right(State())) {
        (state, payload) =>
          state.flatMap(validate(Frame(GoAway, 0, 0, bytes(payload)), fromServer, _))
      }
      assertEquals(result, Right(State(Some(0L))))
    }
    test(
      s"GOAWAY identifier must not increase across incidental controls: fromServer=$fromServer"
    ) {
      val result = for {
        first <- validate(
          Frame(GoAway, 0, 0, uint32(if (fromServer) 3L else 2L) ++ uint32(0L)),
          fromServer,
        )
        middle <- validate(Frame(Priority, 0, 5, bytes("0000000500")), fromServer, first)
        last <- validate(
          Frame(GoAway, 0, 0, uint32(if (fromServer) 5L else 4L) ++ uint32(1L)),
          fromServer,
          middle,
        )
      } yield last
      assert(result.left.exists(_.getMessage.contains("increased")))
    }
  }
  for {
    fromServer <- Vector(false, true)
    last <- Vector(0L, 1L, 2L, 3L, 4L, 0x7fffffffL, 0x80000000L, 0x80000001L, 0x80000002L)
  } test(s"observer boundaries GOAWAY initiator: $fromServer/$last") {
    val frame = Frame(GoAway, 0, 0, uint32(last) ++ uint32(0L))
    val masked = last & 0x7fffffffL
    val valid = masked == 0L || ((masked & 1L) == (if (fromServer) 1L else 0L))
    assertEquals(validate(frame, fromServer).isRight, valid)
  }

}
