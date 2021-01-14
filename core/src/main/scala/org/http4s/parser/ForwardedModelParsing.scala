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

package org.http4s.parser

import java.nio.charset.{Charset, StandardCharsets}

import org.http4s.headers.Forwarded
import org.http4s.internal.parboiled2.CharPredicate.{AlphaNum, Digit}
import org.http4s.internal.parboiled2._
import org.http4s.{Uri => UriModel}

import scala.util.Try

private[http4s] trait ForwardedModelParsing { model: Forwarded.type =>

  private[ForwardedModelParsing] sealed trait ModelParsers extends Rfc3986Parser { rfc =>

    override final def charset: Charset = StandardCharsets.ISO_8859_1

    protected final def ModelNode: Rule1[model.Node] =
      rule {
        (ModelNodeName ~ optional(':' ~ ModelNodePort)) ~> {
          model.Node(_: model.Node.Name, _: Option[model.Node.Port])
        }
      }

    protected final def ModelNodeName: Rule1[model.Node.Name] =
      rule {
        (rfc.ipv4Bytes ~> ((a: Byte, b: Byte, c: Byte, d: Byte) =>
          model.Node.Name.ofIpv4Address(a, b, c, d))) |
          ('[' ~ rfc.ipv6Address ~ ']' ~> model.Node.Name.Ipv6) |
          ("unknown" ~ push(model.Node.Name.Unknown)) |
          ModelNodeObfuscated
      }

    protected final def ModelNodePort: Rule1[model.Node.Port] =
      rule {
        capture((1 to 5).times(Digit)) ~> {
          modelNodePortFromString(_) match {
            case Some(port) => push(port)
            case None => failX(s"incorrect port number")
          }
        } | ModelNodeObfuscated
      }

    private def modelNodePortFromString(str: String): Option[model.Node.Port] =
      Try(Integer.parseUnsignedInt(str)).toOption.flatMap(model.Node.Port.fromInt(_).toOption)

    protected final def ModelNodeObfuscated: Rule1[model.Node.Obfuscated] =
      rule {
        capture('_' ~ oneOrMore(AlphaNum | '.' | '_' | '-')) ~> { str =>
          model.Node.Obfuscated(str)
        }
      }

    protected final def ModelHost: Rule1[model.Host] =
      rule {
        rfc.Host ~ rfc.Port ~> { (uriHost: UriModel.Host, portNum: Option[Int]) =>
          model.Host.fromHostAndMaybePort(uriHost, portNum) match {
            case Left(failure) => failX(failure.message)
            case Right(modelHost) => push(modelHost)
          }
        }
      }
  }

  protected final class ModelNodeParser(s: String)
      extends Http4sParser[model.Node](s, s"invalid node '$s'")
      with ModelParsers {

    override def main: Rule1[Node] = rule(ModelNode ~ EOI)
  }

  protected final class ModelNodeObfuscatedParser(s: String)
      extends Http4sParser[model.Node.Obfuscated](s, s"invalid obfuscated value '$s'")
      with ModelParsers {

    override def main: Rule1[model.Node.Obfuscated] = rule(ModelNodeObfuscated ~ EOI)
  }

  protected final class ModelHostParser(s: String)
      extends Http4sParser[model.Host](s, s"invalid host '$s'")
      with ModelParsers {

    override def main: Rule1[model.Host] = rule(ModelHost ~ EOI)
  }
}
