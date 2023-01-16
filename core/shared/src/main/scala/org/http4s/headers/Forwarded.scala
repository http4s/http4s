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
import cats.parse.Numbers
import cats.parse.Parser0
import cats.parse.Rfc5234
import cats.parse.{Parser => P}
import cats.syntax.either._
import cats.syntax.show._
import com.comcast.ip4s.Ipv4Address
import com.comcast.ip4s.Ipv6Address
import org.http4s.Header
import org.http4s._
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc3986
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

import java.net.Inet4Address
import java.net.Inet6Address
import java.nio.ByteBuffer
import java.util.Locale
import scala.util.Try

object Forwarded extends ForwardedRenderers {

  def apply(head: Forwarded.Element, tail: Forwarded.Element*): Forwarded =
    apply(NonEmptyList(head, tail.toList))

  final case class Node(nodeName: Node.Name, nodePort: Option[Node.Port] = None)

  protected[http4s] val NodeNameIpv4 = Node.Name.Ipv4

  object Node {
    def apply(nodeName: Name, nodePort: Port): Node = apply(nodeName, Some(nodePort))

    sealed trait Name { self: Product => }

    object Name {
      // scalafix:off Http4sGeneralLinters; bincompat until 1.0
      case class Ipv4(address: Ipv4Address) extends Name
      case class Ipv6(address: Ipv6Address) extends Name
      // scalafix:on
      case object Unknown extends Name

      @deprecated("Use Name.Ipv4(Ipv4Address.fromInet4Address(address))", "0.23.5")
      def ofInet4Address(address: Inet4Address): Name =
        Ipv4(Ipv4Address.fromInet4Address(address))
      def ofIpv4Address(a: Byte, b: Byte, c: Byte, d: Byte): Name = Ipv4(
        Ipv4Address.fromBytes(a.toInt, b.toInt, c.toInt, d.toInt)
      )

      @deprecated("Use Name.Ipv6(Ipv6Address.fromInet6Address(address))", "0.23.5")
      def ofInet6Address(address: Inet6Address): Name =
        Ipv6(Ipv6Address.fromInet6Address(address))

      def ofIpv6Address(
          a: Short,
          b: Short,
          c: Short,
          d: Short,
          e: Short,
          f: Short,
          g: Short,
          h: Short,
      ): Name = {
        val bb = ByteBuffer.allocate(16)
        bb.putShort(a)
        bb.putShort(b)
        bb.putShort(c)
        bb.putShort(d)
        bb.putShort(e)
        bb.putShort(f)
        bb.putShort(g)
        bb.putShort(h)
        Ipv6(Ipv6Address.fromBytes(bb.array).get)
      }
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
      * @see [[https://datatracker.ietf.org/doc/html/rfc7239#section-6.3 RFC 7239, Section 6.3, Obfuscated Identifier]]
      */
    sealed abstract case class Obfuscated private (value: String) extends Name with Port

    object Obfuscated {
      val parser: P[Obfuscated] =
        (P.char('_') ~ (P.oneOf(List(Rfc5234.alpha, Rfc5234.digit, P.charIn("._-"))).rep(1))).string
          .map(Obfuscated.apply)

      def fromString(s: String): ParseResult[Obfuscated] =
        parser.parseAll(s).left.map { e =>
          ParseFailure(s"invalid obfuscated value '$s'", e.show)
        }

      /** Unsafe constructor for internal use only. */
      private[http4s] def apply(s: String): Obfuscated = new Obfuscated(s) {}
    }

    def fromString(s: String): ParseResult[Node] =
      parser.parseAll(s).left.map { e =>
        ParseFailure(s"invalid node '$s'", e.show)
      }

    val parser: P[Node] = {
      // https://datatracker.ietf.org/doc/html/rfc7239#section-4

      def modelNodePortFromString(str: String): Option[Node.Port] =
        Try(Integer.parseUnsignedInt(str)).toOption.flatMap(Node.Port.fromInt(_).toOption)

      // node-port = port / obfport
      // port      = 1*5DIGIT
      // obfport   = "_" 1*(ALPHA / DIGIT / "." / "_" / "-")
      val nodePort: P[Node.Port] =
        Numbers.digits
          // is it worth it to consume only up to 5 chars or just let it fail later?
          .mapFilter(digits => modelNodePortFromString(digits))
          .orElse(Obfuscated.parser)

      // nodename = IPv4address / "[" IPv6address "]" / "unknown" / obfnode
      // obfnode  = "_" 1*( ALPHA / DIGIT / "." / "_" / "-")
      val nodeName: P[Node.Name] =
        P.oneOf[Node.Name](
          List(
            Rfc3986.ipv4Address.map(Node.Name.Ipv4.apply),
            Rfc3986.ipv6Address
              .between(P.char('['), P.char(']'))
              .map(Node.Name.Ipv6.apply),
            P.string("unknown").as(Node.Name.Unknown),
            Obfuscated.parser,
          )
        )

      // node = nodename [ ":" node-port ]
      (nodeName ~ (P.char(':') *> nodePort).?)
        .map { case (n, p) => Node(n, p) }
    }
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
        port: Option[Int],
    ): ParseResult[Host] =
      port.fold(ofHost(uriHost).asRight[ParseFailure])(fromHostAndPort(uriHost, _))

    /** Creates [[Host]] from [[Uri.host]] and [[Uri.port]] parts of the given [[Uri]].
      */
    def fromUri(uri: Uri): ParseResult[Host] =
      uri.host.toRight(Failures.missingHost(uri)).flatMap(fromHostAndMaybePort(_, uri.port))

    /** Parses host and optional port number from the given string according to RFC3986.
      */
    def fromString(s: String): ParseResult[Host] =
      parser.parseAll(s).left.map { e =>
        ParseFailure(s"invalid host '$s'", e.show)
      }

    val parser: Parser0[Host] = {
      // this is awkward but the spec allows an empty port number
      val port: P[Option[Int]] = Numbers.digits
        .mapFilter { s =>
          if (s.isEmpty) Some(None)
          else {
            try {
              val i = s.toInt
              if (i <= PortMax) Some(Some(i)) else None
            } catch { case _: NumberFormatException => None }
          }
        }

      // ** RFC7230 **
      // Host     = uri-host [ ":" port ]
      // uri-host = <host, see [RFC3986], Section 3.2.2>
      // port     = <port, see [RFC3986], Section 3.2.3>

      // ** RFC3986 **
      // port = *DIGIT
      (Uri.Parser.host ~ (P.char(':') *> port).?)
        .map { case (h, p) => apply(h, p.flatten) }
    }
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
        maybeProto: Option[Proto] = None,
    ) extends Element {

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
    def invalidPortNum(num: Int): ParseFailure =
      ParseFailure("invalid port number", s"port $num is not in range $PortMin..$PortMax")
    def missingHost(uri: Uri): ParseFailure =
      ParseFailure("missing host", s"no host defined in the URI '$uri'")
  }

  def parse(s: String): ParseResult[Forwarded] =
    ParseResult.fromParser(parser, "Invalid Forwarded header")(s)

  private val parser: P[Forwarded] = {
    // https://datatracker.ietf.org/doc/html/rfc7239#section-4

    // A utility so that we can decode multiple pairs and join them in a single element
    trait Pair {
      def create: Element
      def merge(e: Element): Element
    }

    object Pair {
      def apply(c: Element, m: Element => Element): Pair = new Pair {
        override def create: Element = c
        override def merge(e: Element): Element = m(e)
      }
    }

    def quoted[A](p: Parser0[A]): P[A] =
      CommonRules.token
        .orElse(CommonRules.quotedString)
        .flatMap(str =>
          p.parseAll(str)
            .fold(_ => P.fail[A], P.pure)
        ) // this looks not very good

    // forwarded-pair = token "=" value
    // The syntax of a "by" value, after potential quoted-string unescaping
    // conforms to the "node" ABNF
    // The syntax of a "for" value, after potential quoted-string
    // unescaping, conforms to the "node" ABNF
    // The syntax for a "host" value, after potential quoted-string
    // unescaping, MUST conform to the Host ABNF described in Section 5.4 of
    // [RFC7230].
    // The syntax of a "proto" value, after potential quoted-string
    // unescaping, MUST conform to the URI scheme name as defined in Section 3.1 in
    // [RFC3986]

    val host = Host.parser
    val proto = Uri.Parser.scheme
    val node = Node.parser

    val forwardedPair = P.oneOf(
      List(
        CommonRules.token
          .flatMap(tok =>
            tok.toLowerCase(Locale.ROOT) match {
              case "by" =>
                P.char('=') *> quoted(node).map(n => Pair(Element.fromBy(n), _.withBy(n)))
              case "for" =>
                P.char('=') *> quoted(node).map(n => Pair(Element.fromFor(n), _.withFor(n)))
              case "host" =>
                P.char('=') *> quoted(host).map(h => Pair(Element.fromHost(h), _.withHost(h)))
              case "proto" =>
                P.char('=') *> quoted(proto).map(p => Pair(Element.fromProto(p), _.withProto(p)))
              case other =>
                P.failWith(s"expected parameters: 'by', 'for', 'host' or 'proto', but got '$other'")
            }
          )
      )
    )

    // forwarded-element = [ forwarded-pair ] *( ";" [ forwarded-pair ] )
    val forwardedElement =
      P.repSep(forwardedPair, 1, P.char(';'))
        .map(pairs => pairs.tail.foldLeft(pairs.head.create)((z, x) => x.merge(z)))

    // Forwarded = 1#forwarded-element
    CommonRules
      .headerRep1(forwardedElement)
      .map(Forwarded.apply)
  }

  val name: CIString = ci"Forwarded"

  implicit val headerInstance: Header[Forwarded, Header.Recurring] =
    Header.createRendered(
      name,
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[Forwarded] =
    (a, b) => Forwarded(a.values.concatNel(b.values))

}

final case class Forwarded(values: NonEmptyList[Forwarded.Element])
