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

    sealed trait Name { self: Product => }

    object Name {
      case class Ipv4(address: Uri.Ipv4Address) extends Name
      case class Ipv6(address: Uri.Ipv6Address) extends Name
      case object Unknown extends Name

      def ofInet4Address(address: Inet4Address): Name = Ipv4(
        Uri.Ipv4Address.fromInet4Address(address))
      def ofIpv4Address(a: Byte, b: Byte, c: Byte, d: Byte): Name = Ipv4(
        Uri.Ipv4Address(a, b, c, d))

      def ofInet6Address(address: Inet6Address): Name = Ipv6(
        Uri.Ipv6Address.fromInet6Address(address))
      // format: off
      def ofIpv6Address(
          a: Short, b: Short, c: Short, d: Short, e: Short, f: Short, g: Short, h: Short
      ): Name = Ipv6(Uri.Ipv6Address(a, b, c, d, e, f, g, h))
      // format: on
    }

    sealed trait Port { self: Product => }

    object Port {
      final case class Numeric(value: Int) extends Port {
        override def productPrefix: String = "Port"
      }

      def fromInt(num: Int): ParseResult[Port] =
        checkPortNum(num).toLeft(Numeric(num))

      def unapply(port: Port): Option[Int] =
        PartialFunction.condOpt(port) { case Numeric(num) => num }
    }

    /** Opaque type for obfuscated identifiers.
      *
      * @param value obfuscated identifier with leading '_' (underscore) symbol.
      *
      * @see [[https://tools.ietf.org/html/rfc7239#section-6.3]]
      */
    sealed abstract case class Obfuscated private (value: String) extends Name with Port

    object Obfuscated {
      def fromString(s: String): ParseResult[Obfuscated] =
        new ModelNodeObfuscatedParser(s).parse

      /** Unsafe constructor for internal use only. */
      private[http4s] def apply(s: String): Obfuscated = new Obfuscated(s) {}
    }

    def fromString(s: String): ParseResult[Node] = new ModelNodeParser(s).parse
  }

  sealed abstract case class Host private (host: Uri.Host, port: Option[Int])

  object Host {
    private[this] def apply(host: Uri.Host, port: Option[Int]) = new Host(host, port) {}

    /** Creates [[Host]] from [[Uri.Host]].
      * Assumes that the latter is always valid so no further validation is necessary.
      */
    def ofHost(uriHost: Uri.Host): Host = apply(uriHost, None)

    /** Creates [[Host]] from [[Uri.Host]] and port number.
      * Validates the latter and returns [[ParseFailure]] if it is invalid.
      */
    def fromHostAndPort(uriHost: Uri.Host, port: Int): ParseResult[Host] =
      checkPortNum(port).toLeft(apply(uriHost, Some(port)))

    /** Creates [[Host]] from [[Uri.Host]] and optional port number.
      * For internal use in parsers in generators only.
      */
    private[http4s] def fromHostAndMaybePort(
        uriHost: Uri.Host,
        port: Option[Int]): ParseResult[Host] =
      port.fold(ofHost(uriHost).asRight[ParseFailure])(fromHostAndPort(uriHost, _))

    /** Creates [[Host]] from [[Uri.host]] and [[Uri.port]] parts of the given [[Uri]].
      */
    def fromUri(uri: Uri): ParseResult[Host] =
      uri.host.toRight(Failures.missingHost(uri)).flatMap(fromHostAndMaybePort(_, uri.port))

    /** Parses host and optional port number from the given string according to RFC3986.
      */
    def fromString(s: String): ParseResult[Host] = new ModelHostParser(s).parse
  }

  type Proto = Uri.Scheme
  val Proto: Uri.Scheme.type = Uri.Scheme

  sealed trait Element extends Renderable { self: Product =>
    def maybeBy: Option[Node]
    def maybeFor: Option[Node]
    def maybeHost: Option[Host]
    def maybeProto: Option[Proto]

    def withBy(value: Node): Element
    def withFor(value: Node): Element
    def withHost(value: Host): Element
    def withProto(value: Proto): Element

    override def render(writer: Writer): writer.type = renderElement(writer, this)
  }

  /** Enables the following construction syntax (which preserves type safety and consistency):
    * {{{
    *   Element
    *     .fromBy(<by-node>)
    *     .withFor(<for-node>)
    *     .withHost(<host>)
    *     .withProto(<schema>)`
    * }}}
    */
  object Element {
    // Since at least one of the fields must be set to `Some`,
    // the `Element` trait implementation is hidden.
    private[this] final case class C(
        maybeBy: Option[Node] = None,
        maybeFor: Option[Node] = None,
        maybeHost: Option[Host] = None,
        maybeProto: Option[Proto] = None)
        extends Element {

      def withBy(value: Node): Element = copy(maybeBy = Some(value))
      def withFor(value: Node): Element = copy(maybeFor = Some(value))
      def withHost(value: Host): Element = copy(maybeHost = Some(value))
      def withProto(value: Proto): Element = copy(maybeProto = Some(value))

      override def productPrefix: String = "Element"
    }

    def fromBy(value: Node): Element = C(maybeBy = Some(value))
    def fromFor(value: Node): Element = C(maybeFor = Some(value))
    def fromHost(value: Host): Element = C(maybeHost = Some(value))
    def fromProto(value: Proto): Element = C(maybeProto = Some(value))

    def unapply(elem: Element): Option[(Option[Node], Option[Node], Option[Host], Option[Proto])] =
      Some((elem.maybeBy, elem.maybeFor, elem.maybeHost, elem.maybeProto))
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
