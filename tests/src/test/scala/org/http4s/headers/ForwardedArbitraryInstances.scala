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
import cats.syntax.all._
import com.comcast.ip4s
import com.comcast.ip4s.Arbitraries._
import org.http4s.ParseResult
import org.http4s.Uri
import org.http4s.internal.bug
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

private[http4s] trait ForwardedArbitraryInstances extends ForwardedAuxiliaryGenerators {
  import Forwarded._

  // TODO: copied from `ArbitraryInstances` since the original is private.
  //       Consider re-using it somehow (discuss it).
  implicit private class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => throw bug(e.toString))
  }

  implicit val http4sTestingArbitraryForForwardedNodeObfuscated: Arbitrary[Node.Obfuscated] =
    Arbitrary(
      obfuscatedStringGen.map(Node.Obfuscated.fromString(_).yolo) :|
        "Node.Obfuscated"
    )

  implicit val http4sTestingArbitraryForForwardedNodeName: Arbitrary[Node.Name] =
    Arbitrary(
      Gen.oneOf(
        Arbitrary.arbitrary[ip4s.Ipv4Address].map(Node.Name.Ipv4.apply),
        Arbitrary.arbitrary[ip4s.Ipv6Address].map(Node.Name.Ipv6.apply),
        Arbitrary.arbitrary[Node.Obfuscated],
        Gen.const(Node.Name.Unknown),
      ) :| "Node.Name"
    )

  implicit val http4sTestingArbitraryForForwardedNodePort: Arbitrary[Node.Port] =
    Arbitrary(
      Gen.oneOf(
        portNumGen.map(Node.Port.fromInt(_).yolo),
        Arbitrary.arbitrary[Node.Obfuscated],
      ) :| "Node.Port"
    )

  implicit val http4sTestingArbitraryForForwardedNode: Arbitrary[Node] =
    Arbitrary({
      for {
        nodeName <- Arbitrary.arbitrary[Node.Name]
        nodePort <- Gen.option(Arbitrary.arbitrary[Node.Port])
      } yield Node(nodeName, nodePort)
    } :| "Node")

  implicit val http4sTestingArbitraryForForwardedHost: Arbitrary[Host] = {
    val uriHostGen =
      Arbitrary.arbitrary[Uri.Host]
    Arbitrary({
      for {
        // Increase frequency of empty host reg-names since it's a border case.
        uriHost <- Gen.oneOf(uriHostGen, Gen.const(Uri.RegName("")))
        portNum <- Gen.option(portNumGen)
      } yield Host.fromHostAndMaybePort(uriHost, portNum).yolo
    } :| "Host")
  }

  implicit val http4sTestingArbitraryForForwardedElement: Arbitrary[Element] =
    Arbitrary(
      Gen
        .atLeastOne(
          Arbitrary.arbitrary[Node].map(Element.fromFor),
          Arbitrary.arbitrary[Node].map(Element.fromBy),
          Arbitrary.arbitrary[Host].map(Element.fromHost),
          Arbitrary.arbitrary[Proto].map(Element.fromProto),
        )
        .map(_.reduceLeft[Element] {
          case (elem @ Element(None, _, _, _), Element(Some(forItem), None, None, None)) =>
            elem.withFor(forItem)
          case (elem @ Element(_, None, _, _), Element(None, Some(byItem), None, None)) =>
            elem.withBy(byItem)
          case (elem @ Element(_, _, None, _), Element(None, None, Some(hostItem), None)) =>
            elem.withHost(hostItem)
          case (elem @ Element(_, _, _, None), Element(None, None, None, Some(protoItem))) =>
            elem.withProto(protoItem)
          case (elem1, elem2) => throw bug(s"illegal combination of elements: $elem1 and $elem2")
        }) :|
        "Element"
    )

  implicit val http4sTestingArbitraryForForwarded: Arbitrary[Forwarded] =
    Arbitrary(
      Gen
        .nonEmptyListOf(Arbitrary.arbitrary[Element])
        .map(elems => Forwarded(NonEmptyList(elems.head, elems.tail))) :|
        "Forwarded"
    )
}
