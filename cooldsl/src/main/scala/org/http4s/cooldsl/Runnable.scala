package org.http4s.cooldsl

import shapeless.{::, HList}
import org.http4s.{Response, Method}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{BodyTransformer, Decoder}
import shapeless.ops.hlist.Prepend

import scala.language.existentials

/**
 * Created by Bryce Anderson on 4/29/14.
 */


case class Runnable[T1 <: HList, T2 <: HList](method: Method, p: PathRule[_ <: HList], validators: HeaderRule[T2])
  extends RunnableHeaderRule[T2] {
  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prepend: Prepend[T2, T3]): Runnable[T1,prepend.type#Out] =
    Runnable(method, p, And(validators,v))


  def ==>[F](f: F)(implicit hf: HListToFunc[T1,Task[Response],F]): Goal = RouteExecutor.compile(this, f, hf)
  def decoding[R](decoder: Decoder[R]): CodecRunnable[T1, T2, R] = CodecRunnable(this, decoder)
}

case class CodecRunnable[T1 <: HList, T2 <: HList, R](r: Runnable[T1,T2], t: BodyTransformer[R])
  extends RunnableHeaderRule[T2] {

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep: Prepend[T2, T3]): CodecRunnable[T1,prep.type#Out,R] =
    CodecRunnable(r >>> v, t)

  def ==>[F](f: F)(implicit hf: HListToFunc[R::T1,Task[Response],F]): Goal = RouteExecutor.compileWithBody(this, f, hf)
}


sealed trait RunnableHeaderRule[T <: HList] {
  def >>>[T1 <: HList](v: HeaderRule[T1])(implicit prepend: Prepend[T, T1]): RunnableHeaderRule[prepend.type#Out]
}