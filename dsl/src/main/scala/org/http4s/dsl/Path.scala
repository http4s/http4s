/*
 * Derived from Twitter Finagle.
 *
 * Original source:
 * https://github.com/twitter/finagle/blob/6e2462acc32ac753bf4e9d8e672f9f361be6b2da/finagle-http/src/main/scala/com/twitter/finagle/http/path/Path.scala
 */

package org.http4s
package dsl

import org.http4s.QueryParamDecoder

import scala.util.control.Exception.catching
import scalaz.syntax.traverse._
import scalaz.std.list._

/** Base class for path extractors. */
abstract class Path {
  def /(child: String) = new /(this, child)
  def toList: List[String]
  def parent: Path
  def lastOption: Option[String]
  def startsWith(other: Path): Boolean
}

object Path {
  def apply(str: String): Path =
    if (str == "" || str == "/")
      Root
    else if (!str.startsWith("/"))
      Path("/" + str)
    else {
      val slash = str.lastIndexOf('/')
      val prefix = Path(str.substring(0, slash))
      if (slash == str.length - 1)
        prefix
      else
        prefix / str.substring(slash + 1)
    }

  def apply(first: String, rest: String*): Path =
    rest.foldLeft(Root / first)(_ / _)

  def apply(list: List[String]): Path = list.foldLeft(Root: Path)(_ / _)

  def unapplySeq(path: Path): Option[List[String]] = Some(path.toList)

  def unapplySeq(request: Request): Option[List[String]] = Some(Path(request.pathInfo).toList)

  def unapply(request: Request): Option[Path] = Some(Path(request.pathInfo))

}

object :? {
  def unapply(req: Request): Option[(Request, Map[String, Seq[String]])] = {
    Some((req, req.multiParams))
  }
}

/** File extension extractor */
object ~ {
  /**
   * File extension extractor for Path:
   *   Path("example.json") match {
   *     case Root / "example" ~ "json" => ...
   */
  def unapply(path: Path): Option[(Path, String)] = {
    path match {
      case Root => None
      case parent / last =>
        unapply(last) map {
          case (base, ext) => (parent / base, ext)
        }
    }
  }

  /**
   * File extension matcher for String:
   *   "example.json" match {
   *      case => "example" ~ "json" => ...
   */
  def unapply(fileName: String): Option[(String, String)] = {
    fileName.lastIndexOf('.') match {
      case -1 => Some((fileName, ""))
      case index => Some((fileName.substring(0, index), fileName.substring(index + 1)))
    }
  }
}

case class /(parent: Path, child: String) extends Path {
  lazy val toList: List[String] = parent.toList ++ List(child)
  def lastOption: Option[String] = Some(child)
  lazy val asString = parent.toString + "/" + child
  override def toString = asString
  def startsWith(other: Path) = {
    val components = other.toList
    (toList take components.length) == components
  }
}

object -> {
  /**
   * HttpMethod extractor:
   *   (request.method, Path(request.path)) match {
   *     case Method.GET -> Root / "test.json" => ...
   */
  def unapply(req: Request): Option[(Method, Path)] = {
    Some((req.method, Path(req.pathInfo)))
  }
}

/**
 * Root extractor:
 *   Path("/") match {
 *     case Root => ...
 *   }
 */
case object Root extends Path {
  def toList: List[String] = Nil
  def parent = this
  def lastOption: Option[String] = None
  override def toString = ""
  def startsWith(other: Path) = other == Root
}

// Base class for Integer and LongParam extractors.
protected class NumericPathVar[A <: AnyVal](cast: String => A) {
  def unapply(str: String): Option[A] = {
    if (!str.isEmpty && str.forall(Character.isDigit))
      try {
        Some(cast(str))
      } catch {
        case _: NumberFormatException =>
          None
      }
    else
      None
  }
}

/**
 * Integer extractor of a path variable:
 *   Path("/user/123") match {
 *      case Root / "user" / IntParam(userId) => ...
 */
object IntVar extends NumericPathVar(_.toInt)

/**
 * Long extractor of a path variable:
 *   Path("/user/123") match {
 *      case Root / "user" / LongParam(userId) => ...
 */
object LongVar extends NumericPathVar(_.toLong)

/**
 * Multiple param extractor:
 *   object A extends ParamMatcher("a")
 *   object B extends ParamMatcher("b")
 *   val service: HttpService = {
 *     case GET -> Root / "user" :? A(a) +& B(b) => ...
 */
object +& {
  def unapply(params: Map[String, Seq[String]]) = Some((params, params))
}


/**
 * param extractor using [[QueryParamDecoder]]:
 *   case class Foo(i: Int)
 *   implicit val fooDecoder: QueryParamDecoder[Foo] = ...
 *
 *   object FooMatcher extends QueryParamDecoderMatcher[Foo]("foo")
 *   val service: HttpService = {
 *     case GET -> Root / "closest" :? FooMatcher(2) => ...
 */
abstract class QueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[T]] =
    params.get(name).flatMap(values =>
      values.toList.traverseU(s =>
        QueryParamDecoder[T].decode(QueryParameterValue(s)).toOption
      )
    )

  def unapply(params: Map[String, Seq[String]]): Option[T] =
    params.get(name).flatMap(_.headOption).flatMap(s =>
      QueryParamDecoder[T].decode(QueryParameterValue(s)).toOption
    )
}

/**
 * param extractor using [[QueryParamDecoder]]:
 *   case class Foo(i: Int)
 *   implicit val fooDecoder: QueryParamDecoder[Foo] = ...
 *   implicit val fooParam: QueryParam[Foo] = ...
 *
 *   object FooMatcher extends QueryParamDecoderMatcher[Foo]
 *   val service: HttpService = {
 *     case GET -> Root / "closest" :? FooMatcher(2) => ...
 */
abstract class QueryParamMatcher[T: QueryParamDecoder: QueryParam]
  extends QueryParamDecoderMatcher[T](QueryParam[T].key.value)