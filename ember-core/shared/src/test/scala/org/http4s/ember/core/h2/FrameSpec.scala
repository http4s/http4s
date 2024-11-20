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

package org.http4s.ember.core.h2

import cats.syntax.all._
import munit.CatsEffectSuite
import scodec.bits._

class H2FrameSpec extends CatsEffectSuite {

  test("RawFrame Should Traverse") {
    val init = H2Frame.RawFrame(
      2,
      H2Frame.Settings.`type`,
      0x01,
      Int.MaxValue,
      ByteVector(0x00, 0x02),
    )
    val bv = H2Frame.RawFrame.toByteVector(init)
    val parsed = H2Frame.RawFrame.fromByteVector(bv).map(_._1)

    assertEquals(parsed, init.some)
  }

  test("RawFrame should return excess data") {
    val init = H2Frame.RawFrame(
      2,
      H2Frame.Settings.`type`,
      0x01,
      Int.MaxValue,
      ByteVector(0x00, 0x02, 0x04),
    )
    val bv = H2Frame.RawFrame.toByteVector(init)
    val parsed = H2Frame.RawFrame.fromByteVector(bv).map(_._2)

    assertEquals(parsed, ByteVector(0x04).some)
  }

  test("Decode A Data H2Frame from Raw") {
    val init = H2Frame.RawFrame(
      2,
      H2Frame.Data.`type`,
      0x01,
      Int.MaxValue,
      ByteVector(0x00, 0x02),
    )
    val bv = H2Frame.RawFrame.toByteVector(init)
    val intermediate = H2Frame.RawFrame.fromByteVector(bv).map(_._1)
    val parsed = intermediate.flatMap(H2Frame.Data.fromRaw(_).toOption)
    val expected = H2Frame.Data(Int.MaxValue, ByteVector(0x00, 0x02), None, endStream = true)

    assertEquals(parsed, expected.some)
  }

  test("Data H2Frame should traverse") {
    val init = H2Frame.Data(4, ByteVector(0x02, 0xa0), None, endStream = false)
    val encoded = H2Frame.Data.toRaw(init)
    val back = H2Frame.Data.fromRaw(encoded)
    assertEquals(back, init.asRight)
  }

  test("Data H2Frame should traverse with padding") {
    val init =
      H2Frame.Data(
        4,
        ByteVector(0x02, 0xa0),
        Some(ByteVector(0xff, 0xff, 0xff, 0xff)),
        endStream = false,
      )
    val encoded = H2Frame.Data.toRaw(init)
    val back = H2Frame.Data.fromRaw(encoded)
    assertEquals(back, init.asRight)
  }

  test("Headers should traverse") {
    val init = H2Frame.Headers(
      7,
      Some(H2Frame.Headers.StreamDependency(exclusive = true, 4, 3)),
      endStream = true,
      endHeaders = true,
      ByteVector(3, 4),
      Some(ByteVector(1)),
    )
    val encoded = H2Frame.Headers.toRaw(init)
    val back = H2Frame.Headers.fromRaw(encoded)
    assertEquals(
      back,
      init.asRight,
    )
  }

  test("Priority should traverse") {
    val init = H2Frame.Priority(5, exclusive = true, 7, 0x01)
    val encoded = H2Frame.Priority.toRaw(init)
    val back = H2Frame.Priority.fromRaw(encoded)
    assertEquals(
      back,
      init.asRight,
    )
  }

  test("RstStream should traverse") {
    val init = H2Frame.RstStream(4, 73)
    val encoded = H2Frame.RstStream.toRaw(init)
    val back = H2Frame.RstStream.fromRaw(encoded)
    assertEquals(
      back,
      init.asRight,
    )
  }

  test("Settings should traverse") {
    val init = H2Frame.Settings(
      0x0,
      ack = false,
      List(
        H2Frame.Settings.SettingsHeaderTableSize(4096),
        H2Frame.Settings.SettingsEnablePush(isEnabled = true),
        H2Frame.Settings.SettingsMaxConcurrentStreams(1024),
        H2Frame.Settings.SettingsInitialWindowSize(65535),
        H2Frame.Settings.SettingsMaxFrameSize(16384),
        H2Frame.Settings.SettingsMaxHeaderListSize(4096),
        H2Frame.Settings.Unknown(14, 56),
      ),
    )
    val encoded = H2Frame.Settings.toRaw(init)
    val back = H2Frame.Settings.fromRaw(encoded)
    assertEquals(
      back,
      init.asRight,
    )
  }

  test("PushPromise should traverse") {
    val init =
      H2Frame.PushPromise(
        74,
        endHeaders = true,
        107,
        ByteVector(0x1, 0xe, 0xb),
        Some(ByteVector(0x0, 0x0, 0x0)),
      )
    val encoded = H2Frame.PushPromise.toRaw(init)
    val back = H2Frame.PushPromise.fromRaw(encoded).toOption
    assertEquals(
      back,
      init.some,
    )
  }

  test("Ping should traverse") {
    val init = H2Frame.Ping.ack
    val encoded = H2Frame.Ping.toRaw(init)
    val back = H2Frame.Ping.fromRaw(encoded).toOption
    assertEquals(
      back,
      init.some,
    )
  }

  test("GoAway should traverse") {
    val init = H2Frame.GoAway(0, 476, Int.MaxValue, Some(ByteVector(0xa)))
    val encoded = H2Frame.GoAway.toRaw(init)
    val back = H2Frame.GoAway.fromRaw(encoded).toOption
    assertEquals(
      back,
      init.some,
    )
  }

  test("WindowUpdate should traverse") {
    val init = H2Frame.WindowUpdate(73, 10210)
    val encoded = H2Frame.WindowUpdate.toRaw(init)
    val back = H2Frame.WindowUpdate.fromRaw(encoded).toOption
    assertEquals(
      back,
      init.some,
    )
  }

  test("Continuation should traverse") {
    val init = H2Frame.Continuation(73, endHeaders = true, ByteVector(0xe, 0x8, 0x3))
    val encoded = H2Frame.Continuation.toRaw(init)
    val back = H2Frame.Continuation.fromRaw(encoded).toOption
    assertEquals(
      back,
      init.some,
    )
  }

}
