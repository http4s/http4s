package org.http4s
package cooldsl

import shapeless.{HList, HNil, ::}
import scalaz.concurrent.Task
import shapeless.ops.hlist.Prepend
import scodec.bits.ByteVector

import scalaz.stream.Process

import scala.language.existentials


/**
 *    StatusLine             Validator            Decoders
 *              \           /       \            /
 *
 *
 *
 *
 *
 */


object CoolApi {

  type Goal = Request => Option[Response]

  trait Validator[T <: HList] {

    def or(v: Validator[T]): Validator[T] = Or(this, v)

    def and[T1 <: HList](v: Validator[T1])(implicit prepend : Prepend[T, T1]) : Validator[prepend.Out] =
      And(this, v)
  }

  object EmptyValidator extends Validator[HNil]

  ///////////////// Logic operators ///////////////////////

  case class And[T <: HList, T2 <: HList, T3 <: HList](a: Validator[T2], b: Validator[T3])
    extends Validator[T]

  case class Or[T <: HList](a: Validator[T], b: Validator[T]) extends Validator[T] {
//    def matches(req: Request) = a.matches(req) || b.matches(req)
  }

  //////////////// Header validators //////////////////////

  case class HeaderValidator[H <: HeaderKey.Extractable](key: H, f: H#HeaderT => Boolean) extends Validator[HNil]

  case class HeaderCapture[T <: Header](key: HeaderKey.Extractable) extends Validator[T::HNil]

  case class HeaderMapper[H <: HeaderKey.Extractable, R](key: H, f: H#HeaderT => R) extends Validator[R::HNil]

  case class Runnable[T <: HList](m: Method, p: PathValidator[_ <: HList], h: Validator[_ <: HList]) {
    def ~>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal = compiler(this, f, hf)
  }

  ////////////////// Status line combinators //////////////////////////////////////////

  case class StatusValidator[T1 <: HList, T2 <: HList](m: Method, path: PathValidator[T1], hval: Validator[T2]) {

    def /(p: PathMatch): StatusValidator[T1,T2] = copy(path = path.and(p))

    def /[R](p: PathCapture[R]): StatusValidator[R::T1,T2] = copy(path = path.and(p))

    def || (p: PathValidator[T1]): StatusValidator[T1,T2] = copy(path = path.or(p))

    def compile(implicit prep: Prepend[T1, T2]): Runnable[prep.Out] = Runnable(m, path, hval)
  }

  trait PathValidator[T <: HList] {
    def and[T2 <: HList](p2: PathValidator[T2])(implicit prep: Prepend[T2,T]): PathValidator[prep.Out] =
      PathAnd(this, p2)

    def or(p2: PathValidator[T]): PathValidator[T] = PathOr(this, p2)
  }

  case class PathAnd[T <: HList, T1 <: HList, T2 <: HList](p1: PathValidator[T1], p2: PathValidator[T2])
    extends PathValidator[T]

  case class PathOr[T <: HList](p1: PathValidator[T], p2: PathValidator[T]) extends PathValidator[T]

  case class PathMatch(s: String) extends PathValidator[HNil]

  case class PathCapture[T](parser: String => Option[T]) extends PathValidator[T::HNil]

  case object PathEmpty extends PathValidator[HNil]

  def Get: StatusValidator[HNil, HNil] = StatusValidator(Method.Get, PathEmpty, EmptyValidator)


  ////////////////// Transform the Stream and may perform validation //////////////////
  
  trait BodyTransformer

  case class Decoder[T, H <: HList](codec: Dec[T], v: Validator[H]) extends BodyTransformer {
    def ~>[T1, O](f: T1)(implicit conv: HListToFunc[T::H, O, T1]): Transformed[T,H,O] =
      Transformed(codec, v, conv.conv(f))
  }
  
  case class Transformed[T, H <: HList, O](dec: Dec[T], v: Validator[H], f: T::H => O)
    extends BodyTransformer

  ////////////////// Api /////////////////////////////////////////////////////////////

  /* Checks that the header exists */
  def require(header: HeaderKey.Extractable): Validator[HNil] = requireThat(header)(_ => true)

  /* Check that the header exists and satisfies the condition */
  def requireThat[H <: HeaderKey.Extractable](header: H)
                 (implicit f: H#HeaderT => Boolean = {_: H#HeaderT => true}): Validator[HNil] =
    HeaderValidator[H](header, f)

  /** requires the header and will pull this header from the pile and put it into the function args stack */
  def capture[H <: HeaderKey.Extractable](key: H): Validator[H#HeaderT::HNil] =
      HeaderCapture[H#HeaderT](key)

  def map[H <: HeaderKey.Extractable, R](key: H)(f: H#HeaderT => R): Validator[R::HNil] =
    HeaderMapper[H, R](key, f)
  
  def decode[T](t: MediaType)(implicit codec: Dec[T]): Decoder[T, HNil] = {
    // Need to require the right media header
    ???
  }
  
  trait Dec[T] {
    def decode(s: Process[Task, ByteVector]): Task[T]
  }

  /////////////////// Helpers for turning a function of may params to a function of a HList

  trait HListToFunc[H <: HList, O, F] {
    def conv(f: F): H => O
  }

  implicit def fun1[T1, O] = new HListToFunc[T1::HNil, O, Function1[T1, O]] {
    override def conv(f: (T1) => O): (::[T1, HNil]) => O = { h => f(h.head)
    }
  }

  implicit def fun2[T1, T2, O] = new HListToFunc[T2::T1::HNil, O, Function2[T1, T2, O]] {
    override def conv(f: (T1, T2) => O): (T2::T1::HNil) => O = { h =>
      val t2 = h.head
      val t1 = h.tail.head
      f(t1,t2)
    }
  }


  /////////////////// Route compiler ////////////////////////////////////////
  def compiler[T <: HList, F](r: Runnable[T], f: F, hf: HListToFunc[T,Task[Response],F]): Goal = ???


}