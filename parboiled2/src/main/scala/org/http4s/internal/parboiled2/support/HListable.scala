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

private[http4s] trait HListable[T] {
  type Out <: HList
}

private[http4s] object HListable extends LowerPriorityHListable {
  implicit def fromUnit: HListable[Unit] { type Out = HNil } = `n/a`
  implicit def fromHList[T <: HList]: HListable[T] { type Out = T } = `n/a`
}

private[http4s] abstract class LowerPriorityHListable {
  implicit def fromAnyRef[T]: HListable[T] { type Out = T :: HNil } = `n/a`
}
