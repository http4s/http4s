package org.http4s

import scalaz.std.anyVal._
import scalaz.{Show, ValidationNel}

trait QueryParam[T] {
  def key: QueryParameterKey
}

object QueryParam {
  /** summon an implicit [[QueryParam]] */
  def apply[T](implicit ev: QueryParam[T]): QueryParam[T] = ev
}

/**
 * Type class defining how to encode a T as a Query Parameter
 */
trait QueryParamEncoder[T] {
  def encode(value: T): QueryParameterValue
}

object QueryParamEncoder {

  /** summon an implicit [[QueryParamEncoder]] */
  def apply[T](implicit ev: QueryParamEncoder[T]): QueryParamEncoder[T] = ev


  /*****************************************************************************/
  /** QueryParamEncoder instances                                              */
  /*****************************************************************************/

  def encode[T](f: T => String): QueryParamEncoder[T] = new QueryParamEncoder[T] {
    def encode(value: T): QueryParameterValue =
      QueryParameterValue(f(value))
  }

  def fromShow[T: Show]: QueryParamEncoder[T] = encode(Show[T].shows)

  implicit val booleanQueryParamEncoder: QueryParamEncoder[Boolean] = fromShow[Boolean]
  implicit val doubleQueryParamEncoder : QueryParamEncoder[Double]  = fromShow[Double]
  implicit val floatQueryParamEncoder  : QueryParamEncoder[Float]   = fromShow[Float]
  implicit val shortQueryParamEncoder  : QueryParamEncoder[Short]   = fromShow[Short]
  implicit val intQueryParamEncoder    : QueryParamEncoder[Int]     = fromShow[Int]
  implicit val longQueryParamEncoder   : QueryParamEncoder[Long]    = fromShow[Long]
  implicit val charQueryParamEncoder   : QueryParamEncoder[Char]    = fromShow[Char]
  implicit val stringQueryParamEncoder : QueryParamEncoder[String]  = encode(identity)

}

trait QueryParamDecoder[T] {
  def decode: ValidationNel[ParseFailure, T]
}

final case class QueryParameterKey(value: String)

final case class QueryParameterValue(value: String)