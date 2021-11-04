/*
 * Copyright 2021 http4s.org
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

package fix

import scalafix.v1._

import scala.meta._

class Fs2Linters extends SemanticRule("Http4sFs2Linters") {
  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect { case Stream_compile_M(t @ Term.Select(_, _)) =>
      t.synthetics.collect { case ApplyTree(_, List(ApplyTree(_, List(target)))) =>
        target.symbol.collect { case Target_forSync_M(_) =>
          Patch.lint(NoFs2SyncCompiler(t))
        }.asPatch
      }.asPatch
    }.asPatch

  val Stream_compile_M = SymbolMatcher.exact("fs2/Stream#compile().")
  val Target_forSync_M = SymbolMatcher.exact("fs2/Compiler.TargetLowPriority#forSync().")
}

final case class NoFs2SyncCompiler(t: Tree) extends Diagnostic {
  override def message: String =
    "fs2's Sync compiler should be avoided. Usually this means a Sync constraint needs to be changed to Concurrent or upgraded to Async."

  override def position: Position = t.pos

  override def categoryID: String = "noFs2SyncCompiler"
}
