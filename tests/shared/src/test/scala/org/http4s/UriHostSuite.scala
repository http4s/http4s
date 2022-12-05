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

import cats.kernel.laws.discipline._
import org.http4s.laws.discipline.arbitrary._

final class UriHostSuite extends Http4sSuite {
  checkAll("Order[Uri.Host]", OrderTests[Uri.Host].order)
  checkAll("Hash[Uri.Host]", HashTests[Uri.Host].hash)

  // https://datatracker.ietf.org/doc/html/rfc3986#section-3.2.2

  test("Uri.Host.fromString should parse ALPHA reg-name") {
    val s = "localhost"
    assertEquals(Uri.Host.fromString(s), Right(Uri.RegName(s)))
  }

  test("Uri.Host.fromString should parse DIGIT reg-name") {
    val s = "42"
    assertEquals(Uri.Host.fromString(s), Right(Uri.RegName(s)))
  }

  test("Uri.Host.fromString should parse other unreserved reg-name") {
    val s = "-._~"
    assertEquals(Uri.Host.fromString(s), Right(Uri.RegName(s)))
  }

  test("Uri.Host.fromString should parse pct-encoded reg-name") {
    // https://datatracker.ietf.org/doc/html/rfc3986#section-2.1
    // corresponds to the space character in US-ASCII
    val pctEncoded = "%20"
    assertEquals(Uri.Host.fromString(pctEncoded), Right(Uri.RegName(" ")))
  }

  test("Uri.Host.fromString should parse sub-delims reg-name") {
    val s = "!$&'()*+,;="
    assertEquals(Uri.Host.fromString(s), Right(Uri.RegName(s)))
  }

  test("Uri.Host.fromString should parse a IPv4address") {
    val s = "192.0.2.16"
    assertEquals(Uri.Host.fromString(s), Right(Uri.Ipv4Address.unsafeFromString(s)))
  }

  test("Uri.Host.fromString should parse a IP-literal") {
    val ipv6Address = "2001:db8::7"
    val ipLiteral = s"[$ipv6Address]"
    assertEquals(
      Uri.Host.fromString(ipLiteral),
      Right(Uri.Ipv6Address.unsafeFromString(ipv6Address)),
    )
  }

  test("Uri.Host.fromString should fail to parse gen-delims") {
    val genDelims = ":/?#[]@"
    assert(Uri.Host.fromString(genDelims).isLeft)
  }

  test("Uri.Host.unsafeFromString return direct result") {
    val s = "localhost"
    assertEquals(Uri.Host.unsafeFromString(s), Uri.RegName(s))
  }

  test("Uri.Host.unsafeFromString should throw on bad input") {
    val genDelims = ":/?#[]@"
    intercept[ParseFailure](Uri.Host.unsafeFromString(genDelims))
  }

}
