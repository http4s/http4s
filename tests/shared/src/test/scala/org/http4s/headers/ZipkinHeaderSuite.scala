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
package headers

import cats.syntax.all._
import org.http4s.syntax.header._

class ZipkinHeaderSuite extends Http4sSuite with HeaderLaws {
  /* checkAll("X-B3-Sampled", headerLaws(`X-B3-Sampled`))
   * checkAll("X-B3-Flags", headerLaws(`X-B3-Flags`))
   * checkAll("X-B3-TraceId", headerLaws(`X-B3-TraceId`))
   * checkAll("X-B3-SpanId", headerLaws(`X-B3-SpanId`))
   * checkAll("X-B3-ParentSpanId", headerLaws(`X-B3-ParentSpanId`)) */

  import `X-B3-Flags`.Flag
  test("flags no parse when arbitrary string") {
    assert(`X-B3-Flags`.parse("asdf").isLeft)
  }
  test("flags no parse when negative") {
    assert(`X-B3-Flags`.parse("-1").isLeft)
  }

  test("flags parses no flags when 0") {
    val noFlags = "0"
    assertEquals(`X-B3-Flags`.parse(noFlags), Right(`X-B3-Flags`(Set.empty)))
  }
  test("flags parses 'debug' flag") {
    val debugFlag = "1"
    assertEquals(`X-B3-Flags`.parse(debugFlag), Right(`X-B3-Flags`(Set(Flag.Debug))))
  }
  test("flags parses 'sampling set' flag") {
    val samplingSetFlag = "2"
    assertEquals(`X-B3-Flags`.parse(samplingSetFlag), Right(`X-B3-Flags`(Set(Flag.SamplingSet))))
  }
  test("flags parses 'sampled' flag") {
    val sampledFlag = "4"
    assertEquals(`X-B3-Flags`.parse(sampledFlag), Right(`X-B3-Flags`(Set(Flag.Sampled))))
  }
  test("flags parses multiple flags") {
    val sampledAndDebugFlag = "5"
    val result = `X-B3-Flags`.parse(sampledAndDebugFlag)
    assert(result.isRight)
    assert(result.valueOr(throw _).flags.contains(Flag.Sampled))
    assert(result.valueOr(throw _).flags.contains(Flag.Debug))
  }

  test("flags renders when no flags") {
    val result = `X-B3-Flags`(Set.empty).value
    assertEquals(result, "0")
  }
  test("flags renders when one flag") {
    val result = `X-B3-Flags`(Set(Flag.Debug)).value
    assertEquals(result, "1")
  }
  test("flags renders when no flags") {
    val result = `X-B3-Flags`(Set.empty).value
    assertEquals(result, "0")
  }
  test("flags renders when multiple flags") {
    val result = `X-B3-Flags`(Set(Flag.Debug, Flag.Sampled)).value
    assertEquals(result, "5")
  }

  test("sampled parses false when 0") {
    assertEquals(`X-B3-Sampled`.parse("0"), Right(`X-B3-Sampled`(sampled = false)))
  }
  test("sampled parses true when 1") {
    assertEquals(`X-B3-Sampled`.parse("1"), Right(`X-B3-Sampled`(sampled = true)))
  }
  test("sampled no parse when not 0 or 1") {
    assert(`X-B3-Sampled`.parse("01").isLeft)
  }

  // The parsing logic is the same for all ids.
  test("id no parse when less than 16 chars") {
    val not16Chars = "abcd1234"
    assert(`X-B3-TraceId`.parse(not16Chars).isLeft)
  }
  test("id no parse when more than 16 but less than 32 chars") {
    val not16Or32Chars = "abcd1234a"
    assert(`X-B3-TraceId`.parse(not16Or32Chars).isLeft)
  }
  test("id no parse when more than 32 chars") {
    val not16Or32Chars = "2abcd1234a1493b12"
    assert(`X-B3-TraceId`.parse(not16Or32Chars).isLeft)
  }
  test("id no parse when contains non-hex char") {
    val containsZ = "abcd1z34abcd1234"
    assert(`X-B3-TraceId`.parse(containsZ).isLeft)
  }

  test("id parses a Long when contains 16-char case-insensitive hex string") {
    val long = 2159330025234698493L
    val hexString = "1dF77B37a2f310fD"
    assertEquals(`X-B3-TraceId`.parse(hexString), Right(`X-B3-TraceId`(long, None)))
  }
  test("id parses a two Longs when contains 32-char case-insensitive hex string") {
    val msbLong = 2159330025234698493L
    val lsbLong = 7000848103853419616L
    val hexString = "1dF77B37a2f310fD6128024224a66C60"
    assertEquals(`X-B3-TraceId`.parse(hexString), Right(`X-B3-TraceId`(msbLong, Some(lsbLong))))
  }
}
