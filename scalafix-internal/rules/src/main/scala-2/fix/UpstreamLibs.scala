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

class UpstreamLibs extends SemanticRule("Http4sUpstreamLibs") {
  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case Term.Apply(
            Stream_map_M(Term.Select(_, map)),
            List(
              f @ Term.Function(List(Term.Param(List(), Name.Anonymous(), None, None)), result))) =>
        Patch.replaceTree(map, "as") + Patch.replaceTree(f, result.syntax)
    }.asPatch

  val Stream_map_M = SymbolMatcher.exact("fs2/Stream#map().")
}
