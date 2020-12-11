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

import java.nio.charset.StandardCharsets
import org.http4s.{Header, Uri}
import org.http4s.internal.parboiled2.Rule1

abstract class UriHeaderParser[A <: Header](value: String)
    extends Http4sHeaderParser[A](value)
    with Rfc3986Parser {
  override def charset: java.nio.charset.Charset = StandardCharsets.ISO_8859_1

  // Implementors should build a Header out of the uri
  def fromUri(uri: Uri): A

  def entry: Rule1[A] =
    rule {
      // https://tools.ietf.org/html/rfc3986#section-4.1
      (AbsoluteUri | RelativeRef) ~> { (a: Uri) =>
        fromUri(a)
      }
    }
}
