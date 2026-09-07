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

package org.http4s.headers

import cats.data.NonEmptyList
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId}
import org.http4s.Uri
import org.http4s.headers.`Alt-Svc`.{AltAuthority, AltService, ProtocolId, Value}
import org.http4s.headers.`Alt-Svc`.Value.{AltValue, Clear}
import org.http4s.syntax.header._
import org.scalacheck.{Arbitrary, Gen}
import org.typelevel.ci.CIString

class AltSvcSuite extends HeaderLaws {

  private val genProtocolId: Gen[ProtocolId] =
    Gen.oneOf(
      ProtocolId.`http/1.1`,
      ProtocolId.h2,
      ProtocolId.h2c,
      ProtocolId.h3,
      ProtocolId.`h3-25`,
      ProtocolId.`h3-29`,
    )

  // Kept to alphanumerics: percent-encoding and IPv6-bracket round-tripping are covered by
  // dedicated example tests below rather than the property check.
  private val genRegName: Gen[Uri.Host] =
    Gen.identifier.map(s => Uri.RegName(CIString(s)))

  private val genAltAuthority: Gen[AltAuthority] =
    for {
      host <- Gen.option(genRegName)
      port <- Gen.choose(0, 65535)
    } yield AltAuthority(host, port)

  private val genAltService: Gen[AltService] =
    for {
      protocolId <- genProtocolId
      authority <- genAltAuthority
      maxAge <- Gen.option(Gen.choose(0L, 1000000L))
      persist <- Gen.oneOf(true, false)
    } yield AltService(protocolId, authority, maxAge, persist)

  private val genAltValue: Gen[Value] =
    Gen.nonEmptyListOf(genAltService).map(svcs => AltValue(NonEmptyList.fromListUnsafe(svcs)))

  implicit private val arbitraryAltSvc: Arbitrary[`Alt-Svc`] =
    Arbitrary(Gen.oneOf(Gen.const(Clear), genAltValue).map(`Alt-Svc`(_)))

  checkAll("Alt-Svc", headerLaws[`Alt-Svc`])

  test("`Alt-Svc` parses `Clear") {
    assertEquals(`Alt-Svc`.fromString("Clear"), Right(`Alt-Svc`(Clear)))
  }

  test("`Alt-Svc` renders `Clear`") {
    assertEquals(
      `Alt-Svc`(Value.Clear).renderString,
      "Alt-Svc: clear",
    )
  }

  test("`Alt-Svc` renders alternative service") {
    assertEquals(
      `Alt-Svc`(
        AltValue(AltService(ProtocolId.h2, AltAuthority(None, 8080), 120L.some, persist = true))
      ).renderString,
      """Alt-Svc: h2=":8080"; ma=120; persist=1""",
    )
  }

  test("`Alt-Svc` renders multiple services") {
    assertEquals(
      `Alt-Svc`(
        AltValue(
          AltService(ProtocolId.h2, AltAuthority(None, 8080), 120L.some, persist = true),
          AltService(
            ProtocolId.`http/1.1`,
            AltAuthority(Uri.RegName(CIString("mydomain.com")).some, 8081),
            230L.some,
          ),
          AltService(
            ProtocolId.`h3-25`,
            AltAuthority(Uri.RegName(CIString("anotherhost.com")).some, 8083),
          ),
        )
      ).renderString,
      """Alt-Svc: h2=":8080"; ma=120; persist=1, http/1.1="mydomain.com:8081"; ma=230, h3-25="anotherhost.com:8083"""",
    )
  }

  test("`Alt-Svc` parsers multiple services") {
    assertEquals(
      `Alt-Svc`.fromString(
        """h2=":8080"; ma=120; persist=1, http/1.1="mydomain.com:8081"; ma=230, h3-25="anotherhost.com:8083""""
      ),
      `Alt-Svc`(
        AltValue(
          AltService(ProtocolId.h2, AltAuthority(None, 8080), 120L.some, persist = true),
          AltService(
            ProtocolId.`http/1.1`,
            AltAuthority(Uri.RegName(CIString("mydomain.com")).some, 8081),
            230L.some,
          ),
          AltService(
            ProtocolId.`h3-25`,
            AltAuthority(Uri.RegName(CIString("anotherhost.com")).some, 8083),
          ),
        )
      ).asRight[Throwable],
    )
  }

  test("`Alt-Svc` parses a bare authority with no parameters") {
    assertEquals(
      `Alt-Svc`.fromString("""h2=":443""""),
      Right(`Alt-Svc`(AltValue(AltService(ProtocolId.h2, AltAuthority(None, 443))))),
    )
  }

  test("`Alt-Svc` parses `persist` without `ma`") {
    assertEquals(
      `Alt-Svc`.fromString("""h2=":443"; persist=1"""),
      Right(`Alt-Svc`(AltValue(AltService(ProtocolId.h2, AltAuthority(None, 443), persist = true)))),
    )
  }

  test("`Alt-Svc` parses parameters in either order") {
    assertEquals(
      `Alt-Svc`.fromString("""h2=":443"; persist=1; ma=3600"""),
      Right(
        `Alt-Svc`(
          AltValue(AltService(ProtocolId.h2, AltAuthority(None, 443), 3600L.some, persist = true))
        )
      ),
    )
  }

  test("`Alt-Svc` ignores unknown extension parameters") {
    assertEquals(
      `Alt-Svc`.fromString("""h2=":443"; future-param=hello; ma=60"""),
      Right(`Alt-Svc`(AltValue(AltService(ProtocolId.h2, AltAuthority(None, 443), 60L.some)))),
    )
  }

  test("`Alt-Svc` round-trips an IPv6 authority") {
    val header = `Alt-Svc`(
      AltValue(
        AltService(ProtocolId.h2, AltAuthority(Uri.Host.unsafeFromString("[::1]").some, 443))
      )
    )
    assertEquals(`Alt-Svc`.fromString(header.value), Right(header))
  }

  test("`Alt-Svc` parses an open-ended (unregistered) protocol id") {
    assertEquals(
      `Alt-Svc`.fromString("""webrtc=":443""""),
      Right(
        `Alt-Svc`(
          AltValue(
            AltService(ProtocolId.fromString("webrtc").toOption.get, AltAuthority(None, 443))
          )
        )
      ),
    )
  }

  test("`Alt-Svc` combines multiple header occurrences") {
    val first = `Alt-Svc`(AltValue(AltService(ProtocolId.h2, AltAuthority(None, 443))))
    val second = `Alt-Svc`(AltValue(AltService(ProtocolId.h3, AltAuthority(None, 443))))
    assertEquals(
      `Alt-Svc`.headerSemigroupInstance.combine(first, second),
      `Alt-Svc`(
        AltValue(
          AltService(ProtocolId.h2, AltAuthority(None, 443)),
          AltService(ProtocolId.h3, AltAuthority(None, 443)),
        )
      ),
    )
  }
}
