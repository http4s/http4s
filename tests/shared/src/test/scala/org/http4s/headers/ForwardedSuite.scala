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

import cats.instances.string._
import cats.syntax.option._
import org.http4s.ParseFailure
import org.http4s.Uri
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop._

import java.nio.charset.StandardCharsets

class ForwardedSuite extends munit.ScalaCheckSuite with ForwardedAuxiliaryGenerators {

  import Forwarded.Node
  import Node.{Name, Obfuscated}

  private def Port(num: Int) = Node.Port.fromInt(num).toTry.get

  test("Node fromString should parse valid node definitions") {
    List(
      ("1.2.3.4", Name.ofIpv4Address(1, 2, 3, 4)),
      ("[1:2:3::4:5:6]", Name.ofIpv6Address(1, 2, 3, 0, 0, 4, 5, 6)),
      ("_a.b1-r2a_", Obfuscated("_a.b1-r2a_")),
      ("unknown", Name.Unknown),
    ).foreach { case (nameStr, parsedName) =>
      assertEquals(Node.fromString(nameStr), Right(Node(parsedName)))

      List(
        ("000", Port(0)),
        ("567", Port(567)),
        ("__k3a.d4ab5.r6a-", Obfuscated("__k3a.d4ab5.r6a-")),
      )
        .foreach { case (portStr, parsedPort) =>
          assertEquals(Node.fromString(s"$nameStr:$portStr"), Right(Node(parsedName, parsedPort)))
        }
    }
  }
  test("Node fromString should fail to parse invalid node definitions") {
    val invalidNodes = List(
      "1.2.3", // incorrect IPv4
      "1.2.3.4.5", // incorrect IPv4
      "1.2.3.4.", // dot after IPv4
      "1.2.3.4 ", // whitespace after IPv4
      "1:2:3::4:5:6", // IPv5 must be in brackets
      "[1:2:3:4:5:6]", // incorrect IPv6
      "[1:2:3::4:5:]", // incorrect IPv6
      "[1:2:3::4:5:6] ", // whitespace after node name
      "1.2.3.4:", // missed port
      "1.2.3.4:a", // alpha char as a node port
      "1.2.3.4:_", // illegal char as a node port
      "1.2.3.4: ", // whitespace instead of a node port
      "1.2.3.4: 567", // whitespace before the port num
      "1.2.3.4:567 ", // whitespace after the port num
      "unknownn", // unknown word 'unknownn'
      "_foo~bar", // illegal char '~' in obfuscated name
      "unknown:_foo~bar", // illegal char '~' in the obfuscated node port
      "http4s.org", // reg-name is not allowed
      ":567", // node name is missed
    )

    invalidNodes.foreach { nodeStr =>
      Node.fromString(nodeStr) match {
        case Left(a: ParseFailure) => assertEquals(a.sanitized, s"invalid node '$nodeStr'")
        case _ => fail("failed to parse")
      }
    }
  }

  test("Node.Obfuscated fromString should parse valid obfuscated values") {
    implicit val gen: Arbitrary[String] = Arbitrary[String](obfuscatedStringGen)
    forAll { (obfStr: String) =>
      Obfuscated.fromString(obfStr) match {
        case Right(r: Obfuscated) => assertEquals(r.value, obfStr)
        case _ => fail("failed to parse")
      }
    }
  }
  test(
    "Node.Obfuscated fromString should fail to parse obfuscated values that don't start with '_'"
  ) {
    val obfGen =
      for {
        firstCh <- obfuscatedCharGen if firstCh != '_'
        otherChs <- Gen.nonEmptyListOf(obfuscatedCharGen)
      } yield (firstCh :: otherChs).mkString
    implicit val gen: Arbitrary[String] = Arbitrary[String](obfGen)

    forAll { (obfStr: String) =>
      Obfuscated.fromString(obfStr) match {
        case Left(l: ParseFailure) =>
          assertEquals(l.sanitized, s"invalid obfuscated value '$obfStr'")
        case _ => fail("failed to parse")
      }
    }
  }
  test(
    "Node.Obfuscated fromString should fail to parse obfuscated values that contain invalid symbols"
  ) {
    val obfGen =
      for {
        initChs <- Gen.listOf(obfuscatedCharGen)
        badCh <- Gen.asciiChar if !(badCh.isLetterOrDigit || "._-".contains(badCh))
        lastChs <- Gen.listOf(obfuscatedCharGen)
      } yield ('_' :: initChs ::: badCh :: lastChs).mkString
    implicit val gen: Arbitrary[String] = Arbitrary[String](obfGen)

    forAll { (obfStr: String) =>
      Obfuscated.fromString(obfStr) match {
        case Left(a: ParseFailure) =>
          assertEquals(a.sanitized, s"invalid obfuscated value '$obfStr'")
        case _ => fail("Failed to parse")
      }
    }
  }
  import Forwarded.Host

  test("Host fromUri should build `Host` from `Uri` if a host is defined or fail otherwise") {
    forAll { (uri: Uri) =>
      Host.fromUri(uri) match {
        case Right(host: Host) =>
          assertEquals(host.host, uri.host.get)
          assertEquals(host.port, uri.port)
        case Left(p: ParseFailure) => assertEquals(p.sanitized, "missing host")
      }
    }
  }
  test("Host fromUri should fail to build `Host` if incorrect port number is specified in `Uri`") {
    val portRange = Forwarded.PortMin to Forwarded.PortMax
    implicit val badPortGen: Arbitrary[Int] =
      Arbitrary(Gen.long.map(_.toInt).filterNot(portRange.contains))

    forAll { (genUri: Uri, portNum: Int) =>
      val newUri =
        genUri.copy(authority =
          genUri.authority
            .map(_.copy(port = Some(portNum)))
            .orElse(Some(Uri.Authority(port = Some(portNum))))
        )

      Host.fromUri(newUri) match {
        case Left(p: ParseFailure) => assertEquals(p.sanitized, "invalid port number")
        case _ => fail("Failed to parse")
      }
    }
  }

  test("Host fromUri should parse valid host definitions") {
    forAll { (uriAuth: Uri.Authority) =>
      val hostStr = uriAuth.host.renderString + uriAuth.port.map(":" + _).orEmpty

      Host.fromString(hostStr) match {
        case Right(host: Host) =>
          (host.host, uriAuth.host) match {
            case (Uri.RegName(actual), Uri.RegName(expected)) =>
              assertEquals(
                actual.toString,
                // TODO: `Uri.decode` should not be necessary here. Remove when #1651 (or #2012) get fixed.
                Uri.decode(expected.toString, StandardCharsets.ISO_8859_1),
              )
            case _ => assertEquals(host.host, uriAuth.host)
          }
          assertEquals(host.port, uriAuth.port)
        case _ => fail("Parsing should work")
      }
    }
  }
  test("Host fromUri should fail to parse invalid host definitions") {
    val invalidHosts = List(
      "aaa.bbb:12:34", // two colons
      "aaa.bbb:65536", // port number exceeds the maximum
      "aaa.bbb:a12", // port is not a number
      " aaa.bbb:12", // whitespace
      "aaa .bbb:12", // whitespace
      "aaa. bbb:12", // whitespace
      "aaa.bbb :12", // whitespace
      "aaa.bbb: 12", // whitespace
      "aaa.bbb:12 ", // whitespace
      "aaa?bbb", // illegal symbol
    )

    invalidHosts.foreach { hostStr =>
      Host.fromString(hostStr) match {
        case Left(p: ParseFailure) => assertEquals(p.sanitized, s"invalid host '$hostStr'")
        case _ => fail("Bad parsing")
      }
    }
  }
}
