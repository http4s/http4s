package org.http4s

import shapeless.{::, HList}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{BodyTransformer, Decoder}
import org.http4s.cooldsl.PathBuilder.{PathRule, PathBuilder}
import org.http4s.cooldsl.HeaderMatcher.HeaderRule

import scala.language.existentials

/**
 * Created by Bryce Anderson on 4/28/14.
 */
package object cooldsl {

  type Goal = Request => Option[Task[Response]]

  //////////////// The two types of Routes, those with a body and those without ////

  /////////////////// Route compiler ////////////////////////////////////////
  def compiler[T <: HList, F](r: Runnable[T], f: F, hf: HListToFunc[T,Task[Response],F]): Goal =
    RouteExecutor.compile(r, f, hf)

  def compileWithBody[T <: HList, F, R](r: CodecRunnable[T, R], f: F, fg: HListToFunc[R::T, Task[Response], F]): Goal =
    RouteExecutor.compileWithBody(r, f, fg)

  //////////////////////////////////////////////////////////////////////////////////////////////

  case class Runnable[T <: HList](method: Method, p: PathRule[_ <: HList], validators: HeaderRule[_ <: HList]) {
    def ~>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal = compiler(this, f, hf)
    def decoding[R](decoder: Decoder[R]): CodecRunnable[T, R] = CodecRunnable(this, decoder)
  }

  case class CodecRunnable[T <: HList, R](r: Runnable[T], t: BodyTransformer[R]) {
    def ~>[F](f: F)(implicit hf: HListToFunc[R::T,Task[Response],F]): Goal = compileWithBody(this, f, hf)
    //def decoding(decoder: Decoder[R]): CodecRunnable[T, R] = CodecRunnable(r, decoder)
  }

}
