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
import com.comcast.ip4s.Ipv4Address
import com.comcast.ip4s.Ipv6Address
import org.http4s.syntax.all._
import org.http4s.util.Renderer
import org.scalacheck.Prop._

class ForwardedRenderersSuite extends munit.ScalaCheckSuite with ForwardedArbitraryInstances {

  test("Node.Name") {
    import Forwarded.Node

    forAll { (nodeName: Node.Name) =>
      val rendered = Renderer.renderString(nodeName)
      assert(rendered.nonEmpty) // just to check for something here

      nodeName match {
        case Node.Name.Ipv4(ipv4) =>
          assertEquals(Ipv4Address.fromString(rendered), Some(ipv4))
        case Node.Name.Ipv6(ipv6) =>
          assert(rendered.startsWith("["))
          assert(rendered.endsWith("]"))
          assertEquals(Ipv6Address.fromString(rendered.tail.init), Some(ipv6))
        case Node.Name.Unknown =>
          assertEquals(rendered, "unknown")
        case obfName: Node.Obfuscated =>
          assertEquals(Node.Obfuscated.fromString(rendered), Right(obfName))
      }
    }
  }
  test("Node.Port") {
    import Forwarded.Node

    forAll { (nodePort: Node.Port) =>
      val rendered = Renderer.renderString(nodePort)
      assert(rendered.nonEmpty)

      nodePort match {
        case Node.Port.Numeric(num) =>
          assertEquals(Integer.parseUnsignedInt(rendered), num)
        case obfPort: Node.Obfuscated =>
          assertEquals(Node.Obfuscated.fromString(rendered), Right(obfPort))
      }
    }
  }
  test("Node") {
    import Forwarded.Node

    forAll { (node: Node) =>
      val rendered = Renderer.renderString(node)
      assert(rendered.nonEmpty)

      assertEquals(Node.fromString(rendered), Right(node))
    }
  }
  test("Host") {
    import Forwarded.Host

    forAll { (host: Host) =>
      val rendered = Renderer.renderString(host)

      assertEquals(Host.fromString(rendered), Right(host))
    }
  }
  test("Element") {
    import Forwarded.Element

    forAll { (elem: Element) =>
      val rendered = Renderer.renderString(elem)
      assert(rendered.nonEmpty)

      assertEquals(Forwarded.parse(rendered), Right(Forwarded(NonEmptyList(elem, Nil))))
    }
  }
  test("Forwarded") {
    val headerInit = Forwarded.name.toString + ": "

    forAll { (fwd: Forwarded) =>
      val rendered = Renderer.renderString(fwd.toRaw1)
      assert(rendered.startsWith(headerInit))

      assertEquals(Forwarded.parse(rendered.drop(headerInit.length)), Right(fwd))
    }
  }
}
