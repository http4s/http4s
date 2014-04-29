package org.http4s

import shapeless.{::, HList}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{Dec, BodyTransformer, Decoder}
import org.http4s.cooldsl.PathBuilder.PathRule
import org.http4s.cooldsl.HeaderMatcher.HeaderRule

import scala.language.existentials

/**
 * Created by Bryce Anderson on 4/28/14.
 */
package object cooldsl {

  type Goal = Request => Option[Task[Response]]

  //////////////////////////////////////////////////////////////////////////////////////////////

  case class Runnable[T <: HList](method: Method, p: PathRule[_ <: HList], validators: HeaderRule[_ <: HList]) {
    def ==>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal = RouteExecutor.compile(this, f, hf)
    def decoding[R](decoder: Decoder[R]): CodecRunnable[T, R] = CodecRunnable(this, decoder)
  }

  case class CodecRunnable[T <: HList, R](r: Runnable[T], t: BodyTransformer[R]) {
    def ==>[F](f: F)(implicit hf: HListToFunc[R::T,Task[Response],F]): Goal = RouteExecutor.compileWithBody(this, f, hf)
  }

}
