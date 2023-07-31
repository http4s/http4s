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

package org.http4s
package syntax

import cats.data.NonEmptyList
import org.http4s.util.Renderer
import org.typelevel.ci.CIString

trait HeaderSyntax {
  implicit def http4sHeaderSyntax[A](a: A)(implicit header: Header[A, _]): HeaderOps[A] =
    new HeaderOps(a, header)

  implicit def http4sSelectSyntaxOne[A](a: A)(implicit select: Header.Select[A]): SelectOpsOne[A] =
    new SelectOpsOne(a)

  implicit def http4sSelectSyntaxMultiple[A, H[_]](a: H[A])(implicit
      select: Header.Select.Aux[A, H]
  ): SelectOpsMultiple[A, H] =
    new SelectOpsMultiple[A, H](a)
}

final class HeaderOps[A](val a: A, header: Header[A, _]) {
  def value: String = header.value(a)
  def name: CIString = header.name
}

final class SelectOpsOne[A](val a: A)(implicit ev: Header.Select[A]) {
  def toRaw1: Header.Raw = ev.toRaw1(a)
  def renderString: String = Renderer.renderString(a)
}

final class SelectOpsMultiple[A, H[_]](val a: H[A])(implicit ev: Header.Select.Aux[A, H]) {
  def toRaw: NonEmptyList[Header.Raw] = ev.toRaw(a)
  def renderString: String = Renderer.renderString(toRaw)
}
