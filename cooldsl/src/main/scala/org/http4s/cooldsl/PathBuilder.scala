package org.http4s.cooldsl

import shapeless.{::, HNil, HList}
import shapeless.ops.hlist.Prepend
import org.http4s.{Response, Method}
import org.http4s.cooldsl.BodyCodec.Decoder
import org.http4s.cooldsl.HeaderMatcher.{EmptyHeaderRule, HeaderRule}
import scalaz.concurrent.Task
import scalaz.{\/-, \/}

/**
 * Created by Bryce Anderson on 4/28/14.
 */
object PathBuilder {

  implicit def method(m: Method): PathBuilder[HNil] = new PathBuilder(m, PathEmpty)

  def query[T](key: String)(implicit parser: StringParser[T]) = QueryMapper[T](key, parser)

  def parse[T](implicit parser: StringParser[T]) = PathCapture(parser)

  def -* = CaptureTail()

  ////////////////// Status line combinators //////////////////////////////////////////

  sealed trait PathBuilderBase[T <: HList] {
    def m: Method
    protected def path: PathRule[T]

    final def toAction: Runnable[T] = validate(EmptyHeaderRule)

    final def validate[T1 <: HList](h2: HeaderRule[T1])(implicit prep: Prepend[T1,T]): Runnable[prep.Out] =
      Runnable(m, path, h2)

    final def decoding[R](dec: Decoder[R]): CodecRunnable[T, R] = CodecRunnable(toAction, dec)

    final def ==>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal = RouteExecutor.compile(toAction, f, hf)
  }

  // Serves only to disallow further modifications of the path and are now in query params mode
  final class FinishedPathBuilder[T <: HList](val m: Method, protected val path: PathRule[T]) extends PathBuilderBase[T] {
    def -?[T1](q: QueryMapper[T1]): FinishedPathBuilder[T1::T] = new FinishedPathBuilder(m, path.and(q))
    def &[T1](q: QueryMapper[T1]): FinishedPathBuilder[T1::T] = -?(q)
  }

  final class PathBuilder[T <: HList](val m: Method, protected val path: PathRule[T]) extends PathBuilderBase[T] {

    def -?[T1](q: QueryMapper[T1]): FinishedPathBuilder[T1::T] = new FinishedPathBuilder(m, path.and(q))

    def /(p: String): PathBuilder[T] = new PathBuilder[T](m, path.and(PathMatch(p)))

    def /(p: Symbol): PathBuilder[String::T] = /(PathCapture(StringParser.strParser))

    def /(p: PathMatch): PathBuilder[T] = new PathBuilder[T](m, path.and(p))

    def /[R](p: PathCapture[R]): PathBuilder[R::T] = new PathBuilder(m, path.and(p))

    def /(t: CaptureTail) : FinishedPathBuilder[List[String]::T] = new FinishedPathBuilder(m, path.and(t))
  }

  sealed trait PathRule[T <: HList] {
    def and[T2 <: HList](p2: PathRule[T2])(implicit prep: Prepend[T2,T]): PathRule[prep.Out] =
      PathAnd(this, p2)

    def or(p2: PathRule[T]): PathRule[T] = PathOr(this, p2)
  }

  case class PathAnd[T <: HList, T1 <: HList, T2 <: HList](p1: PathRule[T1], p2: PathRule[T2])
    extends PathRule[T]

  case class PathOr[T <: HList](p1: PathRule[T], p2: PathRule[T]) extends PathRule[T]

  case class PathMatch(s: String) extends PathRule[HNil]

  case class PathCapture[T](parser: StringParser[T]) extends PathRule[T::HNil]

  case class CaptureTail() extends PathRule[List[String]::HNil]

  case object PathEmpty extends PathRule[HNil]

  case class QueryMapper[T](name: String, p: StringParser[T]) extends PathRule[T::HNil]
}
