package org.http4s
package cooldsl

import shapeless.{::, HList}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{BodyTransformer, Decoder}
import shapeless.ops.hlist.Prepend

import scala.language.existentials

/**
 * Created by Bryce Anderson on 4/29/14.
 */

/** Provides the operations for generating a router
  *
  * @param method request methods to match
  * @param p path matching stack
  * @param validators header validation stack
  * @tparam T1 cumulative type of the required method for executing the router
  */
case class Router[T1 <: HList](method: Method, p: PathRule[_ <: HList], validators: HeaderRule[_ <: HList])
  extends RouteExecutable[T1] with HeaderAppendable[T1] {

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1]): Router[prep1.Out] =
    Router(method, p, And(validators,v))

  override def |>>[F](f: F)(implicit hf: HListToFunc[T1,Task[Response],F]): Goal = RouteExecutor.compile(this, f, hf)

  override def |>>>[F](f: F)(implicit hf: HListToFunc[T1,Task[Response],F]): CoolAction[T1, F] =
    new CoolAction(this, f, hf)

  def decoding[R](decoder: Decoder[R]): CodecRouter[T1,R] = CodecRouter(this, decoder)
}

case class CodecRouter[T1 <: HList, R](r: Router[T1], t: BodyTransformer[R])extends HeaderAppendable[T1] with RouteExecutable[R::T1] {

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1]): CodecRouter[prep1.Out,R] =
    CodecRouter(r >>> v, t)

  override def |>>[F](f: F)(implicit hf: HListToFunc[R::T1,Task[Response],F]): Goal =
    RouteExecutor.compileWithBody(this, f, hf)

  override def |>>>[F](f: F)(implicit hf: HListToFunc[R::T1,Task[Response],F]): CoolAction[R::T1, F] =
    new CoolAction(this, f, hf)
}

private[cooldsl] trait RouteExecutable[T <: HList] {
  def |>>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal
  def |>>>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): CoolAction[T, F]
}

private[cooldsl] trait HeaderAppendable[T1 <: HList] {
  def >>>[T2 <: HList](v: HeaderRule[T2])(implicit prep1: Prepend[T2, T1]): HeaderAppendable[prep1.Out]
}