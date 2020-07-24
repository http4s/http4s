/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import scalafix.v1._

import scala.meta._

package object fix {

  /** Allows to apply multiple extractors/matchers to a single expression in a `case` block.
    */
  object & {
    def unapply[A](a: A): Option[(A, A)] = Some((a, a))
  }

  object XTerm {
    // Matches if the term has curly braces.
    object IsBlockOrPartFunc {
      def unapply(term: Term): Boolean =
        PartialFunction.cond(term) {
          case Term.Block(_) | Term.PartialFunction(_) => true
        }
    }
  }

  object XSymbol {
    def unapply(tree: Tree)(implicit doc: SemanticDocument): Option[Symbol] = Some(tree.symbol)

    object Owner {
      def unapply(sym: Symbol): Option[Symbol] = Some(sym.owner)
    }
  }

  implicit final class XSymbolOps(val self: Symbol) extends AnyVal {
    def isTypeOf(that: Symbol)(implicit doc: Symtab): Boolean =
      PartialFunction.cond(self) {
        case `that` => true
        case XSignature(ClassSignature(_, parents, _, _)) =>
          parents.iterator
            .collect { case TypeRef(_, parentSym, _) => parentSym }
            .exists(_.isTypeOf(that))
      }
  }

  object XSignature {
    def unapply(symInfo: SymbolInformation): Option[Signature] = Some(symInfo.signature)
    def unapply(sym: Symbol)(implicit doc: Symtab): Option[Signature] = sym.info.flatMap(unapply)
  }

  object XSemanticType {

    /** Tries to infer a result SemanticType of a particular Stat.
      *
      * @note Not all possible cases are handled.
      */
    def unapply(stat: Stat)(implicit doc: SemanticDocument): Option[SemanticType] =
      PartialFunction.condOpt(stat) {
        case Term.Name(_) & XSymbol(XSignature(ValueSignature(tpe))) => tpe

        case Term.Apply(_, _) &
            XSymbol(XSignature(ValueSignature(TypeRef(_, funcSym, _ :+ tpe))))
            if funcSym.value.startsWith("scala/Function") && funcSym.value.endsWith("#") =>
          tpe

        case Term.Block(_ :+ XSemanticType(tpe)) => tpe
      }
  }
}
