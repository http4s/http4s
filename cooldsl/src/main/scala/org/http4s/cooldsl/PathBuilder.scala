package org.http4s.cooldsl

import shapeless.{::, HNil, HList}
import shapeless.ops.hlist.Prepend
import org.http4s.{Response, Method}
import org.http4s.cooldsl.BodyCodec.Decoder
import scalaz.concurrent.Task

import scala.language.existentials

/**
 * Created by Bryce Anderson on 4/28/14.
 *
 * The goal of a PathBuilder is to allow the composition of what is typically on the status line
 * of a HTTP request. That includes the request method, path, and query params.
 */
object PathBuilder {

  implicit def method(m: Method): PathBuilder[HNil] = new PathBuilder(m, PathEmpty)

  // TODO: this should be implemented as a 'Syntax'
  implicit def /(str: String): CombinablePathRule[HNil] = PathMatch(str)

  def query[T](key: String)(implicit parser: StringParser[T]) = QueryMapper[T](key, parser)

  def parse[T](implicit parser: StringParser[T]) = PathCapture(parser)

  def -* = CaptureTail()

  ////////////////// Status line combinators //////////////////////////////////////////

  sealed trait PathBuilderBase[T <: HList] {
    def m: Method
    private[cooldsl] def path: PathRule[T]

    final def toAction: Runnable[T, HNil] = validate(EmptyHeaderRule)

    final def validate[T1 <: HList](h2: HeaderRule[T1])(implicit prep: Prepend[T1,T]): Runnable[prep.Out, T1] =
      Runnable(m, path, h2)

    final def decoding[R](dec: Decoder[R]): CodecRunnable[T, HNil, R] = CodecRunnable(toAction, dec)

    final def ==>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal = RouteExecutor.compile(toAction, f, hf)
  }

  /** PathBuilder that disallows modifications to path but allows further query params mode */
  final class FinishedPathBuilder[T <: HList](val m: Method, private[cooldsl] val path: PathRule[T]) extends PathBuilderBase[T] {
    def -?[T1](q: QueryMapper[T1]): FinishedPathBuilder[T1::T] = new FinishedPathBuilder(m, path.and(q))
    def &[T1](q: QueryMapper[T1]): FinishedPathBuilder[T1::T] = -?(q)
  }

  /** Fully functional path building */
  final class PathBuilder[T <: HList](val m: Method, private[cooldsl] val path: PathRule[T])
          extends PathBuilderBase[T] with CombinablePathSyntax[T] {

    override protected type ThisType[T <: HList] = PathBuilder[T]

    override protected def push[T2 <: HList](r2: CombinablePathRule[T2])(implicit prep: Prepend[T2, T]): PathBuilder[prep.type#Out] =
      new PathBuilder(m, path.and(r2))

    def -?[T1](q: QueryMapper[T1]): FinishedPathBuilder[T1::T] = new FinishedPathBuilder(m, path.and(q))

    def /(t: CaptureTail) : FinishedPathBuilder[List[String]::T] = new FinishedPathBuilder(m, path.and(t))

    def /[T2 <: HList](t: PathBuilder[T2])(implicit prep: Prepend[T2, T]) : PathBuilder[prep.Out] =
      new PathBuilder(m, path.and(t.path))

    def /[T2 <: HList](t: FinishedPathBuilder[T2])(implicit prep: Prepend[T2, T]) : FinishedPathBuilder[prep.Out] =
      new FinishedPathBuilder(m, path.and(t.path))
  }

  ////////////////// AST representation of operations supported on the path ///////////////////
  sealed trait PathRule[T <: HList] {
    def and[T2 <: HList](p2: PathRule[T2])(implicit prep: Prepend[T2,T]): PathRule[prep.Out] =
      PathAnd(this, p2)

    def &&[T2 <: HList](p2: PathRule[T2])(implicit prep: Prepend[T2,T]): PathRule[prep.Out] = and(p2)

    def or(p2: PathRule[T]): PathRule[T] = PathOr(this, p2)

    def ||(p2: PathRule[T]): PathRule[T] = or(p2)
  }

  /** Facilitates the combination of rules which my be logically arranged using the '/' operator */
  private[cooldsl] trait CombinablePathSyntax[T <: HList] {
    protected type ThisType[T <: HList] <: CombinablePathSyntax[T]

    protected def push[T2 <: HList](r2: CombinablePathRule[T2])(implicit prep: Prepend[T2, T]): ThisType[prep.Out]

    def /(p: String): ThisType[T] = /(PathMatch(p))

    def /(p: Symbol): ThisType[String::T] = /(PathCapture(StringParser.strParser))

    def /(p: PathMatch): ThisType[T] = push(p)

    def /[R](p: PathCapture[R]): ThisType[R::T] = push(p)

    def /[T2 <: HList](p: PathAnd[T2])(implicit prep: Prepend[T2, T]): ThisType[prep.Out] = push(p)
  }

  sealed trait CombinablePathRule[T <: HList] extends PathRule[T] with CombinablePathSyntax[T] {
    override protected type ThisType[T <: HList] = CombinablePathRule[T]

    override protected def push[T2 <: HList](r2: CombinablePathRule[T2])
                       (implicit prep: Prepend[T2, T]): CombinablePathRule[prep.type#Out] = PathAnd(this, r2)
  }

  /** Actual elements which build up the AST */

  case class PathAnd[T <: HList](p1: PathRule[_ <: HList], p2: PathRule[_ <: HList]) extends CombinablePathRule[T]

  case class PathOr[T <: HList](p1: PathRule[T], p2: PathRule[T]) extends CombinablePathRule[T]

  case class PathMatch(s: String) extends CombinablePathRule[HNil]

  case class PathCapture[T](parser: StringParser[T]) extends CombinablePathRule[T::HNil]

  // These don't fit the  operations of CombinablePathSyntax because they may
  // result in a change of the type of PathBulder
  case class CaptureTail() extends PathRule[List[String]::HNil]

  case object PathEmpty extends PathRule[HNil]

  case class QueryMapper[T](name: String, p: StringParser[T]) extends PathRule[T::HNil]
}
