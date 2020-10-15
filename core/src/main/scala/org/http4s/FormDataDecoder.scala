/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.Applicative
import cats.data.Validated.Valid
import cats.data.{Chain, ValidatedNel}
import cats.effect.Sync
import cats.implicits._

/** A decoder ware that uses [[QueryParamDecoder]] to decode values in [[org.http4s.UrlForm]]
  *
  * @example
  * {{{
  * scala> import cats.implicits._
  * scala> import cats.data._
  * scala> import org.http4s.FormDataDecoder._
  * scala> import org.http4s.ParseFailure
  * scala> case class Foo(a: String, b: Boolean)
  * scala> case class Bar(fs: List[Foo], f: Foo, d: Boolean)
  * scala>
  * scala> implicit val fooMapper = (
  *      |   field[String]("a"),
  *      |   field[Boolean]("b")
  *      | ).mapN(Foo.apply)
  * scala>
  * scala> val barMapper = (
  *      |   list[Foo]("fs"),
  *      |   nested[Foo]("f"),
  *      |   field[Boolean]("d")
  *      | ).mapN(Bar.apply)
  * scala>
  * scala> barMapper(
  *      |   Map(
  *      |    "fs[].a" -> Chain("a1", "a2"),
  *      |    "fs[].b" -> Chain("true", "false"),
  *      |    "f.a" -> Chain("fa"),
  *      |    "f.b" -> Chain("false"),
  *      |    "d" -> Chain("true"))
  *      | )
  * res1: ValidatedNel[ParseFailure, Bar] = Valid(Bar(List(Foo(a1,true), Foo(a2,false)),Foo(fa,false),true))
  *
  * }}}
  *
  * The companion object provides a [[EntityDecoder]] from
  * HTML form parameters.
  * @example
  * {{{
  *   import org.http4s.FormDataDecoder.formEntityDecoder
  *   HttpRoutes
  *    .of[F] {
  *      case req @ POST -> Root =>
  *        req.as[MyEntity].flatMap { entity =>
  *          Ok()
  *        }
  *    }
  * }}}
  *
  * For more examples, check the tests
  * https://github.com/http4s/http4s/blob/master/tests/src/test/scala/org/http4s/FormDataDecoderSpec.scala
  */
sealed trait FormDataDecoder[A] {
  def apply(data: Map[String, Chain[String]]): ValidatedNel[ParseFailure, A]

  def mapValidated[B](f: A => ValidatedNel[ParseFailure, B]): FormDataDecoder[B] =
    FormDataDecoder(this(_).andThen(f))

  /** Filters out empty strings including spaces before decoding
    */
  def sanitized: FormDataDecoder[A] =
    FormDataDecoder { data =>
      this(data.map { case (k, v) =>
        (k, v.filter(_.trim.nonEmpty))
      })
    }

}

object FormDataDecoder {
  type FormData = Map[String, Chain[String]]
  type Result[A] = ValidatedNel[ParseFailure, A]

  def apply[A](f: FormData => Result[A]): FormDataDecoder[A] =
    new FormDataDecoder[A] {
      def apply(data: FormData): Result[A] = f(data)
    }

  implicit def formEntityDecoder[F[_]: Sync, A](implicit
      fdd: FormDataDecoder[A]
  ): EntityDecoder[F, A] =
    UrlForm.entityDecoder[F].flatMapR { d =>
      fdd(d.values)
        .leftMap(es => InvalidMessageBodyFailure(es.map(_.sanitized).mkString_("\n")))
        .liftTo[DecodeResult[F, *]]
    }

  private def apply[Data, A](
      extract: FormData => Either[String, Data]
  )(decode: Data => Result[A]): FormDataDecoder[Either[String, A]] =
    new FormDataDecoder[Either[String, A]] {
      def apply(data: FormData): Result[Either[String, A]] =
        extract(data).traverse(decode(_))
    }

  implicit class FormDataDecoderSyntax[A](private val decoder: FormDataDecoder[Either[String, A]])
      extends AnyVal {

    def required: FormDataDecoder[A] =
      decoder.mapValidated(_.fold(ParseFailure(_, "").invalidNel[A], Valid(_)))

    def optional: FormDataDecoder[Option[A]] =
      decoder.map(_.map(Some(_)).getOrElse(None))

    /** Use a default value when the field is missing
      * @param defaultValue
      */
    def default(defaultValue: A): FormDataDecoder[A] =
      decoder.map(_.getOrElse(defaultValue))
  }

  def fieldEither[A](
      key: String
  )(implicit
      qpd: QueryParamDecoder[A]
  ): FormDataDecoder[Either[String, A]] =
    apply[String, A] { data =>
      nonEmptyFields(data)
        .get(key)
        .flatMap(_.headOption)
        .toRight(s"$key is missing")
    }(v => qpd.decode(QueryParameterValue(v)))

  def field[A: QueryParamDecoder](key: String) = fieldEither(key).required

  def fieldOptional[A: QueryParamDecoder](key: String) = fieldEither(key).optional

  /** For nested, this decoder assumes that the form parameter name use "." as deliminator for levels.
    * E.g. For a field named "bar" inside a nested class under the field "foo",
    * the parameter name is "foo.bar".
    */
  def nested[A: FormDataDecoder](key: String): FormDataDecoder[A] =
    nestedEither(key).required

  /** For nested, this decoder assumes that the form parameter name use "." as deliminator for levels.
    * E.g. For a field named "bar" inside a nested class under the field "foo",
    * the parameter name is "foo.bar".
    */
  def nestedOptional[A: FormDataDecoder](key: String): FormDataDecoder[Option[A]] =
    nestedEither(key).optional

  def nestedEither[A](
      key: String
  )(implicit
      fdd: FormDataDecoder[A]
  ): FormDataDecoder[Either[String, A]] =
    apply[FormData, A](extractPrefix(key + "."))(fdd.apply)

  def chain[A: FormDataDecoder](key: String): FormDataDecoder[Chain[A]] =
    chainEither(key).mapValidated(_.fold(_ => Valid(Chain.empty), Valid(_)))

  def chainOf[A](key: String)(qd: QueryParamDecoder[A]): FormDataDecoder[Chain[A]] =
    apply(data =>
      data
        .get(key + "[]")
        .orElse(data.get(key))
        .getOrElse(Chain.empty)
        .traverse(v => qd.decode(QueryParameterValue(v))))

  def chainEither[A](
      key: String
  )(implicit A: FormDataDecoder[A]): FormDataDecoder[Either[String, Chain[A]]] =
    apply[FormData, Chain[A]](extractPrefix(key + "[]."))(
      _.toList
        .map { case (k, cv) =>
          cv.map(v => List((k, Chain(v))))
        }
        .reduceOption { (left, right) =>
          left.zipWith(right)(_ ++ _)
        }
        .getOrElse(Chain.empty)
        .map(_.toMap)
        .traverse(d => A(d))
    )

  /** For repeated nested values, assuming that the form parameter name use "[]." as a suffix
    * E.g. "foos[].bar"
    */
  def list[A: FormDataDecoder](key: String) =
    chain(key).map(_.toList)

  /** For repeated primitive values, assuming that the form parameter name use "[]" as a suffix
    * E.g. "foos[]"
    */
  def listOf[A](key: String)(implicit A: QueryParamDecoder[A]) =
    chainOf(key)(A).map(_.toList)

  private def nonEmptyFields(data: FormData): FormData =
    data.filter(_._2.nonEmpty)

  private def extractPrefix(
      prefix: String
  )(data: FormData): Either[String, FormData] = {
    val extracted = nonEmptyFields(data).toList.mapFilter { case (k, v) =>
      if (k.startsWith(prefix))
        Some((k.stripPrefix(prefix), v))
      else None
    }.toMap

    if (extracted.isEmpty)
      Left(s"There are no keys that starts with $prefix")
    else Right(extracted)
  }

  implicit val formDataDecoderInstances: Applicative[FormDataDecoder] =
    new Applicative[FormDataDecoder] {

      def pure[A](a: A): FormDataDecoder[A] =
        apply(_ => Valid(a))

      override def map[A, B](fa: FormDataDecoder[A])(f: A => B): FormDataDecoder[B] =
        apply(data => fa(data).map(f))

      def ap[A, B](
          ff: FormDataDecoder[A => B]
      )(fa: FormDataDecoder[A]): FormDataDecoder[B] =
        apply { data =>
          fa(data).ap(ff(data))
        }
    }
}
