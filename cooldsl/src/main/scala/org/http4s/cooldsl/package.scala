package org.http4s

import shapeless.{::, HList}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{Dec, BodyTransformer, Decoder}
import org.http4s.cooldsl.PathBuilder.PathRule

import scala.language.existentials
import shapeless.ops.hlist.Prepend

/**
 * Created by Bryce Anderson on 4/28/14.
 */
package object cooldsl {

  type Goal = Request => Option[Task[Response]]

  //////////////////////////////////////////////////////////////////////////////////////////////

  case class Runnable[T1 <: HList, T2 <: HList](method: Method, p: PathRule[_ <: HList], validators: HeaderRule[T2])
          extends RunnableHeaderRule[T2] {

    override protected type ThisType[T <: HList] = Runnable[T1, T]

    override protected def combine[T3 <: HList](f: (HeaderRule[T2]) => HeaderRule[T3]): ThisType[T3] = {
      Runnable(method, p, validators = f(validators))
    }

    def ==>[F](f: F)(implicit hf: HListToFunc[T1,Task[Response],F]): Goal = RouteExecutor.compile(this, f, hf)
    def decoding[R](decoder: Decoder[R]): CodecRunnable[T1, T2, R] = CodecRunnable(this, decoder)
  }

  case class CodecRunnable[T1 <: HList, T2 <: HList, R](r: Runnable[T1,T2], t: BodyTransformer[R])
          extends RunnableHeaderRule[T2] {

    override protected def combine[T1 <: HList](f: (HeaderRule[T2]) => HeaderRule[T1]): ThisType[T1] =
      CodecRunnable(Runnable(r.method, r.p, f(r.validators)), t)

    override protected type ThisType[T <: HList] = CodecRunnable[T1, T, R]

    def ==>[F](f: F)(implicit hf: HListToFunc[R::T1,Task[Response],F]): Goal = RouteExecutor.compileWithBody(this, f, hf)
  }


  private[cooldsl] trait RunnableHeaderRule[T <: HList] extends HeaderRuleSyntax[T] {

    protected type ThisType[T <: HList] <: RunnableHeaderRule[T]

    protected def combine[T1 <: HList](f: HeaderRule[T] => HeaderRule[T1]): ThisType[T1]

    override def &&[T1 <: HList](v: HeaderRule[T1])(implicit prepend: Prepend[T, T1]): ThisType[prepend.type#Out] =
      and(v)

    override def and[T1 <: HList](v: HeaderRule[T1])(implicit prepend: Prepend[T, T1]): ThisType[prepend.type#Out] =
      combine(current => current.and(v))

    override def ||(v: HeaderRule[T]): HeaderRuleSyntax[T] = or(v)

    override def or(v: HeaderRule[T]): HeaderRuleSyntax[T] = combine(vold => vold.or(v))
  }

}
