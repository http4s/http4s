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

import cats.parse.Parser0
import org.http4s._
import org.http4s.util.Renderer
import org.typelevel.ci._

private[headers] abstract class HeaderCompanion[A](_name: String) {

  protected def name: CIString = CIString(_name)

  private[http4s] val parser: Parser0[A]

  implicit val headerInstance: Header[A, _ <: Header.Type]

  private val invalidHeader = s"Invalid ${_name} header"

  def parse(s: String): ParseResult[A] =
    ParseResult.fromParser(parser, invalidHeader)(s)

  protected def create[T <: Header.Type](value_ : A => String): Header[A, T] =
    Header.create(name, value_, parse)

  protected def createRendered[T <: Header.Type, B: Renderer](value_ : A => B): Header[A, T] =
    Header.createRendered(name, value_, parse)
}
