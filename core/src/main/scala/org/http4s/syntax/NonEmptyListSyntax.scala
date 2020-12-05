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

package org.http4s.syntax

import cats.data.NonEmptyList

trait NonEmptyListSyntax {
  @deprecated("Use cats.foldable._", "0.18.5")
  implicit def http4sNonEmptyListSyntax[A](l: NonEmptyList[A]): NonEmptyListOps[A] =
    new NonEmptyListOps[A](l)
}

final class NonEmptyListOps[A](val self: NonEmptyList[A]) extends AnyVal {
  @deprecated("Use cats.foldable._", "0.18.5")
  def contains(a: A): Boolean =
    if (a == self.head) true
    else self.tail.contains(a)

  @deprecated("Use cats.foldable._", "0.18.5")
  def collectFirst[B](pf: PartialFunction[A, B]): Option[B] =
    pf.lift(self.head).orElse(self.tail.collectFirst(pf))

  @deprecated("Use cats.foldable._", "0.18.5")
  def mkString(s: String): String =
    self.toList.mkString(s)

  @deprecated("Use cats.foldable._", "0.18.5")
  def mkString(begin: String, s: String, end: String): String =
    self.toList.mkString(begin, s, end)
}
