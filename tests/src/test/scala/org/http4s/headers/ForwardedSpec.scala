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

import java.nio.charset.StandardCharsets

import cats.instances.string._
import cats.syntax.option._
import org.http4s.laws.discipline.ArbitraryInstances
import org.http4s.{ParseFailure, Uri}
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.execute.Result
import org.specs2.mutable.{Specification, Tables}

class ForwardedSpec
    extends Specification
    with Tables
    with ScalaCheck
    with ArbitraryInstances
    with ForwardedAuxiliaryGenerators {

  "Node" >> {
    import Forwarded.Node
    import Node.{Name, Obfuscated}

    def Port(num: Int) = Node.Port.fromInt(num).toTry.get

    "fromString" should {
      "parse valid node definitions" in {
        "Node Name String" | "Parsed Node Name" |
          "1.2.3.4" ! Name.ofIpv4Address(1, 2, 3, 4) |
          "[1:2:3::4:5:6]" ! Name.ofIpv6Address(1, 2, 3, 0, 0, 4, 5, 6) |
          "_a.b1-r2a_" ! Obfuscated("_a.b1-r2a_") |
          "unknown" ! Name.Unknown |> { (nameStr, parsedName) =>
            Node.fromString(nameStr) must beRight(Node(parsedName))

            "Node Port String" | "Parsed Node Port" |
              "000" ! Port(0) |
              "567" ! Port(567) |
              "__k3a.d4ab5.r6a-" ! Obfuscated("__k3a.d4ab5.r6a-") |> { (portStr, parsedPort) =>
                Node.fromString(s"$nameStr:$portStr") must beRight(Node(parsedName, parsedPort))
              }
          }
      }
      "fail to parse invalid node definitions" in {
        val invalidNodes = Seq(
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
          ":567" // node name is missed
        )

        Result.foreach(invalidNodes) { nodeStr =>
          Node.fromString(nodeStr) must beLeft {
            (_: ParseFailure).sanitized must_=== s"invalid node '$nodeStr'"
          }
        }
      }
    }
  }
  "Node.Obfuscated" >> {
    import Forwarded.Node.Obfuscated

    "fromString" >> {
      "parse valid obfuscated values" in {
        prop { (obfStr: String) =>
          Obfuscated.fromString(obfStr) must beRight {
            (_: Obfuscated).value must_=== obfStr
          }
        }.setGen(obfuscatedStringGen)
      }
      "fail to parse obfuscated values that don't start with '_'" in {
        val obfGen =
          for {
            firstCh <- obfuscatedCharGen if firstCh != '_'
            otherChs <- Gen.nonEmptyListOf(obfuscatedCharGen)
          } yield (firstCh :: otherChs).mkString

        prop { (obfStr: String) =>
          Obfuscated.fromString(obfStr) must beLeft {
            (_: ParseFailure).sanitized must_=== s"invalid obfuscated value '$obfStr'"
          }
        }.setGen(obfGen)
      }
      "fail to parse obfuscated values that contain invalid symbols" in {
        val obfGen =
          for {
            initChs <- Gen.listOf(obfuscatedCharGen)
            badCh <- Gen.asciiChar if !(badCh.isLetterOrDigit || "._-".contains(badCh))
            lastChs <- Gen.listOf(obfuscatedCharGen)
          } yield ('_' :: initChs ::: badCh :: lastChs).mkString

        prop { (obfStr: String) =>
          Obfuscated.fromString(obfStr) must beLeft {
            (_: ParseFailure).sanitized must_=== s"invalid obfuscated value '$obfStr'"
          }
        }.setGen(obfGen)
      }
    }
  }
  "Host" >> {
    import Forwarded.Host

    "fromUri" should {
      "build `Host` from `Uri` if a host is defined or fail otherwise" in prop { (uri: Uri) =>
        Host.fromUri(uri) must {
          if (uri.host.isDefined)
            beRight { (host: Host) =>
              host.host must_=== uri.host.get
              host.port must_=== uri.port
            }
          else
            beLeft {
              (_: ParseFailure).sanitized must_=== "missing host"
            }
        }
      }
      "fail to build `Host` if incorrect port number is specified in `Uri`" in {
        val portRange = Forwarded.PortMin to Forwarded.PortMax
        val badPortGen = Arbitrary.arbitrary[Int].filterNot(portRange.contains)

        prop { (genUri: Uri, portNum: Int) =>
          val newUri =
            genUri.copy(authority = genUri.authority
              .map(_.copy(port = Some(portNum)))
              .orElse(Some(Uri.Authority(port = Some(portNum)))))

          Host.fromUri(newUri) must beLeft {
            (_: ParseFailure).sanitized must_=== "invalid port number"
          }
        }.setGen2(badPortGen)
      }
    }

    "fromString" should {
      "parse valid host definitions" in prop { (uriAuth: Uri.Authority) =>
        val hostStr = uriAuth.host.renderString + uriAuth.port.map(":" + _).orEmpty

        Host.fromString(hostStr) must beRight { (host: Host) =>
          (host.host, uriAuth.host) must beLike {
            case (Uri.RegName(actual), Uri.RegName(expected)) =>
              actual.toString must_===
                // TODO: `Uri.decode` should not be necessary here. Remove when #1651 (or #2012) get fixed.
                Uri.decode(expected.toString, StandardCharsets.ISO_8859_1)
            case _ => host.host must_=== uriAuth.host
          }
          host.port must_=== uriAuth.port
        }
      }
      "fail to parse invalid host definitions" in {
        val invalidHosts = Seq(
          "aaa.bbb:12:34", // two colons
          "aaa.bbb:65536", // port number exceeds the maximum
          "aaa.bbb:a12", // port is not a number
          " aaa.bbb:12", // whitespace
          "aaa .bbb:12", // whitespace
          "aaa. bbb:12", // whitespace
          "aaa.bbb :12", // whitespace
          "aaa.bbb: 12", // whitespace
          "aaa.bbb:12 ", // whitespace
          "aaa?bbb" // illegal symbol
        )

        Result.foreach(invalidHosts) { hostStr =>
          Host.fromString(hostStr) must beLeft {
            (_: ParseFailure).sanitized must_=== s"invalid host '$hostStr'"
          }
        }
      }
    }
  }
}
