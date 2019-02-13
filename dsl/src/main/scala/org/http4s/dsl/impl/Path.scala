/*
 * Derived from Twitter Finagle.
 *
 * Original source:
 * https://github.com/twitter/finagle/blob/6e2462acc32ac753bf4e9d8e672f9f361be6b2da/finagle-http/src/main/scala/com/twitter/finagle/http/path/Path.scala
 */

package org.http4s.dsl.impl

import cats.data._
import cats.data.Validated._
import cats.implicits._
import org.http4s._
import org.http4s.util.UrlCodingUtils
import scala.util.Try

/** Base class for path extractors. */
trait Path {
  def /(child: String) = new /(this, child)
  def toList: List[String]
  def parent: Path
  def lastOption: Option[String]
  def startsWith(other: Path): Boolean
}

object Path {

  /** Constructs a path from a single string by splitting on the `'/'`
    * character.
    *
    * Leading slashes do not create an empty path segment.  This is to
    * reflect that there is no distinction between a request to
    * `http://www.example.com` from `http://www.example.com/`.
    *
    * Trailing slashes result in a path with an empty final segment,
    * unless the path is `"/"`, which is `Root`.
    *
    * Segments are URL decoded.
    *
    * {{{
    * scala> Path("").toList
    * res0: List[String] = List()
    * scala> Path("/").toList
    * res1: List[String] = List()
    * scala> Path("a").toList
    * res2: List[String] = List(a)
    * scala> Path("/a").toList
    * res3: List[String] = List(a)
    * scala> Path("/a/").toList
    * res4: List[String] = List(a, "")
    * scala> Path("//a").toList
    * res5: List[String] = List("", a)
    * scala> Path("/%2F").toList
    * res0: List[String] = List(/)
    * }}}
    */
  def apply(str: String): Path =
    if (str == "" || str == "/")
      Root
    else {
      val segments = str.split("/", -1)
      // .head is safe because split always returns non-empty array
      val segments0 = if (segments.head == "") segments.drop(1) else segments
      segments0.foldLeft(Root: Path)((path, seg) => path / UrlCodingUtils.urlDecode(seg))
    }

  def apply(first: String, rest: String*): Path =
    rest.foldLeft(Root / first)(_ / _)

  def apply(list: List[String]): Path =
    list.foldLeft(Root: Path)(_ / _)

  def unapplySeq(path: Path): Some[List[String]] =
    Some(path.toList)

  def unapplySeq[F[_]](request: Request[F]): Some[List[String]] =
    Some(Path(request.pathInfo).toList)
}

object :? {
  def unapply[F[_]](req: Request[F]): Some[(Request[F], Map[String, Seq[String]])] =
    Some((req, req.multiParams))
}

/** File extension extractor */
object ~ {

  /**
    * File extension extractor for Path:
    *   Path("example.json") match {
    *     case Root / "example" ~ "json" => ...
    */
  def unapply(path: Path): Option[(Path, String)] =
    path match {
      case Root => None
      case parent / last =>
        unapply(last).map {
          case (base, ext) => (parent / base, ext)
        }
    }

  /**
    * File extension matcher for String:
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

final case class /(parent: Path, child: String) extends Path {
  lazy val toList: List[String] = parent.toList ++ List(child)

  def lastOption: Some[String] = Some(child)

  lazy val asString: String = s"$parent/${UrlCodingUtils.pathEncode(child)}"

  override def toString: String = asString

  def startsWith(other: Path): Boolean = {
    val components = other.toList
    toList.take(components.length) === components
  }
}

object -> {

  /**
    * HttpMethod extractor:
    * {{{
    *   (request.method, Path(request.path)) match {
    *     case Method.GET -> Root / "test.json" => ...
    * }}}
    */
  def unapply[F[_]](req: Request[F]): Some[(Method, Path)] =
    Some((req.method, Path(req.pathInfo)))
}

class MethodConcat(val methods: Set[Method]) {

  /**
    * HttpMethod 'or' extractor:
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

/**
  * Root extractor:
  * {{{
  *   Path("/") match {
  *     case Root => ...
  *   }
  * }}}
  */
case object Root extends Path {
  def toList: List[String] = Nil

  def parent: Path = this

  def lastOption: None.type = None

  override def toString = ""

  def startsWith(other: Path): Boolean = other == Root
}

/**
  * Path separator extractor:
  * {{{
  *   Path("/1/2/3/test.json") match {
  *     case "1" /: "2" /: _ =>  ...
  * }}}
  */
object /: {
  def unapply(path: Path): Option[(String, Path)] =
    path.toList match {
      case head :: tail => Some(head -> Path(tail))
      case Nil => None
    }
}

protected class PathVar[A](cast: String => Try[A]) {
  def unapply(str: String): Option[A] =
    if (!str.isEmpty)
      cast(str).toOption
    else
      None
}

/**
  * Integer extractor of a path variable:
  * {{{
  *   Path("/user/123") match {
  *      case Root / "user" / IntVar(userId) => ...
  * }}}
  */
object IntVar extends PathVar(str => Try(str.toInt))

/**
  * Long extractor of a path variable:
  * {{{
  *   Path("/user/123") match {
  *      case Root / "user" / LongVar(userId) => ...
  * }}}
  */
object LongVar extends PathVar(str => Try(str.toLong))

/**
  * UUID extractor of a path variable:
  * {{{
  *   Path("/user/13251d88-7a73-4fcf-b935-54dfae9f023e") match {
  *      case Root / "user" / UUIDVar(userId) => ...
  * }}}
  */
object UUIDVar extends PathVar(str => Try(java.util.UUID.fromString(str)))

/**
  * Multiple param extractor:
  * {{{
  *   object A extends QueryParamDecoderMatcher[String]("a")
  *   object B extends QueryParamDecoderMatcher[Int]("b")
  *   val routes = HttpRoutes.of {
  *     case GET -> Root / "user" :? A(a) +& B(b) => ...
  * }}}
  */
object +& {
  def unapply(params: Map[String, Seq[String]])
    : Some[(Map[String, Seq[String]], Map[String, Seq[String]])] =
    Some((params, params))
}

/**
  * param extractor using [[QueryParamDecoder]]:
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
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[T]] =
    params
      .get(name)
      .flatMap(values =>
        values.toList.traverse(s => QueryParamDecoder[T].decode(QueryParameterValue(s)).toOption))

  def unapply(params: Map[String, Seq[String]]): Option[T] =
    params
      .get(name)
      .flatMap(_.headOption)
      .flatMap(s => QueryParamDecoder[T].decode(QueryParameterValue(s)).toOption)
}

/**
  * param extractor using [[QueryParamDecoder]]:
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
  def unapply(params: Map[String, Seq[String]]): Option[Option[T]] =
    params
      .get(name)
      .flatMap(_.headOption)
      .traverse(s => QueryParamDecoder[T].decode(QueryParameterValue(s)))
      .toOption
}

/**
  * Flag (value-less) query param extractor
  */
abstract class FlagQueryParamMatcher(name: String) {
  def unapply(params: Map[String, Seq[String]]): Option[Boolean] =
    Some(params.contains(name))
}

/**
  * Capture a query parameter that appears 0 or more times.
  *
  * {{{
  *   case class Foo(i: Int)
  *   implicit val fooDecoder: QueryParamDecoder[Foo] = ...
  *   implicit val fooParam: QueryParam[Foo] = ...
  *
  *   object FooMatcher extends OptionalMultiQueryParamDecoderMatcher[Foo]("foo")
  *   val routes = HttpRoutes.of {
  *     // matches http://.../closest?foo=2&foo=3&foo=4
  *     case GET -> Root / "closest" :? FooMatcher(Some(Seq(2,3,4))) => ...
  *
  *     /*
  *     *  matches http://.../closest?foo=2&foo=3&foo=4 as well as http://.../closest (no parameters)
  *     *  or http://.../closest?foo=2 (single occurrence)
  *     */
  *     case GET -> Root / "closest" :? FooMatcher(is) => ...
  * }}}
  */
abstract class OptionalMultiQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapply(params: Map[String, Seq[String]]): Option[ValidatedNel[ParseFailure, List[T]]] =
    params.get(name) match {
      case Some(values) =>
        Some(values.toList.traverse(s => QueryParamDecoder[T].decode(QueryParameterValue(s))))
      case None => Some(Valid(Nil)) // absent
    }
}

abstract class OptionalQueryParamMatcher[T: QueryParamDecoder: QueryParam]
    extends OptionalQueryParamDecoderMatcher[T](QueryParam[T].key.value)

/**
  *  param extractor using [[org.http4s.QueryParamDecoder]]. Note that this will return a
  *  [[ParseFailure]] if the parameter cannot be decoded.
  *
  * {{{
  *  case class Foo(i: Int)
  *  implicit val fooDecoder: QueryParamDecoder[Foo] = ...
  *
  *  object FooMatcher extends ValidatingQueryParamDecoderMatcher[Foo]("foo")
  *  val routes: HttpRoutes.of = {
  *    case GET -> Root / "closest" :? FooMatcher(fooValue) => {
  *      fooValue.fold(
  *        nelE => BadRequest(nelE.toList.map(_.sanitized).mkString("\n")),
  *        foo  => { ... }
  *      )
  *    }
  * }}}
  */
abstract class ValidatingQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapply(params: Map[String, Seq[String]]): Option[ValidatedNel[ParseFailure, T]] =
    params.get(name).flatMap(_.headOption).map { s =>
      QueryParamDecoder[T].decode(QueryParameterValue(s))
    }
}

/**
  *  param extractor using [[org.http4s.QueryParamDecoder]]. Note that this will _always_ match, but will return
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
  *    case GET -> Root / "closest" :? FooMatcher(fooValue) +& BarMatcher(barValue) => {
  *      ^(fooValue, barValue getOrElse 42.right) { (foo, bar) =>
  *        ...
  *      }.fold(
  *        nelE => BadRequest(nelE.toList.map(_.sanitized).mkString("\n")),
  *        baz  => { ... }
  *      )
  *    }
  * }}}
  */
abstract class OptionalValidatingQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapply(params: Map[String, Seq[String]]): Some[Option[ValidatedNel[ParseFailure, T]]] =
    Some {
      params.get(name).flatMap(_.headOption).fold[Option[ValidatedNel[ParseFailure, T]]](None) {
        s =>
          Some(QueryParamDecoder[T].decode(QueryParameterValue(s)))
      }
    }
}
