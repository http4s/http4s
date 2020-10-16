/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.parser

import cats.data.NonEmptyList
import cats.syntax.either._
import org.http4s.headers.Forwarded
import org.http4s.internal.bug
import org.http4s.syntax.literals._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{ParseFailure, Uri}
import org.specs2.execute.Result
import org.specs2.mutable.{Specification, Tables}

class ForwardedHeaderSpec extends Specification with Tables {
  import Forwarded.Element
  import ForwardedHeaderSpec._

  "FORWARDED" should {
    def parse(input: String) = HttpHeaderParser.FORWARDED(input)

    "parse" >> {
      "single simple elements" in {
        "Header String" | "Parsed Model" |
          "by=4.3.2.1" ! Element.fromBy(uri"//4.3.2.1") |
          "for=\"[1:2:0::0:3:4]:56\"" ! Element.fromFor(uri"//[1:2::3:4]:56") |
          "BY=\"_a.b-r.a_:_k.a-d._.b-r.a\"" ! Element.fromBy(uri"//_a.b-r.a_#_k.a-d._.b-r.a") |
          "for=unknown" ! Element.fromFor(uri"//unknown") |
          "by=\"unknown:451\"" ! Element.fromBy(uri"//unknown:451") |
          "For=\"unknown:__p.0_r.t-\"" ! Element.fromFor(uri"//unknown#__p.0_r.t-") |
          "host=http4s.org" ! Element.fromHost(uri"//http4s.org") |
          "hOSt=\"http4s.org:12345\"" ! Element.fromHost(uri"//http4s.org:12345") |
          "host=\"1.2.3.4:567\"" ! Element.fromHost(uri"//1.2.3.4:567") |
          "host=\"[8:7:6:5:4:3:2:1]\"" ! Element.fromHost(uri"//[8:7:6:5:4:3:2:1]") |
          "proto=http" ! Element.fromProto(scheme"http") |
          "proto=\"https\"" ! Element.fromProto(scheme"https") |
          "prOtO=gopher" ! Element.fromProto(scheme"gopher") |> { (headerStr, parsedMod) =>
            parse(headerStr) must beRight((_: Forwarded).values ==== NEL(parsedMod))
          }
      }
      "single compound elements" in {
        "Header String" | "Parsed Model" |
          "by=_abra;for=_kadabra" !
          Element.fromBy(uri"//_abra").withFor(uri"//_kadabra") |
          "by=_abra;for=_kadabra;host=http4s.org" !
          Element.fromBy(uri"//_abra").withFor(uri"//_kadabra").withHost(uri"//http4s.org") |
          "by=_abra;for=_kadabra;host=\"http4s.org\";proto=http" !
          Element
            .fromBy(uri"//_abra")
            .withFor(uri"//_kadabra")
            .withHost(uri"//http4s.org")
            .withProto(scheme"http") |
          "for=_kadabra;by=_abra;proto=http;host=http4s.org" !
          Element
            .fromBy(uri"//_abra")
            .withFor(uri"//_kadabra")
            .withHost(uri"//http4s.org")
            .withProto(scheme"http") |
          "host=http4s.org;for=_kadabra;proto=http;by=_abra" !
          Element
            .fromBy(uri"//_abra")
            .withFor(uri"//_kadabra")
            .withHost(uri"//http4s.org")
            .withProto(scheme"http") |> { (headerStr, parsedMod) =>
            parse(headerStr) must beRight((_: Forwarded).values ==== NEL(parsedMod))
          }
      }
      "multi elements" in {
        "Header String" | "Parsed Model" |
          "by=_foo, for=_bar , host=foo.bar ,proto=foobar" !
          NEL(
            Element.fromBy(uri"//_foo"),
            Element.fromFor(uri"//_bar"),
            Element.fromHost(uri"//foo.bar"),
            Element.fromProto(scheme"foobar")
          ) |
          "by=_foo;for=_bar , host=foo.bar;proto=foobar" !
          NEL(
            Element.fromBy(uri"//_foo").withFor(uri"//_bar"),
            Element.fromHost(uri"//foo.bar").withProto(scheme"foobar")
          ) |
          "by=_foo ,for=_bar;host=foo.bar, proto=foobar" !
          NEL(
            Element.fromBy(uri"//_foo"),
            Element.fromFor(uri"//_bar").withHost(uri"//foo.bar"),
            Element.fromProto(scheme"foobar")
          ) |> { (headerStr, parsedMod) =>
            parse(headerStr) must beRight((_: Forwarded).values ==== parsedMod)
          }
      }
    }
    "fail to parse" >> {
      "unknown parameter" in {
        Result.foreach(
          Seq(
            "bye=1.2.3.4",
            "four=_foobar",
            "ghost=foo.bar",
            "proot=http"
          )) {
          parse(_) must beLeft((_: ParseFailure).sanitized ==== "Invalid header")
        }
      }
      "unquoted non-token" in {
        Result.foreach(
          Seq(
            "by=[1:2:3::4:5:6]",
            "for=_abra:_kadabra",
            "host=foo.bar:123"
          )) {
          parse(_) must beLeft((_: ParseFailure).sanitized ==== "Invalid header")
        }
      }
    }
  }
}

object ForwardedHeaderSpec {
  import Forwarded.{Host, Node}

  private val ObfuscatedRe = """^(_[\p{Alnum}\.\_\-]+)$""".r

  private object UnCIString {
    def unapply(cistr: CaseInsensitiveString): Option[String] = Some(cistr.value)
  }

  implicit def convertUriToNode(uri: Uri): Node =
    Node(
      uri.host match {
        case Some(ipv4: Uri.Ipv4Address) => Node.Name.Ipv4(ipv4)
        case Some(ipv6: Uri.Ipv6Address) => Node.Name.Ipv6(ipv6)
        case Some(Uri.RegName(UnCIString("unknown"))) => Node.Name.Unknown
        case Some(Uri.RegName(UnCIString(ObfuscatedRe(obfuscatedName)))) =>
          Node.Obfuscated(obfuscatedName)
        case Some(other) => throw bug(s"not allowed as host for node: $other")
        case _ => throw bug(s"no host in URI: $uri")
      },
      uri.port.flatMap(Node.Port.fromInt(_).toOption).orElse {
        // Convention: use the URI fragment to define an obfuscated port.
        uri.fragment.flatMap {
          PartialFunction.condOpt(_) { case ObfuscatedRe(obfuscatedPort) =>
            Node.Obfuscated(obfuscatedPort)
          }
        }
      }
    )

  implicit def convertUriToHost(uri: Uri): Host = Host.fromUri(uri).valueOr(throw _)

  /** Just a shortcut for `NonEmptyList.of()` */
  private def NEL[A](head: A, tail: A*) = NonEmptyList.of[A](head, tail: _*)
}
