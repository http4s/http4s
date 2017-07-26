package org.http4s

import scalaz.std.anyVal._
import scalaz.syntax.validation._
import scalaz.{Show, Validation, ValidationNel, Functor, Contravariant, PlusEmpty}


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
trait QueryParamEncoder[T] { outer =>
  def encode(value: T): QueryParameterValue

  /** QueryParamEncoder is a contravariant functor. */
  def contramap[U](f: U => T): QueryParamEncoder[U] =
    new QueryParamEncoder[U] {
      override def encode(value: U) =
        outer.encode(f(value))
    }

}

object QueryParamEncoder {

  /** summon an implicit [[QueryParamEncoder]] */
  def apply[T](implicit ev: QueryParamEncoder[T]): QueryParamEncoder[T] = ev

  /** QueryParamEncoder is a contravariant functor. */
  implicit val ContravariantQueryParamEncoder: Contravariant[QueryParamEncoder] =
    new Contravariant[QueryParamEncoder] {
      override def contramap[A, B](fa: QueryParamEncoder[A])(f: B => A) =
        fa.contramap(f)
    }

  @deprecated("Use QueryParamEncoder[U].contramap(f)", "0.17")
  def encodeBy[T, U](f: T => U)(
    implicit qpe: QueryParamEncoder[U]
  ): QueryParamEncoder[T] =
    qpe.contramap(f)

  @deprecated("Use QueryParamEncoder[String].contramap(f)", "0.17")
  def encode[T](f: T => String): QueryParamEncoder[T] =
    stringQueryParamEncoder.contramap(f)

  def fromShow[T](
    implicit sh: Show[T]
  ): QueryParamEncoder[T] =
    stringQueryParamEncoder.contramap(sh.shows)

  implicit lazy val booleanQueryParamEncoder: QueryParamEncoder[Boolean] = fromShow[Boolean]
  implicit lazy val doubleQueryParamEncoder : QueryParamEncoder[Double]  = fromShow[Double]
  implicit lazy val floatQueryParamEncoder  : QueryParamEncoder[Float]   = fromShow[Float]
  implicit lazy val shortQueryParamEncoder  : QueryParamEncoder[Short]   = fromShow[Short]
  implicit lazy val intQueryParamEncoder    : QueryParamEncoder[Int]     = fromShow[Int]
  implicit lazy val longQueryParamEncoder   : QueryParamEncoder[Long]    = fromShow[Long]

  implicit lazy val stringQueryParamEncoder : QueryParamEncoder[String]  =
    new QueryParamEncoder[String] {
      override def encode(value: String) =
        QueryParameterValue(value)
    }

}


/**
 * Type class defining how to decode a [[QueryParameterValue]] into a `T`
 * @see QueryParamCodecLaws
 */
trait QueryParamDecoder[T] { outer =>
  def decode(value: QueryParameterValue): ValidationNel[ParseFailure, T]

  /** QueryParamDecoder is a covariant functor. */
  def map[U](f: T => U): QueryParamDecoder[U] =
    new QueryParamDecoder[U] {
      override def decode(value: QueryParameterValue) =
        outer.decode(value).map(f)
    }

  /** Use another decoder if this one fails. */
  def orElse[U >: T](qpd: QueryParamDecoder[U]): QueryParamDecoder[U] =
    new QueryParamDecoder[U] {
      override def decode(value: QueryParameterValue) =
        outer.decode(value) orElse qpd.decode(value)
    }

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

  /** QueryParamDecoder is a covariant functor. */
  implicit val FunctorQueryParamDecoder: Functor[QueryParamDecoder] =
    new Functor[QueryParamDecoder] {
      override def map[A, B](fa: QueryParamDecoder[A])(f: A => B) =
        fa.map(f)
    }

  /** QueryParamDecoder is a PlusEmpty. */
  implicit val PlusEmptyQueryParamDecoder: PlusEmpty[QueryParamDecoder] =
    new PlusEmpty[QueryParamDecoder] {
      def empty[A] =
        FunctorQueryParamDecoder.widen[Nothing, A](nothingQueryParamDecoder)
      def plus[A](a: QueryParamDecoder[A], b: => QueryParamDecoder[A]) =
        a.orElse(b)
    }

  @deprecated("Use QueryParamEncoder[T].map(f)", "0.17")
  def decodeBy[U, T](f: T => U)(
    implicit qpd: QueryParamDecoder[T]
  ): QueryParamDecoder[U] =
    qpd.map(f)

  /** A decoder that always fails. */
  implicit lazy val nothingQueryParamDecoder: QueryParamDecoder[Nothing] =
    new QueryParamDecoder[Nothing] {
      override def decode(value: QueryParameterValue) =
        Validation.failureNel(ParseFailure("Failed.", "Nothing decoder always fails."))
    }

  implicit lazy val booleanQueryParamDecoder: QueryParamDecoder[Boolean] =
    fromUnsafeCast[Boolean](_.value.toBoolean)("Boolean")
  implicit lazy val doubleQueryParamDecoder: QueryParamDecoder[Double] =
    fromUnsafeCast[Double](_.value.toDouble)("Double")
  implicit lazy val floatQueryParamDecoder: QueryParamDecoder[Float] =
    fromUnsafeCast[Float](_.value.toFloat)("Float")
  implicit lazy val shortQueryParamDecoder: QueryParamDecoder[Short] =
    fromUnsafeCast[Short](_.value.toShort)("Short")
  implicit lazy val intQueryParamDecoder: QueryParamDecoder[Int] =
    fromUnsafeCast[Int](_.value.toInt)("Int")
  implicit lazy val longQueryParamDecoder: QueryParamDecoder[Long] =
    fromUnsafeCast[Long](_.value.toLong)("Long")

  implicit lazy val charQueryParamDecoder: QueryParamDecoder[Char] = new QueryParamDecoder[Char]{
    def decode(value: QueryParameterValue): ValidationNel[ParseFailure, Char] =
      if(value.value.size == 1) value.value.head.successNel
      else ParseFailure("Failed to parse Char query parameter",
                       s"Could not parse ${value.value} as a Char") .failureNel
  }

  implicit lazy val stringQueryParamDecoder: QueryParamDecoder[String] = new QueryParamDecoder[String]{
    def decode(value: QueryParameterValue): ValidationNel[ParseFailure, String] =
      value.value.successNel
  }
}
