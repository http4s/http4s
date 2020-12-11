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

package org.http4s.util

import cats.{Monoid, Order, Show}
import org.http4s.internal.{hashLower => ciHash}

/** A String wrapper such that two strings `x` and `y` are equal if
  * `x.value.equalsIgnoreCase(y.value)`
  */
sealed class CaseInsensitiveString private (val value: String)
    extends CharSequence
    with Ordered[CaseInsensitiveString] {
  import CaseInsensitiveString._

  /* Lazily cache the hash code.  This is nearly identical to the
   * hashCode of java.lang.String, but converting to lower case on
   * the fly to avoid copying `value`'s character storage.
   */
  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0)
      hash = ciHash(value)
    hash
  }

  override def equals(obj: Any): Boolean =
    obj match {
      case that: CaseInsensitiveString => value.equalsIgnoreCase(that.value)
      case _ => false
    }

  override def toString: String = value

  override def charAt(n: Int): Char =
    toString.charAt(n)

  override def length(): Int =
    value.length

  override def subSequence(start: Int, end: Int): CaseInsensitiveString =
    apply(value.subSequence(start, end))

  override def compare(other: CaseInsensitiveString): Int =
    value.compareToIgnoreCase(other.value)
}

object CaseInsensitiveString extends CaseInsensitiveStringInstances {
  val empty: CaseInsensitiveString =
    CaseInsensitiveString("")

  def apply(cs: CharSequence): CaseInsensitiveString =
    new CaseInsensitiveString(cs.toString)
}

private[http4s] sealed trait CaseInsensitiveStringInstances {
  implicit val http4sMonoidForCaseInsensitiveString: Monoid[CaseInsensitiveString] =
    new Monoid[CaseInsensitiveString] {
      override def empty = CaseInsensitiveString.empty

      override def combine(x: CaseInsensitiveString, y: CaseInsensitiveString) =
        CaseInsensitiveString(x.value + y.value)
    }

  implicit val http4sOrderForCaseInsensitiveString: Order[CaseInsensitiveString] =
    Order.fromOrdering

  implicit val http4sShowForCaseInsensitiveString: Show[CaseInsensitiveString] =
    Show.fromToString
}
