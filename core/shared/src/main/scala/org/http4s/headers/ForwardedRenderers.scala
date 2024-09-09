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

import cats.Eval
import cats.syntax.flatMap._
import org.http4s.Uri
import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderer
import org.http4s.util.Writer

import java.nio.charset.StandardCharsets

/** Renderers for the [[Forwarded]] header models.
  */
private[http4s] trait ForwardedRenderers {
  import Forwarded._

  implicit val http4sForwardedNodeNameRenderer: Renderer[Node.Name] =
    new Renderer[Node.Name] {
      override def render(writer: Writer, nodeName: Node.Name): writer.type =
        nodeName match {
          case Node.Name.Ipv4(ipv4addr) => writer << ipv4addr.toUriString
          case Node.Name.Ipv6(ipv6addr) => writer << ipv6addr.toUriString
          case Node.Name.Unknown => writer << "unknown"
          case Node.Obfuscated(str) => writer << str
        }
    }

  implicit val http4sForwardedNodePortRenderer: Renderer[Node.Port] = new Renderer[Node.Port] {
    override def render(writer: Writer, nodePort: Node.Port): writer.type =
      nodePort match {
        case Node.Port.Numeric(num) => writer << num
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
        case Uri.RegName(rname) =>
          // TODO: A workaround for #1651, remove when the former issue gets fixed.
          writer << Uri.encode(rname.toString, StandardCharsets.ISO_8859_1, toSkip = RegNameChars)
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
          if (CommonRules.token.parseAll(rendered).isRight)
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
