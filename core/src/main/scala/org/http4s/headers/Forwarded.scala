/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.headers

import java.net.{Inet4Address, Inet6Address}

import cats.data.NonEmptyList
import cats.syntax.either._
import org.http4s._
import org.http4s.util.{Renderable, Writer}

object Forwarded
    extends HeaderKey.Internal[Forwarded]
    with HeaderKey.Recurring
    with ForwardedRenderers
    with parser.ForwardedModelParsing {

  final case class Node(nodeName: Node.Name, nodePort: Option[Node.Port] = None)

  protected[http4s] val NodeNameIpv4 = Node.Name.Ipv4

  object Node {
    def apply(nodeName: Name, nodePort: Port): Node = apply(nodeName, Some(nodePort))

    sealed trait Name { _: Product => }

    object Name {
      case class Ipv4(address: Uri.Ipv4Address) extends Name
      case class Ipv6(address: Uri.Ipv6Address) extends Name
      case object Unknown extends Name

      def apply(address: Uri.Ipv4Address): Name = Name.Ipv4(address)
      def apply(address: Inet4Address): Name = apply(Uri.Ipv4Address.fromInet4Address(address))
      def apply(a: Byte, b: Byte, c: Byte, d: Byte): Name = apply(Uri.Ipv4Address(a, b, c, d))

      def apply(address: Uri.Ipv6Address): Name = Name.Ipv6(address)
      def apply(address: Inet6Address): Name = apply(Uri.Ipv6Address.fromInet6Address(address))
      def apply(a: Short, b: Short, c: Short, d: Short, e: Short, f: Short, g: Short, h: Short)
          : Name = apply(Uri.Ipv6Address(a, b, c, d, e, f, g, h))
    }

    sealed trait Port { _: Product => }

    object Port {
      private[this] final case class C(value: Int) extends Port {
        override def productPrefix: String = "Port"
      }

      def fromInt(num: Int): ParseResult[Port] =
        checkPortNum(num).toLeft(C(num))

      def unapply(port: Port): Option[Int] =
        PartialFunction.condOpt(port) { case C(num) => num }
    }

    sealed trait Obfuscated extends Name with Port { _: Product =>

      /** Obfuscated value must start with '_' (underscore) symbol.
        */
      def value: String
    }
    object Obfuscated {
      private[this] final case class C(value: String) extends Obfuscated {
        override def productPrefix: String = "Obfuscated"
      }

      def fromString(s: String): ParseResult[Obfuscated] =
        new ModelNodeObfuscatedParser(s).parse

      def unapply(o: Obfuscated): Option[String] = Some(o.value)

      // Referenced by model parsers.
      private[http4s] def apply(s: String): Obfuscated = C(s)
    }

    def fromString(s: String): ParseResult[Node] = new ModelNodeParser(s).parse
  }

  sealed trait Host { _: Product =>
    def host: Uri.Host
    def port: Option[Int]
  }

  object Host {
    private[this] final case class C(host: Uri.Host, port: Option[Int]) extends Host {
      override def productPrefix: String = "Host"
    }

    def apply(uriHost: Uri.Host): Host = C(uriHost, None)

    def from(uriHost: Uri.Host, port: Int): ParseResult[Host] =
      checkPortNum(port).toLeft(C(uriHost, Some(port)))

    def from(uriHost: Uri.Host, port: Option[Int]): ParseResult[Host] =
      port.fold(apply(uriHost).asRight[ParseFailure])(from(uriHost, _))

    def fromUri(uri: Uri): ParseResult[Host] =
      uri.host.toRight(Failures.missingHost(uri)).flatMap(from(_, uri.port))

    def fromString(s: String): ParseResult[Host] = new ModelHostParser(s).parse

    def unapply(host: Host): Option[(Uri.Host, Option[Int])] = Some((host.host, host.port))
  }

  type Proto = Uri.Scheme
  val Proto: Uri.Scheme.type = Uri.Scheme

  sealed trait Element extends Renderable { _: Product =>
    def `by`: Option[Node]
    def `for`: Option[Node]
    def `host`: Option[Host]
    def `proto`: Option[Proto]

    def withBy(value: Node): Element
    def withFor(value: Node): Element
    def withHost(value: Host): Element
    def withProto(value: Proto): Element

    def withoutBy: Element
    def withoutFor: Element
    def withoutHost: Element
    def withoutProto: Element

    override def render(writer: Writer): writer.type = renderElement(writer, this)
  }

  /** Enables the following construction syntax (while preserving type safety and consistency):
    * {{{
    *   Element
    *     .withBy(<by-node>)
    *     .withFor(<for-node>)
    *     .withHost(<host>)
    *     .withProto(<schema>)`
    * }}}
    */
  object Element {
    // Since at least one of the fields must be set to `Some`,
    // the `Element` trait implementation is hidden.
    private[this] final case class C(
        `by`: Option[Node] = None,
        `for`: Option[Node] = None,
        `host`: Option[Host] = None,
        `proto`: Option[Proto] = None)
        extends Element {

      def withBy(value: Node): Element = copy(`by` = Some(value))
      def withFor(value: Node): Element = copy(`for` = Some(value))
      def withHost(value: Host): Element = copy(`host` = Some(value))
      def withProto(value: Proto): Element = copy(`proto` = Some(value))

      def withoutBy: Element = copy(`by` = None)
      def withoutFor: Element = copy(`for` = None)
      def withoutHost: Element = copy(`host` = None)
      def withoutProto: Element = copy(`proto` = None)

      override def productPrefix: String = "Element"
    }

    def withBy(value: Node): Element = C(`by` = Some(value))
    def withFor(value: Node): Element = C(`for` = Some(value))
    def withHost(value: Host): Element = C(`host` = Some(value))
    def withProto(value: Proto): Element = C(`proto` = Some(value))

    def unapply(elem: Element): Option[(Option[Node], Option[Node], Option[Host], Option[Proto])] =
      Some((elem.`by`, elem.`for`, elem.`host`, elem.`proto`))
  }

  final val PortMin = 0
  final val PortMax = 65535

  private def checkPortNum(portNum: Int): Option[ParseFailure] =
    if ((portNum >= PortMin) && (portNum <= PortMax))
      None
    else
      Some(Failures.invalidPortNum(portNum))

  private object Failures {
    def invalidPortNum(num: Int) =
      ParseFailure("invalid port number", s"port $num is not in range $PortMin..$PortMax")
    def missingHost(uri: Uri) =
      ParseFailure("missing host", s"no host defined in the URI '$uri'")
  }

  override def parse(s: String): ParseResult[Forwarded] = parser.HttpHeaderParser.FORWARDED(s)
}

final case class Forwarded(values: NonEmptyList[Forwarded.Element])
    extends Header.RecurringRenderable {

  override type Value = Forwarded.Element
  override def key: Forwarded.type = Forwarded

  def apply(firstElem: Forwarded.Element, otherElems: Forwarded.Element*): Forwarded =
    Forwarded(NonEmptyList.of(firstElem, otherElems: _*))
}
