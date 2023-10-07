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

package org.http4s.dsl

/** A conjunction extractor.  Generally used as an infix operator.
  *
  * {{{
  * scala> object Even { def unapply(i: Int) = (i % 2) == 0 }
  * scala> object Positive { def unapply(i: Int) = i > 0 }
  * scala> def describe(i: Int) = i match {
  *      |   case org.http4s.dsl.&(Even(), Positive()) => "even and positive"
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
