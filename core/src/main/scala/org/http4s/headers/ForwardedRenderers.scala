/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.headers

import java.nio.charset.StandardCharsets

import cats.Eval
import cats.syntax.flatMap._
import org.http4s.Uri
import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{Renderer, Writer}

/** Renderers for the [[Forwarded]] header models.
  */
private[http4s] trait ForwardedRenderers {
  import Forwarded._

  implicit val http4sForwardedNodeNameRenderer: Renderer[Node.Name] =
    new Renderer[Node.Name] {
      override def render(writer: Writer, nodeName: Node.Name): writer.type =
        nodeName match {
          case Node.Name.Ipv4(ipv4addr) => writer << ipv4addr
          case Node.Name.Ipv6(ipv6addr) => writer << '[' << ipv6addr << ']'
          case Node.Name.Unknown => writer << "unknown"
          case Node.Obfuscated(str) => writer << str
        }
    }

  implicit val http4sForwardedNodePortRenderer: Renderer[Node.Port] = new Renderer[Node.Port] {
    override def render(writer: Writer, nodePort: Node.Port): writer.type =
      nodePort match {
        case Node.Port(num) => writer << num
        case Node.Obfuscated(str) => writer << str
      }
  }

  implicit val http4sForwardedNodeRenderer: Renderer[Node] = new Renderer[Node] {
    override def render(writer: Writer, node: Node): writer.type = {
      writer << node.nodeName
      node.nodePort.fold[writer.type](writer)(writer << ':' << _)
    }
  }

  implicit val http4sForwardedHostRenderer: Renderer[Host] = new Renderer[Host] {
    // See in `Rfc3986Parser`: `RegName` -> `SubDelims`
    private val RegNameChars = Uri.Unreserved ++ "!$&'()*+,;="

    override def render(writer: Writer, host: Host): writer.type = {
      host.host match {
        case Uri.RegName(name) =>
          // TODO: A workaround for #1651, remove when the former issue gets fixed.
          writer << Uri.encode(name.toString, StandardCharsets.ISO_8859_1, toSkip = RegNameChars)
        case other =>
          writer << other
      }
      host.port.fold[writer.type](writer)(writer << ':' << _)
    }
  }

  protected def renderElement(writer: Writer, elem: Element): writer.type = {

    def renderParamEval[A: Renderer](name: String, maybeValue: Option[A]) =
      maybeValue.map { value =>
        // Do not write it immediately since we're going to interleave existing parameters with ';'
        Eval.always[writer.type] { // NOTE: not clear why the explicit type is necessary here
          writer << name << '='

          val rendered = Renderer.renderString(value)
          if (Rfc2616BasicRules.isToken(rendered))
            writer << rendered
          else
            writer <<# rendered // quote non-token values
        }
      }.toList

    {
      import elem._
      renderParamEval("by", maybeBy) :::
        renderParamEval("for", maybeFor) :::
        renderParamEval("host", maybeHost) :::
        renderParamEval("proto", maybeProto)
    }.reduceLeft { (leftParamEval, rightParamEval) =>
      // Interleave every couple of parameters with ';'
      leftParamEval >> Eval.always(writer << ';') >> rightParamEval
    }.value // all actual rendering happens here
  }
}
