/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.{Contravariant, Functor, Hash, MonoidK, Order, Show}
import cats.data.{Validated, ValidatedNel}
import cats.syntax.all._
import java.time.{Instant, LocalDate, ZonedDateTime}
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.temporal.TemporalAccessor

final case class QueryParameterKey(value: String) extends AnyVal

object QueryParameterKey {
  implicit lazy val showInstance: Show[QueryParameterKey] =
    Show.show(_.value)

  implicit lazy val orderInstance: Order[QueryParameterKey] =
    Order.by(_.value)

  implicit lazy val hashInstance: Hash[QueryParameterKey] =
    Hash.by(_.value)
}

final case class QueryParameterValue(value: String) extends AnyVal

object QueryParameterValue {
  implicit lazy val showInstance: Show[QueryParameterValue] =
    Show.show(_.value)

  implicit lazy val orderInstance: Order[QueryParameterValue] =
    Order.by(_.value)

  implicit lazy val hashInstance: Hash[QueryParameterValue] =
    Hash.by(_.value)
}

/** type class defining the key of a query parameter
  * Usually used in conjunction with [[QueryParamEncoder]] and [[QueryParamDecoder]]
  *
  * Any [[QueryParam]] instance is also a valid [[QueryParamKeyLike]] instance
  * where the same key is used for all values.
  */
trait QueryParam[T] extends QueryParamKeyLike[T] {
  def key: QueryParameterKey
  override final def getKey(t: T): QueryParameterKey = key
}

object QueryParam {

  /** summon an implicit [[QueryParam]] */
  def apply[T](implicit ev: QueryParam[T]): QueryParam[T] = ev

  def fromKey[T](k: String): QueryParam[T] =
    new QueryParam[T] {
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

trait QueryParamCodec[T] extends QueryParamEncoder[T] with QueryParamDecoder[T]
object QueryParamCodec {
  def apply[A](implicit instance: QueryParamCodec[A]): QueryParamCodec[A] = instance

  def from[A](decodeA: QueryParamDecoder[A], encodeA: QueryParamEncoder[A]): QueryParamCodec[A] =
    new QueryParamCodec[A] {
      override def encode(value: A): QueryParameterValue = encodeA.encode(value)
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, A] =
        decodeA.decode(value)
    }

  def instantQueryParamCodec(formatter: DateTimeFormatter): QueryParamCodec[Instant] = {
    import QueryParamDecoder.instantQueryParamDecoder
    import QueryParamEncoder.instantQueryParamEncoder

    QueryParamCodec
      .from[Instant](instantQueryParamDecoder(formatter), instantQueryParamEncoder(formatter))
  }

  def localDateQueryParamCodec(formatter: DateTimeFormatter): QueryParamCodec[LocalDate] = {
    import QueryParamDecoder.localDateQueryParamDecoder
    import QueryParamEncoder.localDateQueryParamEncoder

    QueryParamCodec
      .from[LocalDate](localDateQueryParamDecoder(formatter), localDateQueryParamEncoder(formatter))
  }

  def zonedDateTimeQueryParamCodec(formatter: DateTimeFormatter): QueryParamCodec[ZonedDateTime] = {
    import QueryParamDecoder.zonedDateTimeQueryParamDecoder
    import QueryParamEncoder.zonedDateTimeQueryParamEncoder

    QueryParamCodec.from[ZonedDateTime](
      zonedDateTimeQueryParamDecoder(formatter),
      zonedDateTimeQueryParamEncoder(formatter))
  }
}

/** Type class defining how to encode a `T` as a [[QueryParameterValue]]s
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

  def fromCodec[T](implicit ev: QueryParamCodec[T]): QueryParamEncoder[T] = ev

  /** QueryParamEncoder is a contravariant functor. */
  implicit val ContravariantQueryParamEncoder: Contravariant[QueryParamEncoder] =
    new Contravariant[QueryParamEncoder] {
      override def contramap[A, B](fa: QueryParamEncoder[A])(f: B => A) =
        fa.contramap(f)
    }

  def instantQueryParamEncoder(formatter: DateTimeFormatter): QueryParamEncoder[Instant] =
    QueryParamEncoder[String].contramap[Instant] { (i: Instant) =>
      formatter.format(i)
    }

  def localDateQueryParamEncoder(formatter: DateTimeFormatter): QueryParamEncoder[LocalDate] =
    QueryParamEncoder[String].contramap[LocalDate] { (ld: LocalDate) =>
      formatter.format(ld)
    }

  def zonedDateTimeQueryParamEncoder(
      formatter: DateTimeFormatter): QueryParamEncoder[ZonedDateTime] =
    QueryParamEncoder[String].contramap[ZonedDateTime] { (zdt: ZonedDateTime) =>
      formatter.format(zdt)
    }

  def fromShow[T](implicit
      sh: Show[T]
  ): QueryParamEncoder[T] =
    stringQueryParamEncoder.contramap(sh.show)

  implicit lazy val booleanQueryParamEncoder: QueryParamEncoder[Boolean] = fromShow[Boolean]
  implicit lazy val doubleQueryParamEncoder: QueryParamEncoder[Double] = fromShow[Double]
  implicit lazy val floatQueryParamEncoder: QueryParamEncoder[Float] = fromShow[Float]
  implicit lazy val shortQueryParamEncoder: QueryParamEncoder[Short] = fromShow[Short]
  implicit lazy val intQueryParamEncoder: QueryParamEncoder[Int] = fromShow[Int]
  implicit lazy val longQueryParamEncoder: QueryParamEncoder[Long] = fromShow[Long]

  implicit lazy val stringQueryParamEncoder: QueryParamEncoder[String] =
    new QueryParamEncoder[String] {
      override def encode(value: String) =
        QueryParameterValue(value)
    }
  implicit lazy val uriQueryParamEncoder: QueryParamEncoder[Uri] =
    QueryParamEncoder[String].contramap(_.renderString)
}

/** Type class defining how to decode a [[QueryParameterValue]] into a `T`
  * @see QueryParamCodecLaws
  */
trait QueryParamDecoder[T] { outer =>
  def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, T]

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
        outer.decode(value).orElse(qpd.decode(value))
    }

  /** Validate the currently parsed value a function to Either[ParseFailure, *]. */
  def emap[U](f: T => Either[ParseFailure, U]): QueryParamDecoder[U] =
    emapValidatedNel(f.andThen(_.toValidatedNel))

  /** Validate the currently parsed value using a function to ValidatedNel[ParseFailure, *]. */
  def emapValidatedNel[U](f: T => ValidatedNel[ParseFailure, U]): QueryParamDecoder[U] =
    new QueryParamDecoder[U] {
      override def decode(value: QueryParameterValue) =
        outer.decode(value).andThen(f)
    }
}

object QueryParamDecoder {

  /** summon an implicit [[QueryParamDecoder]] */
  def apply[T](implicit ev: QueryParamDecoder[T]): QueryParamDecoder[T] = ev

  def fromCodec[T](implicit ev: QueryParamCodec[T]): QueryParamDecoder[T] = ev

  def fromUnsafeCast[T](cast: QueryParameterValue => T)(typeName: String): QueryParamDecoder[T] =
    new QueryParamDecoder[T] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, T] =
        Validated
          .catchNonFatal(cast(value))
          .leftMap(t => ParseFailure(s"Query decoding $typeName failed", t.getMessage))
          .toValidatedNel
    }

  def instantQueryParamDecoder(formatter: DateTimeFormatter): QueryParamDecoder[Instant] =
    new QueryParamDecoder[Instant] {
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, Instant] =
        Validated
          .catchOnly[DateTimeParseException] {
            val x: TemporalAccessor = formatter.parse(value.value)
            Instant.from(x)
          }
          .leftMap { e =>
            ParseFailure(s"Failed to decode value: ${value.value} as Instant", e.getMessage)
          }
          .toValidatedNel
    }

  def localDateQueryParamDecoder(formatter: DateTimeFormatter): QueryParamDecoder[LocalDate] =
    (value: QueryParameterValue) =>
      Validated
        .catchOnly[DateTimeParseException] {
          val x: TemporalAccessor = formatter.parse(value.value)
          LocalDate.from(x)
        }
        .leftMap { e =>
          ParseFailure(s"Failed to decode value: ${value.value} as LocalDate", e.getMessage)
        }
        .toValidatedNel

  def zonedDateTimeQueryParamDecoder(
      formatter: DateTimeFormatter): QueryParamDecoder[ZonedDateTime] =
    (value: QueryParameterValue) =>
      Validated
        .catchOnly[DateTimeParseException] {
          val x: TemporalAccessor = formatter.parse(value.value)
          ZonedDateTime.from(x)
        }
        .leftMap { e =>
          ParseFailure(s"Failed to decode value: ${value.value} as ZonedDateTime", e.getMessage)
        }
        .toValidatedNel

  /** QueryParamDecoder is a covariant functor. */
  implicit val FunctorQueryParamDecoder: Functor[QueryParamDecoder] =
    new Functor[QueryParamDecoder] {
      override def map[A, B](fa: QueryParamDecoder[A])(f: A => B) =
        fa.map(f)
    }

  /** QueryParamDecoder is a MonoidK. */
  implicit val PlusEmptyQueryParamDecoder: MonoidK[QueryParamDecoder] =
    new MonoidK[QueryParamDecoder] {
      def empty[A] =
        fail[A]("Decoding failed.", "Empty decoder (always fails).")
      def combineK[A](a: QueryParamDecoder[A], b: QueryParamDecoder[A]) =
        a.orElse(b)
    }

  /** A decoder that always succeeds. */
  def success[A](a: A): QueryParamDecoder[A] =
    fromUnsafeCast[A](_ => a)("Success")

  /** A decoder that always fails. */
  def fail[A](sanitized: String, detail: String): QueryParamDecoder[A] =
    new QueryParamDecoder[A] {
      override def decode(value: QueryParameterValue) =
        ParseFailure(sanitized, detail).invalidNel
    }

  implicit lazy val unitQueryParamDecoder: QueryParamDecoder[Unit] =
    success(())
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

  implicit lazy val charQueryParamDecoder: QueryParamDecoder[Char] = new QueryParamDecoder[Char] {
    def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, Char] =
      if (value.value.size == 1) value.value.head.validNel
      else
        ParseFailure(
          "Failed to parse Char query parameter",
          s"Could not parse ${value.value} as a Char").invalidNel
  }

  implicit lazy val stringQueryParamDecoder: QueryParamDecoder[String] =
    new QueryParamDecoder[String] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, String] =
        value.value.validNel
    }

  implicit val uriQueryParamDecoder: QueryParamDecoder[Uri] =
    new QueryParamDecoder[Uri] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, Uri] =
        Uri.fromString(value.value).toValidatedNel
    }
}
