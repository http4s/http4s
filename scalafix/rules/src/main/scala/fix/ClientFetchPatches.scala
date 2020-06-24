/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package fix

import scalafix.v1._

import scala.meta._

object ClientFetchPatches {
  def apply(tree: Tree)(implicit doc: SemanticDocument): List[Patch] =
    tree
      .collect {

        // Match `[client.]fetch(req)(fun)` for `req: Request[F]`
        case subtree @ ClientFetchReqFunCall(
              maybeClient,
              applyTypesStr,
              AsApplyParamString(req),
              AsApplyParamString(fun)) =>
          Patch.replaceTree(subtree, s"${asCalleeString(maybeClient)}run$req.use$applyTypesStr$fun")

        // Match `_.fetch(req)(fun)` for `req: F[Request[F]]`
        case subtree @ ClientFetchFReqFunCall(
              Some(Term.Placeholder()),
              applyTypesStr,
              req,
              AsApplyParamString(fun)) =>
          Patch.replaceTree(subtree, s"client => $req.flatMap(client.run(_).use$applyTypesStr$fun)")

        // Match `[client.]fetch(req)(fun)` for `req: F[Request[F]]`
        case subtree @ ClientFetchFReqFunCall(
              maybeClient,
              applyTypesStr,
              req,
              AsApplyParamString(fun)) =>
          Patch.replaceTree(
            subtree,
            s"$req.flatMap(${asCalleeString(maybeClient)}run(_).use$applyTypesStr$fun)")
      }

  private val clientClassSymbol = Symbol("org/http4s/client/Client#")
  private val requestClassSymbol = Symbol("org/http4s/Request#")

  private object IsSymbolATypeOfClientClass {
    def unapply(sym: Symbol)(implicit doc: Symtab): Boolean = sym.isTypeOf(clientClassSymbol)
  }

  private object RequestValueEffectTypeSymbol {
    def unapply(tpe: SemanticType): Option[Symbol] =
      PartialFunction.condOpt(tpe) {
        case TypeRef(_, `requestClassSymbol`, List(TypeRef(_, effectTypeSym, Nil))) => effectTypeSym
      }
    def unapply(symInfo: SymbolInformation): Option[Symbol] =
      PartialFunction.condOpt(symInfo.signature) {
        case ValueSignature(RequestValueEffectTypeSymbol(effectTypeSym)) => effectTypeSym
      }
    def unapply(sym: Symbol)(implicit doc: Symtab): Option[Symbol] = sym.info.flatMap(unapply)
  }
  private object EffectRequestValueTypeSymbol {
    def unapply(tpe: SemanticType): Option[Symbol] =
      PartialFunction.condOpt(tpe) {
        case TypeRef(_, effectTypeSym1, List(RequestValueEffectTypeSymbol(effectTypeSym2)))
            if effectTypeSym1 == effectTypeSym2 =>
          effectTypeSym1
      }
    def unapply(sym: Symbol)(implicit doc: Symtab): Option[Symbol] =
      sym.info.map(_.signature).collect {
        case ValueSignature(EffectRequestValueTypeSymbol(effectTypeSym)) => effectTypeSym
      }
  }

  private object IsClientFetchReqMethod {
    def unapply(sig: MethodSignature): Boolean =
      PartialFunction.cond(sig.parameterLists) {
        case List(List(RequestValueEffectTypeSymbol(_)), _) => true
      }
  }
  private object IsClientFetchFReqMethod {
    def unapply(sig: MethodSignature)(implicit doc: Symtab): Boolean =
      PartialFunction.cond(sig.parameterLists) {
        case List(
              List(
                XSignature(
                  ValueSignature(
                    TypeRef(
                      _,
                      XSymbol.Owner(IsSymbolATypeOfClientClass()),
                      List(RequestValueEffectTypeSymbol(_)))
                  ))),
              _) =>
          true
      }
  }
  private object ClientFetchMethod {
    def unapply(term: Term.Name)(implicit doc: SemanticDocument): Option[MethodSignature] =
      PartialFunction.condOpt(term) {
        case Term.Name("fetch") &
            XSymbol(
              XSymbol.Owner(IsSymbolATypeOfClientClass()) & XSignature(
                fetchSig: MethodSignature)) =>
          fetchSig
      }
  }
  private object ClientFetchCall {
    def unapply(tree: Tree)(implicit doc: SemanticDocument)
        : Option[(MethodSignature, Option[Term], Option[(SemanticType, Type)], Term, Term)] =
      PartialFunction.condOpt(tree) {
        case // fetch(fun)(req)
            Term.Apply(Term.Apply(ClientFetchMethod(methodSig), List(req)), List(fun)) =>
          (methodSig, None, None, req, fun)

        case // client.fetch(fun)(req)
            Term.Apply(
              Term.Apply(Term.Select(callee, ClientFetchMethod(methodSig)), List(req)),
              List(fun)) =>
          (methodSig, Some(callee), None, req, fun)

        case // fetch[A](fun)(req)
            Term.Apply(
              Term.Apply(
                Term.ApplyType(ClientFetchMethod(methodSig), List(entityType)),
                List(req @ XSemanticType(effectSemType))),
              List(fun)) =>
          (methodSig, None, Some((effectSemType, entityType)), req, fun)

        case // fetch[A](fun)(req)
            Term.Apply(
              Term.Apply(Term.ApplyType(ClientFetchMethod(methodSig), List(_)), List(req)),
              List(fun)) =>
          (methodSig, None, None, req, fun)

        case // client.fetch[A](fun)(req)
            Term.Apply(
              Term.Apply(
                Term.ApplyType(Term.Select(callee, ClientFetchMethod(methodSig)), List(entityType)),
                List(req @ XSemanticType(effectSemType))),
              List(fun)) =>
          (methodSig, Some(callee), Some((effectSemType, entityType)), req, fun)

        case // client.fetch[A](fun)(req)
            Term.Apply(
              Term.Apply(
                Term.ApplyType(Term.Select(callee, ClientFetchMethod(methodSig)), List(_)),
                List(req)),
              List(fun)) =>
          (methodSig, Some(callee), None, req, fun)
      }
  }

  private object ClientFetchReqFunCall {
    def unapply(tree: Tree)(implicit
        doc: SemanticDocument): Option[(Option[Term], String, Term, Term)] =
      PartialFunction.condOpt(tree) {
        case ClientFetchCall(IsClientFetchReqMethod(), maybeCallee, None, req, fun) =>
          (maybeCallee, "", req, fun)

        case ClientFetchCall(
              IsClientFetchReqMethod(),
              maybeCallee,
              Some((RequestValueEffectTypeSymbol(effectTypeSym), entityType)),
              req,
              fun) =>
          (maybeCallee, asApplyTypesString(effectTypeSym, entityType), req, fun)
      }
  }

  private object ClientFetchFReqFunCall {
    def unapply(tree: Tree)(implicit
        doc: SemanticDocument): Option[(Option[Term], String, Term, Term)] =
      PartialFunction.condOpt(tree) {
        case ClientFetchCall(IsClientFetchFReqMethod(), maybeCallee, None, req, fun) =>
          (maybeCallee, "", req, fun)

        case ClientFetchCall(
              IsClientFetchFReqMethod(),
              maybeCallee,
              Some((EffectRequestValueTypeSymbol(effectTypeSym), entityType)),
              req,
              fun) =>
          (maybeCallee, asApplyTypesString(effectTypeSym, entityType), req, fun)
      }
  }

  private def asCalleeString(maybeCalleeTerm: Option[Term]) = maybeCalleeTerm.fold("")(_.toString + ".")

  private def asApplyTypesString(effectTypeSym: Symbol, entityType: Type) =
    s"[${effectTypeSym.displayName}, $entityType]"

  private object AsApplyParamString {
    def unapply(term: Term): Option[String] =
      PartialFunction.condOpt(term) {
        case XTerm.IsBlockOrPartFunc() => s" $term"
        case _ => s"($term)"
      }
  }
}
