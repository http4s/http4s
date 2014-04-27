package org.http4s
package cooldsl

import shapeless.{HList, HNil, ::}
import scalaz.concurrent.Task
import shapeless.ops.hlist.Prepend

import BodyCodec._

import scala.language.existentials

object CoolApi {

  type Goal = Request => Option[Task[Response]]

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

  ////////////////// Status line combinators //////////////////////////////////////////

  case class StatusValidator[T1 <: HList, T2 <: HList](m: Method, path: PathValidator[T1], hval: Validator[T2]) {

    def /(p: PathMatch): StatusValidator[T1,T2] = copy(path = path.and(p))

    def /[R](p: PathCapture[R]): StatusValidator[R::T1,T2] = copy(path = path.and(p))

    def || (p: PathValidator[T1]): StatusValidator[T1,T2] = copy(path = path.or(p))

    def validate[T3 <: HList](h2: Validator[T3])(implicit prep: Prepend[T2,T3]): StatusValidator[T1, prep.Out] =
      StatusValidator(m, path, hval.and(h2)(prep))

    def compile(implicit prep: Prepend[T1, T2]): Runnable[prep.Out] = Runnable(m, path, hval)

    def decoding[T](dec: Decoder[T])(implicit prep: Prepend[T1, T2]) = compile(prep).decoding(dec)
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

  /////////////////// API helpers ///////////////////////////////////////////

  implicit def makeFromMethod(m: Method): StatusValidator[HNil, HNil] = StatusValidator(m, PathEmpty, EmptyValidator)

  implicit def mkMatch(s: String): PathMatch = PathMatch(s)

  implicit def captureString[T](sym: Symbol): PathCapture[String] = PathCapture(s => Some(s))


  ////////////////// Transform the Stream and may perform validation //////////////////
  


  //////////////// The two types of Runnables, those with a body and those without ////

  case class Runnable[T <: HList](m: Method, p: PathValidator[_ <: HList], h: Validator[_ <: HList]) {
    def ~>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal = compiler(this, f, hf)
    def decoding[R](decoder: Decoder[R]): CodecRunnable[T, R] = CodecRunnable(this, decoder)
  }

  case class CodecRunnable[T <: HList, R](r: Runnable[T], t: BodyTransformer[R]) {
    def ~>[F](f: F)(implicit hf: HListToFunc[R::T,Task[Response],F]): Goal = compileWithBody(this, f, hf)
    def decoding(decoder: Decoder[R]): CodecRunnable[T, R] = CodecRunnable(r, decoder)
  }

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

  /////////////////// Route compiler ////////////////////////////////////////
  def compiler[T <: HList, F](r: Runnable[T], f: F, hf: HListToFunc[T,Task[Response],F]): Goal =
    RouteExecutor.compile(r, f, hf)

  def compileWithBody[T <: HList, F, R](r: CodecRunnable[T, R], f: F, fg: HListToFunc[R::T, Task[Response], F]): Goal =
    RouteExecutor.compileWithBody(r, f, fg)
}