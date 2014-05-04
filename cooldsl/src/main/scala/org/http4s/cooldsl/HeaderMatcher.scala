package org.http4s
package cooldsl

import shapeless.{HList, HNil, ::}
import shapeless.ops.hlist.Prepend

import scala.language.existentials
import org.http4s.cooldsl.bits.{QueryParser, StringParser}

/////////////////////// Implementation bits //////////////////////////////////////////////////////

sealed trait HeaderRule[T <: HList] extends HeaderRuleSyntax[T] {

  final def or(v: HeaderRule[T]): HeaderRule[T] = Or(this, v)

  final def ||(v: HeaderRule[T]): HeaderRule[T] = or(v)

  final def and[T1 <: HList](v: HeaderRule[T1])(implicit prepend : Prepend[T, T1]) : HeaderRule[prepend.Out] = And(this, v)

  final def &&[T1 <: HList](v: HeaderRule[T1])(implicit prepend : Prepend[T, T1]) : HeaderRule[prepend.Out] = and(v)
}

/* this exists only to force method consistency on the Route and HeaderRules,
   not to play a role in the type tree */
private[cooldsl] trait HeaderRuleSyntax[T <: HList] {

  def or(v: HeaderRule[T]): HeaderRuleSyntax[T]

  def ||(v: HeaderRule[T]): HeaderRuleSyntax[T]

  def and[T1 <: HList](v: HeaderRule[T1])(implicit prepend : Prepend[T, T1]) : HeaderRuleSyntax[prepend.Out]

  def &&[T1 <: HList](v: HeaderRule[T1])(implicit prepend : Prepend[T, T1]) : HeaderRuleSyntax[prepend.Out]
}

///////////////// Header and body AST ///////////////////////

private[cooldsl] case class And[T <: HList, T2 <: HList, T3 <: HList](a: HeaderRule[T2], b: HeaderRule[T3]) extends HeaderRule[T]

private[cooldsl] case class Or[T <: HList](a: HeaderRule[T], b: HeaderRule[T]) extends HeaderRule[T]

private[cooldsl] case class HeaderRequire[H <: HeaderKey.Extractable](key: H, f: H#HeaderT => Boolean) extends HeaderRule[HNil]

private[cooldsl] case class HeaderCapture[T <: Header](key: HeaderKey.Extractable) extends HeaderRule[T::HNil]

private[cooldsl] case class HeaderMapper[H <: HeaderKey.Extractable, R](key: H, f: H#HeaderT => R) extends HeaderRule[R::HNil]

private[cooldsl] case class QueryRule[T](name: String, p: QueryParser[T]) extends HeaderRule[T::HNil]

private[cooldsl] object EmptyHeaderRule extends HeaderRule[HNil]