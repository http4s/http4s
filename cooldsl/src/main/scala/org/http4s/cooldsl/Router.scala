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
  * @tparam T2 type of just the HeaderRules for further composition
  */
case class Router[T1 <: HList, T2 <: HList](method: Method, p: PathRule[_ <: HList], validators: HeaderRule[T2])
  extends RouteExecutable[T1] with HeaderRuleAppendable[T1,T2] {
  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1], prep2: Prepend[T3, T2]): Router[prep1.Out,prep2.type#Out] =
    Router(method, p, And(validators,v))


  def |>>[F](f: F)(implicit hf: HListToFunc[T1,Task[Response],F]): Goal = RouteExecutor.compile(this, f, hf)
  def decoding[R](decoder: Decoder[R]): CodecRouter[T1, T2, R] = CodecRouter(this, decoder)
}

case class CodecRouter[T1 <: HList, T2 <: HList, R](r: Router[T1,T2], t: BodyTransformer[R])
  extends HeaderRuleAppendable[T1,T2] with RouteExecutable[R::T1] {

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1], prep2: Prepend[T3, T2]): CodecRouter[prep1.Out,prep2.type#Out, R] =
    CodecRouter(r >>> v, t)

  override def |>>[F](f: F)(implicit hf: HListToFunc[R::T1,Task[Response],F]): Goal =
    RouteExecutor.compileWithBody(this, f, hf)
}

private[cooldsl] trait RouteExecutable[T <: HList] {
  def |>>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal
}

private[cooldsl] sealed trait HeaderRuleAppendable[T1 <: HList, T2 <: HList] {
  def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1], prep2: Prepend[T3, T2]): HeaderRuleAppendable[prep1.Out, prep2.Out]
}