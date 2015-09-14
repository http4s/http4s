package org.http4s

import scalaz.std.anyVal._
import scalaz.syntax.validation._
import scalaz.{Show, Validation, ValidationNel}


final case class QueryParameterKey(value: String) extends AnyVal

final case class QueryParameterValue(value: String) extends AnyVal

/**
 * type class defining the key of a query parameter
 * Usually used in conjunction with [[QueryParamEncoder]] and [[QueryParamDecoder]]
 */
trait QueryParam[T] {
  def key: QueryParameterKey
}

object QueryParam {
  /** summon an implicit [[QueryParam]] */
  def apply[T](implicit ev: QueryParam[T]): QueryParam[T] = ev

  def fromKey[T](k: String): QueryParam[T] = new QueryParam[T] {
    def key: QueryParameterKey = QueryParameterKey(k)
  }
}

trait QueryParamKeyLike[T] {
  def getKey(t: T): QueryParameterKey
}

object QueryParamKeyLike {
  def apply[T](implicit ev: QueryParamKeyLike[T]): QueryParamKeyLike[T] = ev

  implicit val stringKey: QueryParamKeyLike[String] = new QueryParamKeyLike[String] {
    override def getKey(t: String): QueryParameterKey = QueryParameterKey(t)
  }
}

/**
 * Type class defining how to encode a `T` as a [[QueryParameterValue]]s
 * @see QueryParamCodecLaws
 */
trait QueryParamEncoder[T] {
  def encode(value: T): QueryParameterValue
}

object QueryParamEncoder {

  /** summon an implicit [[QueryParamEncoder]] */
  def apply[T](implicit ev: QueryParamEncoder[T]): QueryParamEncoder[T] = ev

  def encodeBy[T, U: QueryParamEncoder](f: T => U): QueryParamEncoder[T] = new QueryParamEncoder[T] {
    def encode(value: T): QueryParameterValue =
      QueryParamEncoder[U].encode(f(value))
  }

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
  implicit val stringQueryParamEncoder : QueryParamEncoder[String]  = encode(identity)
}


/**
 * Type class defining how to decode a [[QueryParameterValue]] into a `T`
 * @see QueryParamCodecLaws
 */
trait QueryParamDecoder[T] {
  def decode(value: QueryParameterValue): ValidationNel[ParseFailure, T]
}

object QueryParamDecoder {
  /** summon an implicit [[QueryParamDecoder]] */
  def apply[T](implicit ev: QueryParamDecoder[T]): QueryParamDecoder[T] = ev

  def fromUnsafeCast[T](cast: QueryParameterValue => T)(typeName: String): QueryParamDecoder[T] = new QueryParamDecoder[T]{
    def decode(value: QueryParameterValue): ValidationNel[ParseFailure, T] =
      Validation.fromTryCatchNonFatal(cast(value)).leftMap(t =>
        ParseFailure(s"Query decoding $typeName failed", t.getMessage)
      ).toValidationNel
  }

  def decodeBy[T, U: QueryParamDecoder](f: U => T): QueryParamDecoder[T] = new QueryParamDecoder[T] {
    def decode(value: QueryParameterValue): ValidationNel[ParseFailure, T] =
      QueryParamDecoder[U].decode(value) map f
  }


  implicit val booleanQueryParamDecoder: QueryParamDecoder[Boolean] =
    fromUnsafeCast[Boolean](_.value.toBoolean)("Boolean")
  implicit val doubleQueryParamDecoder: QueryParamDecoder[Double] =
    fromUnsafeCast[Double](_.value.toDouble)("Double")
  implicit val floatQueryParamDecoder: QueryParamDecoder[Float] =
    fromUnsafeCast[Float](_.value.toFloat)("Float")
  implicit val shortQueryParamDecoder: QueryParamDecoder[Short] =
    fromUnsafeCast[Short](_.value.toShort)("Short")
  implicit val intQueryParamDecoder: QueryParamDecoder[Int] =
    fromUnsafeCast[Int](_.value.toInt)("Int")
  implicit val longQueryParamDecoder: QueryParamDecoder[Long] =
    fromUnsafeCast[Long](_.value.toLong)("Long")

  implicit val charQueryParamDecoder: QueryParamDecoder[Char] = new QueryParamDecoder[Char]{
    def decode(value: QueryParameterValue): ValidationNel[ParseFailure, Char] =
      if(value.value.size == 1) value.value.head.successNel
      else ParseFailure("Failed to parse Char query parameter",
                       s"Could not parse ${value.value} as a Char") .failureNel
  }

  implicit val stringQueryParamDecoder: QueryParamDecoder[String] = new QueryParamDecoder[String]{
    def decode(value: QueryParameterValue): ValidationNel[ParseFailure, String] =
      value.value.successNel
  }
}
