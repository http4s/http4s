package org.http4s

import shapeless.{HNil, ::, HList}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{Dec, BodyTransformer, Decoder}

import scala.language.existentials
import shapeless.ops.hlist.Prepend

/**
 * Created by Bryce Anderson on 4/28/14.
 */
package object cooldsl {

  type Goal = Request => Option[Task[Response]]

  implicit def method(m: Method): PathBuilder[HNil] = new PathBuilder(m, PathEmpty)

  // TODO: this should be implemented as a 'Syntax'
  implicit def /(str: String): CombinablePathRule[HNil] = PathMatch(str)

  def query[T](key: String)(implicit parser: StringParser[T]) = QueryMapper[T](key, parser)

  def parse[T](implicit parser: StringParser[T]) = PathCapture(parser)

  def -* = CaptureTail()

  /////////////////////////////// Header helpers //////////////////////////////////////

  /* Checks that the header exists */
  def require(header: HeaderKey.Extractable): HeaderRule[HNil] = requireThat(header)(_ => true)

  /* Check that the header exists and satisfies the condition */
  def requireThat[H <: HeaderKey.Extractable](header: H)
                                             (implicit f: H#HeaderT => Boolean = {_: H#HeaderT => true}): HeaderRule[HNil] =
    HeaderRequire[H](header, f)

  /** requires the header and will pull this header from the pile and put it into the function args stack */
  def capture[H <: HeaderKey.Extractable](key: H): HeaderRule[H#HeaderT::HNil] =
    HeaderCapture[H#HeaderT](key)

  def requireMap[H <: HeaderKey.Extractable, R](key: H)(f: H#HeaderT => R): HeaderRule[R::HNil] =
    HeaderMapper[H, R](key, f)
}
