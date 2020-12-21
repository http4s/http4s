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

import org.specs2.Specification
import org.typelevel.discipline.specs2.Discipline
import cats._
import cats.syntax.all._
import cats.laws.discipline.NonEmptyTraverseTests
import org.http4s.laws.discipline.arbitrary._

class ContextRequestSpec extends Specification with Discipline {
  implicit def nonBodyEquality[F[_], A: Eq]: Eq[ContextRequest[F, A]] =
    Eq.instance { case (first, second) =>
      first.context === second.context &&
        first.req === second.req
    }

  def is =
    checkAll(
      "ContextRequest[F, *]",
      NonEmptyTraverseTests[ContextRequest[Option, *]]
        .nonEmptyTraverse[Option, Int, Int, Int, Int, Option, Option]
    )
}
