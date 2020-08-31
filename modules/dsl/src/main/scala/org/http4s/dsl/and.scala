/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.dsl

/** A conjunction extractor.  Generally used as an infix operator.
  *
  * {{{
  * scala> import org.http4s.dsl.&
  * scala> object Even { def unapply(i: Int) = (i % 2) == 0 }
  * scala> object Positive { def unapply(i: Int) = i > 0 }
  * scala> def describe(i: Int) = i match {
  *      |   case Even() & Positive() => "even and positive"
  *      |   case Even() => "even but not positive"
  *      |   case Positive() => "positive but not even"
  *      |   case _ => "neither even nor positive"
  *      | }
  * scala> describe(-1)
  * res0: String = neither even nor positive
  * scala> describe(0)
  * res1: String = even but not positive
  * scala> describe(1)
  * res2: String = positive but not even
  * scala> describe(2)
  * res3: String = even and positive
  * }}}
  */
object & {
  def unapply[A](a: A): Some[(A, A)] = Some((a, a))
}
