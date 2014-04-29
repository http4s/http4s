package org.http4s
package cooldsl

import shapeless.{HList, HNil, ::}
import shapeless.ops.hlist.Prepend

import BodyCodec._

import scala.language.existentials

object HeaderMatcher {
  ////////////////// Api /////////////////////////////////////////////////////////////

  /* Checks that the header exists */
  def require(header: HeaderKey.Extractable): HeaderRule[HNil] = requireThat(header)(_ => true)

  /* Check that the header exists and satisfies the condition */
  def requireThat[H <: HeaderKey.Extractable](header: H)
                                             (implicit f: H#HeaderT => Boolean = {_: H#HeaderT => true}): HeaderRule[HNil] =
    HeaderRequire[H](header, f)

  /** requires the header and will pull this header from the pile and put it into the function args stack */
  def capture[H <: HeaderKey.Extractable](key: H): HeaderRule[H#HeaderT::HNil] =
    HeaderCapture[H#HeaderT](key)

  def map[H <: HeaderKey.Extractable, R](key: H)(f: H#HeaderT => R): HeaderRule[R::HNil] =
    HeaderMapper[H, R](key, f)

  /////////////////////// Implementation bits //////////////////////////////////////////////////////

  sealed trait HeaderRule[T <: HList] {

    def or(v: HeaderRule[T]): HeaderRule[T] = Or(this, v)

    def and[T1 <: HList](v: HeaderRule[T1])(implicit prepend : Prepend[T, T1]) : HeaderRule[prepend.Out] =
      And(this, v)

    final def &&[T1 <: HList](v: HeaderRule[T1])(implicit prepend : Prepend[T, T1]) : HeaderRule[prepend.Out] = and(v)

    final def ||(v: HeaderRule[T]): HeaderRule[T] = or(v)
  }

  ///////////////// Header and body AST ///////////////////////

  case class And[T <: HList, T2 <: HList, T3 <: HList](a: HeaderRule[T2], b: HeaderRule[T3])
    extends HeaderRule[T]

  case class Or[T <: HList](a: HeaderRule[T], b: HeaderRule[T]) extends HeaderRule[T]

  case class HeaderRequire[H <: HeaderKey.Extractable](key: H, f: H#HeaderT => Boolean) extends HeaderRule[HNil]

  case class HeaderCapture[T <: Header](key: HeaderKey.Extractable) extends HeaderRule[T::HNil]

  case class HeaderMapper[H <: HeaderKey.Extractable, R](key: H, f: H#HeaderT => R) extends HeaderRule[R::HNil]

  object EmptyHeaderRule extends HeaderRule[HNil]
}