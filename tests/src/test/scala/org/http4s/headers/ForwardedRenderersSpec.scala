/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.headers

import cats.data.NonEmptyList
import org.http4s.Uri
import org.http4s.util.Renderer
import org.specs2.ScalaCheck
import org.specs2.mutable.{Specification, Tables}

class ForwardedRenderersSpec
    extends Specification
    with ScalaCheck
    with Tables
    with ForwardedArbitraryInstances {

  "Renderer should render to a string that roundtrips back to the original value".p.tab

  "Node.Name" in {
    import Forwarded.Node

    prop { (nodeName: Node.Name) =>
      val rendered = Renderer.renderString(nodeName)
      rendered must not be empty // just to check for something here

      nodeName match {
        case Node.Name.Ipv4(ipv4) =>
          Uri.Ipv4Address.fromString(rendered) must beRight(ipv4)
        case Node.Name.Ipv6(ipv6) =>
          rendered must startWith("[")
          rendered must endWith("]")
          Uri.Ipv6Address.fromString(rendered.tail.init) must beRight(ipv6)
        case Node.Name.Unknown =>
          rendered ==== "unknown"
        case obfName: Node.Obfuscated =>
          Node.Obfuscated.fromString(rendered) must beRight(obfName)
      }
    }
  }
  "Node.Port" in {
    import Forwarded.Node

    prop { (nodePort: Node.Port) =>
      val rendered = Renderer.renderString(nodePort)
      rendered must not be empty

      nodePort match {
        case Node.Port(num) =>
          Integer.parseUnsignedInt(rendered) ==== num
        case obfPort: Node.Obfuscated =>
          Node.Obfuscated.fromString(rendered) must beRight(obfPort)
      }
    }
  }
  "Node" in {
    import Forwarded.Node

    prop { (node: Node) =>
      val rendered = Renderer.renderString(node)
      rendered must not be empty

      Node.fromString(rendered) must beRight(node)
    }
  }
  "Host" in {
    import Forwarded.Host

    prop { (host: Host) =>
      val rendered = Renderer.renderString(host)

      Host.fromString(rendered) must beRight(host)
    }
  }
  "Element" in {
    import Forwarded.Element

    prop { (elem: Element) =>
      val rendered = Renderer.renderString(elem)
      rendered must not be empty

      Forwarded.parse(rendered) must beRight(Forwarded(NonEmptyList(elem, Nil)))
    }
  }
  "Forwarded" in {
    val headerInit = Forwarded.name.value + ": "

    prop { (fwd: Forwarded) =>
      val rendered = Renderer.renderString(fwd)
      rendered must startWith(headerInit)

      Forwarded.parse(rendered.drop(headerInit.length)) must beRight(fwd)
    }
  }
}
