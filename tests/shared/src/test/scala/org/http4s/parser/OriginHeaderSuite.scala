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
package parser

import cats.data.NonEmptyList
import com.comcast.ip4s._
import org.http4s.headers.Origin
import org.http4s.syntax.all._

class OriginHeaderSuite extends munit.FunSuite {
  private val host1 = Origin.Host(Uri.Scheme.http, Uri.RegName("www.foo.com"), Some(12345))
  private val host2 = Origin.Host(Uri.Scheme.https, Uri.Ipv4Address(ipv4"127.0.0.1"), None)
  private val host3 = Origin.Host(Uri.Scheme.https, Uri.RegName("1bar.foo.com"), None)

  private val hostString1 = "http://www.foo.com:12345"
  private val hostString2 = "https://127.0.0.1"
  private val hostString3 = "https://1bar.foo.com"

  test("Origin value method should Render a host with a port number") {
    val origin: Origin = Origin.HostList(NonEmptyList.of(host1))
    assertEquals(origin.value, hostString1)
  }

  test("Origin value method should Render a host without a port number") {
    val origin: Origin = Origin.HostList(NonEmptyList.of(host2))
    assertEquals(origin.value, hostString2)
  }

  test("Origin value method should Render a list of multiple hosts") {
    val origin: Origin = Origin.HostList(NonEmptyList.of(host1, host2))
    assertEquals(origin.value, s"$hostString1 $hostString2")
  }

  test("Origin value method should Render an empty origin") {
    val origin: Origin = Origin.Null
    assertEquals(origin.value, "null")
  }

  test("OriginHeader parser should Parse a host with a port number") {
    val text = hostString1
    val origin = Origin.HostList(NonEmptyList.of(host1))
    val headers = Headers(("Origin", text))
    val extracted = headers.get[Origin]
    assertEquals(extracted, Some(origin))
  }

  test("OriginHeader parser should Parse a host without a port number") {
    val text = hostString2
    val origin = Origin.HostList(NonEmptyList.of(host2))
    val headers = Headers(("Origin", text))
    val extracted = headers.get[Origin]
    assertEquals(extracted, Some(origin))
  }

  test("OriginHeader parser should Parse a host beginning with a number") {
    val text = hostString3
    val origin = Origin.HostList(NonEmptyList.of(host3))
    val headers = Headers(("Origin", text))
    val extracted = headers.get[Origin]
    assertEquals(extracted, Some(origin))
  }

  test("OriginHeader parser should Parse a list of multiple hosts") {
    val text = s"$hostString1 $hostString2"
    val origin = Origin.HostList(NonEmptyList.of(host1, host2))
    val headers = Headers(("Origin", text))
    val extracted = headers.get[Origin]
    assertEquals(extracted, Some(origin))
  }

  test("OriginHeader parser should Parse a 'null' origin") {
    val text = "null"
    val origin = Origin.Null
    val headers = Headers(("Origin", text))
    val extracted = headers.get[Origin]
    assertEquals(extracted, Some(origin))
  }

  test("OriginHeader should fail on an empty string") {
    val text = ""
    val headers = Headers(("Origin", text))
    val extracted = headers.get[Origin]
    assertEquals(extracted, None)
  }
}
