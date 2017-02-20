/*
 * Copyright (C) 2009-2013 Mathias Doenitz, Alexander Myltsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parboiled2.support

import scala.annotation.implicitNotFound

@implicitNotFound("The `optional`, `zeroOrMore`, `oneOrMore` and `times` modifiers " +
  "can only be used on rules of type `Rule0`, `Rule1[T]` and `Rule[I, O <: I]`!")
private[http4s] sealed trait Lifter[M[_], I <: HList, O <: HList] {
  type In <: HList
  type StrictOut <: HList
  type OptionalOut <: HList
}

private[http4s] object Lifter extends LowerPriorityLifter {
  implicit def forRule0[M[_]]: Lifter[M, HNil, HNil] {
    type In = HNil
    type StrictOut = HNil
    type OptionalOut = StrictOut
  } = `n/a`

  implicit def forRule1[M[_], T]: Lifter[M, HNil, T :: HNil] {
    type In = HNil
    type StrictOut = M[T] :: HNil
    type OptionalOut = StrictOut
  } = `n/a`
}

private[http4s] sealed abstract class LowerPriorityLifter {
  implicit def forReduction[M[_], L <: HList, R <: L]: Lifter[M, L, R] {
    type In = L
    type StrictOut = R
    type OptionalOut = L
  } = `n/a`
}
