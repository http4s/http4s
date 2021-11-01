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

import scalafix.v1._

import scala.meta._
import scala.meta.classifiers._
import scala.annotation.tailrec

package object fix {

  def show(tree: Tree)(implicit doc: SemanticDocument): Patch = {
    println("Tree.syntax: " + tree.syntax)
    println("Tree.structure: " + tree.structure)
    println("Tree.structureLabeled: " + tree.structureLabeled)
    println("Tree.symbol: " + tree.symbol)
    println()
    Patch.empty
  }

  implicit class TreeOps(private val self: Tree) extends AnyVal {
    @tailrec
    final def isDescendentOf[U](implicit classifier: Classifier[Tree, U]): Boolean =
      self.is[U] || {
        self.parent match {
          case None => false
          case Some(parent) => parent.isDescendentOf[U]
        }
      }
  }
}
