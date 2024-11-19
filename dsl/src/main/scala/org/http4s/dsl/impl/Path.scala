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

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/twitter/finagle/blob/6e2462acc32ac753bf4e9d8e672f9f361be6b2da/finagle-http/src/main/scala/com/twitter/finagle/http/path/Path.scala
 * Copyright 2017, Twitter Inc.
 */

package org.http4s.dsl.impl

import cats.Applicative
import cats.Foldable
import cats.Monad
import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import org.http4s.Uri.Path
import org.http4s.Uri.Path._
import org.http4s._
import org.http4s.headers.Allow

import scala.util.Try

object :? {
  def unapply[F[_]](req: Request[F]): Some[(Request[F], Map[String, collection.Seq[String]])] =
    Some((req, req.multiParams))
}

/** File extension extractor */
object ~ {

  /** File extension extractor for Path:
    *   Path("example.json") match {
    *     case Root / "example" ~ "json" => ...
    */
  def unapply(path: Path): Option[(Path, String)] =
    (path: @unchecked) match {
      case Root => None
      case parent / last =>
        unapply(last).map { case (base, ext) =>
          (parent / Path.Segment(base), ext)
        }
    }

  /** File extension matcher for String:
    * {{{
    *   "example.json" match {
    *      case => "example" ~ "json" => ...
    * }}}
    */
  def unapply(fileName: String): Option[(String, String)] =
    fileName.lastIndexOf('.') match {
      case -1 => Some((fileName, ""))
      case index => Some((fileName.substring(0, index), fileName.substring(index + 1)))
    }
}

object / {
  def unapply(path: Path): Option[(Path, String)] =
    if (path != Root && path.endsWithSlash)
      Some(path.dropEndsWithSlash -> "")
    else
      path.segments match {
        case allButLast :+ last if allButLast.isEmpty =>
          if (path.absolute)
            Some(Root -> last.decoded())
          else
            Some(empty -> last.decoded())
        case allButLast :+ last =>
          Some(Path(allButLast, absolute = path.absolute) -> last.decoded())
        case _ => None
      }
}

object -> {

  /** HttpMethod extractor:
    * {{{
    *   (request.method, Path(request.path)) match {
    *     case Method.GET -> Root / "test.json" => ...
    * }}}
    */
  def unapply[F[_]](req: Request[F]): Some[(Method, Path)] =
    Some((req.method, req.pathInfo))
}

object ->> {
  private val allMethods = Method.all.toSet

  /** Extractor to match an http resource and then enumerate all supported methods:
    * {{{
    *   (request.method, Path(request.path)) match {
    *     case withMethod ->> Root / "test.json" => withMethod {
    *       case Method.GET => ...
    *       case Method.POST => ...
    * }}}
    *
    * Returns an error response if the method is not matched, in accordance with [[https://datatracker.ietf.org/doc/html/rfc7231#section-4.1 RFC7231]]
    */
  def unapply[F[_]: Applicative](
      req: Request[F]
  ): Some[(PartialFunction[Method, F[Response[F]]] => F[Response[F]], Path)] =
    Some {
      (
        pf =>
          pf.applyOrElse(
            req.method,
            (method: Method) =>
              Applicative[F].pure {
                if (allMethods.contains(method)) {
                  Response(
                    status = Status.MethodNotAllowed,
                    headers = Headers(Allow(allMethods.filter(pf.isDefinedAt))),
                  )
                } else { Response(status = Status.NotImplemented) }
              },
          ),
        req.pathInfo,
      )

    }
}

class MethodConcat(val methods: Set[Method]) {

  /** HttpMethod 'or' extractor:
    * {{{
    *  val request: Request = ???
    *  request match {
    *    case (Method.GET | Method.POST) -> Root / "123" => ???
    *  }
    * }}}
    */
  def unapply(method: Method): Option[Method] =
    Some(method).filter(methods)
}

/** Path separator extractor:
  * {{{
  *   Path("/1/2/3/test.json") match {
  *     case "1" /: "2" /: _ =>  ...
  * }}}
  */
object /: {
  def unapply(path: Path): Option[(String, Path)] =
    path.segments match {
      case head +: tail => Some(head.decoded() -> Path(tail))
      case _ => None
    }
}

/** Abstract extractor of a path variable:
  * {{{
  *   enum Color:
  *     case Red, Green, Blue
  *
  *   val ColorPath = PathVar.fromTry(str => Try(Color.valueOf(str)))
  *
  *   Path("/Green") match {
  *     case Root / ColorPath(color) => ...
  * }}}
  */
class PathVar[A] private[impl] (cast: String => Option[A]) {
  def unapply(str: String): Option[A] =
    if (str.nonEmpty) cast(str)
    else None
}

object PathVar {
  def of[A](cast: String => A): PathVar[A] =
    new PathVar(str => Option(cast(str)))

  def fromPartialFunction[A](cast: PartialFunction[String, A]): PathVar[A] =
    new PathVar(str => cast.lift(str))

  def fromTry[A](cast: String => Try[A]): PathVar[A] =
    new PathVar(str => cast(str).toOption)
}

/** Integer extractor of a path variable:
  * {{{
  *   Path("/user/123") match {
  *      case Root / "user" / IntVar(userId) => ...
  * }}}
  */
object IntVar extends PathVar(str => Try(str.toInt).toOption)

/** Long extractor of a path variable:
  * {{{
  *   Path("/user/123") match {
  *      case Root / "user" / LongVar(userId) => ...
  * }}}
  */
object LongVar extends PathVar(str => Try(str.toLong).toOption)

/** UUID extractor of a path variable:
  * {{{
  *   Path("/user/13251d88-7a73-4fcf-b935-54dfae9f023e") match {
  *      case Root / "user" / UUIDVar(userId) => ...
  * }}}
  */
object UUIDVar extends PathVar(str => Try(java.util.UUID.fromString(str)).toOption)

/** Matrix path variable extractor
  * For an example see [[https://www.w3.org/DesignIssues/MatrixURIs.html MatrixURIs]]
  * This is useful for representing a resource that may be addressed in multiple dimensions where order is unimportant
  *
  * {{{
  *
  *    object BoardVar extends MatrixVar("square", List("x", "y"))
  *    Path("/board/square;x=5;y=3") match {
  *      case Root / "board" / BoardVar(IntVar(x), IntVar(y)) => ...
  *    }
  * }}}
  */
abstract class MatrixVar[F[_]: Foldable](name: String, domain: F[String]) {
  private val domainList = domain.toList

  def unapplySeq(str: String): Option[Seq[String]] =
    if (str.nonEmpty) {
      val firstSemi = str.indexOf(';')
      if (firstSemi < 0 && (domain.nonEmpty || name != str)) None
      else if (firstSemi < 0 && name == str) Some(Seq.empty[String])
      // Matrix segment didn't match the expected name
      else if (str.substring(0, firstSemi) != name) None
      else {
        val assocListOpt =
          if (firstSemi >= 0)
            Monad[Option].tailRecM(MatrixVar.RecState(str, firstSemi + 1, List.empty))(toAssocList)
          else Some(List.empty[(String, String)])
        assocListOpt.flatMap { assocList =>
          domainList.traverse(dom => assocList.find(_._1 == dom).map(_._2))
        }
      }
    } else None

  private def toAssocList(
      recState: MatrixVar.RecState
  ): Option[Either[MatrixVar.RecState, List[(String, String)]]] =
    // We can't extract anything else but there was a trailing ;
    if (recState.position >= recState.str.length - 1)
      Some(Right(recState.accumulated))
    else {
      val nextSplit = recState.str.indexOf(';', recState.position)
      // This is the final ; delimited segment
      if (nextSplit < 0)
        toAssocListElem(recState.str, recState.position, recState.str.length)
          .map(elem => Right(elem :: recState.accumulated))
      // An internal empty ; delimited segment so just skip
      else if (nextSplit == recState.position)
        Some(Left(recState.copy(position = nextSplit + 1)))
      else
        toAssocListElem(recState.str, recState.position, nextSplit)
          .map(elem =>
            Left(
              recState.copy(position = nextSplit + 1, accumulated = elem :: recState.accumulated)
            )
          )
    }

  private def toAssocListElem(str: String, position: Int, end: Int): Option[(String, String)] = {
    val delimSplit = str.indexOf('=', position)
    val nextDelimSplit = str.indexOf('=', delimSplit + 1)
    // if the segment does not contain an = inside then it is invalid
    if (delimSplit < 0 || delimSplit === position || delimSplit >= end) None
    // if the segment contains multiple = then it is invalid
    else if (nextDelimSplit < end && nextDelimSplit >= 0) None
    else Some(str.substring(position, delimSplit) -> str.substring(delimSplit + 1, end))
  }
}

object MatrixVar {
  private final case class RecState(str: String, position: Int, accumulated: List[(String, String)])
}

/** Multiple param extractor:
  * {{{
  *   object A extends QueryParamDecoderMatcher[String]("a")
  *   object B extends QueryParamDecoderMatcher[Int]("b")
  *   val routes = HttpRoutes.of {
  *     case GET -> Root / "user" :? A(a) +& B(b) => ...
  * }}}
  */
object +& {
  def unapply(
      params: Map[String, collection.Seq[String]]
  ): Some[(Map[String, collection.Seq[String]], Map[String, collection.Seq[String]])] =
    Some((params, params))
}

/** param extractor using [[QueryParamDecoder]]:
  * {{{
  *   case class Foo(i: Int)
  *   implicit val fooDecoder: QueryParamDecoder[Foo] = ...
  *
  *   object FooMatcher extends QueryParamDecoderMatcher[Foo]("foo")
  *   val routes = HttpRoutes.of {
  *     case GET -> Root / "closest" :? FooMatcher(2) => ...
  * }}}
  */
abstract class QueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapplySeq(params: Map[String, collection.Seq[String]]): Option[collection.Seq[T]] =
    params
      .get(name)
      .flatMap(values =>
        values.toList.traverse(s => QueryParamDecoder[T].decode(QueryParameterValue(s)).toOption)
      )

  def unapply(params: Map[String, collection.Seq[String]]): Option[T] =
    params
      .get(name)
      .flatMap(_.headOption)
      .flatMap(s => QueryParamDecoder[T].decode(QueryParameterValue(s)).toOption)
}

/** param extractor using [[QueryParamDecoder]]:
  *
  * {{{
  *   case class Foo(i: Int)
  *   implicit val fooDecoder: QueryParamDecoder[Foo] = ...
  *   implicit val fooParam: QueryParam[Foo] = ...
  *
  *   object FooMatcher extends QueryParamDecoderMatcher[Foo]
  *   val routes = HttpRoutes.of {
  *     case GET -> Root / "closest" :? FooMatcher(2) => ...
  * }}}
  */
abstract class QueryParamMatcher[T: QueryParamDecoder: QueryParam]
    extends QueryParamDecoderMatcher[T](QueryParam[T].key.value)

abstract class OptionalQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapply(params: Map[String, collection.Seq[String]]): Option[Option[T]] =
    params
      .get(name)
      .flatMap(_.headOption)
      .traverse(s => QueryParamDecoder[T].decode(QueryParameterValue(s)))
      .toOption
}

/** A param extractor with a default value. If the query param is not present, the default value is returned
  * If the query param is present but incorrectly formatted, will return `None`
  */
abstract class QueryParamDecoderMatcherWithDefault[T: QueryParamDecoder](name: String, default: T) {
  def unapply(params: Map[String, collection.Seq[String]]): Option[T] =
    params
      .get(name)
      .flatMap(_.headOption)
      .traverse(s => QueryParamDecoder[T].decode(QueryParameterValue(s)))
      .toOption
      .map(_.getOrElse(default))
}

abstract class QueryParamMatcherWithDefault[T: QueryParamDecoder: QueryParam](default: T)
    extends QueryParamDecoderMatcherWithDefault[T](QueryParam[T].key.value, default)

/** Flag (value-less) query param extractor
  */
abstract class FlagQueryParamMatcher(name: String) {
  def unapply(params: Map[String, collection.Seq[String]]): Option[Boolean] =
    Some(params.contains(name))
}

/** Capture a query parameter that appears 0 or more times.
  *
  * {{{
  *   case class Foo(i: Int)
  *   implicit val fooDecoder: QueryParamDecoder[Foo] = ...
  *   implicit val fooParam: QueryParam[Foo] = ...
  *
  *   object FooMatcher extends OptionalMultiQueryParamDecoderMatcher[Foo]("foo")
  *   val routes = HttpRoutes.of {
  *     // matches http://.../closest?foo=2&foo=3&foo=4
  *     case GET -> Root / "closest" :? FooMatcher(Validated.Valid(Seq(Foo(2),Foo(3),Foo(4)))) => ...
  *
  *     /*
  *     *  matches http://.../closest?foo=2&foo=3&foo=4 as well as http://.../closest (no parameters)
  *     *  or http://.../closest?foo=2 (single occurrence)
  *     */
  *     case GET -> Root / "closest" :? FooMatcher(is) => ...
  * }}}
  */
abstract class OptionalMultiQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapply(
      params: Map[String, collection.Seq[String]]
  ): Option[ValidatedNel[ParseFailure, List[T]]] =
    params.get(name) match {
      case Some(values) =>
        Some(values.toList.traverse(s => QueryParamDecoder[T].decode(QueryParameterValue(s))))
      case None => Some(Valid(Nil)) // absent
    }
}

abstract class OptionalQueryParamMatcher[T: QueryParamDecoder: QueryParam]
    extends OptionalQueryParamDecoderMatcher[T](QueryParam[T].key.value)

/**  param extractor using [[org.http4s.QueryParamDecoder]]. Note that this will return a
  *  [[ParseFailure]] if the parameter cannot be decoded.
  *
  * {{{
  *  case class Foo(i: Int)
  *  implicit val fooDecoder: QueryParamDecoder[Foo] = ...
  *
  *  object FooMatcher extends ValidatingQueryParamDecoderMatcher[Foo]("foo")
  *  val routes: HttpRoutes.of = {
  *    case GET -> Root / "closest" :? FooMatcher(fooValue) =>
  *      fooValue.fold(
  *        nelE => BadRequest(nelE.toList.map(_.sanitized).mkString("\n")),
  *        foo  => { ... }
  *      )
  * }}}
  */
abstract class ValidatingQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapply(params: Map[String, collection.Seq[String]]): Option[ValidatedNel[ParseFailure, T]] =
    params.get(name).flatMap(_.headOption).map { s =>
      QueryParamDecoder[T].decode(QueryParameterValue(s))
    }
}

/**  param extractor using [[org.http4s.QueryParamDecoder]]. Note that this will _always_ match, but will return
  *  an Option possibly containing the result of the conversion to T
  *
  * {{{
  *  case class Foo(i: Int)
  *  implicit val fooDecoder: QueryParamDecoder[Foo] = ...
  *
  *  case class Bar(i: Int)
  *  implicit val barDecoder: QueryParamDecoder[Bar] = ...
  *
  *  object FooMatcher extends ValidatingQueryParamDecoderMatcher[Foo]("foo")
  *  object BarMatcher extends OptionalValidatingQueryParamDecoderMatcher[Bar]("bar")
  *
  *  val routes = HttpRoutes.of {
  *    case GET -> Root / "closest" :? FooMatcher(fooValue) +& BarMatcher(barValue) =>
  *      ^(fooValue, barValue getOrElse 42.right) { (foo, bar) =>
  *        ...
  *      }.fold(
  *        nelE => BadRequest(nelE.toList.map(_.sanitized).mkString("\n")),
  *        baz  => { ... }
  *      )
  * }}}
  */
abstract class OptionalValidatingQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapply(
      params: Map[String, collection.Seq[String]]
  ): Some[Option[ValidatedNel[ParseFailure, T]]] =
    Some {
      params.get(name).flatMap(_.headOption).fold[Option[ValidatedNel[ParseFailure, T]]](None) {
        s =>
          Some(QueryParamDecoder[T].decode(QueryParameterValue(s)))
      }
    }
}
