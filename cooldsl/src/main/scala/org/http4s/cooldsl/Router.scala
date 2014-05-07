package org.http4s
package cooldsl

import shapeless.{::, HList}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{BodyTransformer, Decoder}
import shapeless.ops.hlist.Prepend

import scala.language.existentials
import org.http4s.cooldsl.bits.HListToFunc
import org.http4s.Header.`Content-Type`

/**
 * Created by Bryce Anderson on 4/29/14.
 */

/** Provides the operations for generating a router
  *
  * @param method request methods to match
  * @param path path matching stack
  * @param validators header validation stack
  * @tparam T1 cumulative type of the required method for executing the router
  */
case class Router[T1 <: HList](method: Method,
                               private[cooldsl] val path: PathRule[_ <: HList],
                               validators: HeaderRule[_ <: HList])
                extends RouteExecutable[T1] with HeaderAppendable[T1] {

  type Self = Router[T1]

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1]): Router[prep1.Out] =
    Router(method, path, And(validators,v))

  override def makeAction[F,O](f: F)(implicit hf: HListToFunc[T1,O,F]): CoolAction[T1, F, O] =
    new CoolAction(this, f, hf)

  def decoding[R](decoder: Decoder[R]): CodecRouter[T1,R] = CodecRouter(this, decoder)

  override protected def addMetaData(data: MetaData): Router[T1] = copy(path = PathAnd(path, data))
}

case class CodecRouter[T1 <: HList, R](router: Router[T1], decoder: BodyTransformer[R])extends HeaderAppendable[T1] with RouteExecutable[R::T1] {

  type Self = CodecRouter[T1, R]

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1]): CodecRouter[prep1.Out,R] =
    CodecRouter(router >>> v, decoder)

  override def makeAction[F,O](f: F)(implicit hf: HListToFunc[R::T1,O,F]): CoolAction[R::T1, F, O] =
    new CoolAction(this, f, hf)

  private[cooldsl] override def path: PathRule[_ <: HList] = router.path

  override def method: Method = router.method

  override private[cooldsl] val validators: HeaderRule[_ <: HList] = {
    if (!decoder.consumes.isEmpty) {
      val mt = requireThat(Header.`Content-Type`){ h: Header.`Content-Type`.HeaderT =>
        decoder.consumes.find(_ == h.mediaType).isDefined
      }
      And(router.validators, mt)
    } else router.validators
  }

  override protected def addMetaData(data: MetaData): CodecRouter[T1, R] = copy(router.copy(path = PathAnd(router.path, data)))
}

private[cooldsl] trait RouteExecutable[T <: HList] {
  def method: Method
  
  def makeAction[F, O](f: F)(implicit hf: HListToFunc[T,O,F]): CoolAction[T, F, O]
  
  private[cooldsl] def path: PathRule[_ <: HList]
  
  private[cooldsl] def validators: HeaderRule[_ <: HList]
  
  final def |>>>[F, O, R](f: F)(implicit hf: HListToFunc[T,O,F], srvc: CompileService[R]): R =
    srvc.compile(makeAction(f))
  
  final def runWith[F, O, R](f: F)(implicit hf: HListToFunc[T,O,F]): Request=>Option[Task[Response]] =
    |>>>(f)(hf, RouteExecutor)
}

private[cooldsl] trait HeaderAppendable[T1 <: HList] extends MetaDataSyntax {
  type Self <: HeaderAppendable[T1]
  def >>>[T2 <: HList](v: HeaderRule[T2])(implicit prep1: Prepend[T2, T1]): HeaderAppendable[prep1.Out]
}