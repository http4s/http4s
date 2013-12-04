/*
 * Derived from Twitter Finagle.
 *
 * Original source:
 * https://github.com/twitter/finagle/blob/6e2462acc32ac753bf4e9d8e672f9f361be6b2da/finagle-http/src/main/scala/com/twitter/finagle/http/path/Path.scala
 */

package org.http4s
package dsl

import scala.util.control.Exception.catching

/** Base class for path extractors. */
abstract class Path {
  def /(child: String) = new /(this, child)
  def :?(params: Map[String, Seq[String]]) = new :?(this, params)
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
    rest.foldLeft(Root / first)( _ / _)

  def apply(list: List[String]): Path = list.foldLeft(Root : Path)(_ / _)

  def unapplySeq(path: Path): Option[List[String]] = Some(path.toList)

  def unapplySeq(request: Request): Option[List[String]] = Some(Path(request.pathInfo).toList)

  def unapply(request: Request): Option[Path] = Some(Path(request.pathInfo))

}


case class :?(path: Path, params: Map[String, Seq[String]]) {
  override def toString = params.toString
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

object :/ {
  def unapply(req: Request): Option[(Path, String)] = {
    val p = Path(req.pathInfo)
    Some(p.parent, p.lastOption.getOrElse(""))
  }
}

//object * {
//  def unapply(req: RequestPrelude): Option[(Method, Path)] = {
//    Some((req.requestMethod, Path(req.pathInfo)))
//  }
//}

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
   *     case Method.Get -> Root / "test.json" => ...
   */
  def unapply(req: Request): Option[(Method, Path)] = {
    Some((req.requestMethod, Path(req.pathInfo)))
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
protected class NumericParam[A <: AnyVal](cast: String => A) {
  def unapply(str: String): Option[A] = {
    if (!str.isEmpty && str.forall(Character.isDigit _))
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
 * Integer extractor:
 *   Path("/user/123") match {
 *      case Root / "user" / IntParam(userId) => ...
 */
object IntParam extends NumericParam(_.toInt)

/**
 * LongParam extractor:
 *   Path("/user/123") match {
 *      case Root / "user" / LongParam(userId) => ...
 */
object LongParam extends NumericParam(_.toLong)



/**
 * Multiple param extractor:
 *   object A extends ParamMatcher("a")
 *   object B extends ParamMatcher("b")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "user" :? A(a) :& B(b) => ...
 */
object :& {
  def unapply(params: Map[String, Seq[String]]) = Some((params, params))
}


/**
 * Param extractor:
 *   object ScreenName extends ParamMatcher("screen_name")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "user" :? ScreenName(screenName) => ...
 */
abstract class ParamMatcher(name: String) {
  def unapply(params: Map[String, Seq[String]]) = unapplySeq(params).flatMap(_.headOption)
  def unapplySeq(params: Map[String, Seq[String]]) = params.get(name)
}


/**
 * IntParam param extractor:
 *   object Page extends IntParamMatcher("page")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "blog" :? Page(page) => ...
 */
abstract class IntParamMatcher(name: String) {
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[Int]] =
    params.get(name) map { value =>
      (value map { v =>
        catching(classOf[NumberFormatException]) opt v.toInt
      }).flatten
    }

  def unapply(params: Map[String, Seq[String]]): Option[Int] = unapplySeq(params).flatMap(_.headOption)
}


/**
 * LongParam param extractor:
 *   object UserId extends LongParamMatcher("user_id")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "user" :? UserId(userId) => ...
 */
abstract class LongParamMatcher(name: String) {
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[Long]] =
    params.get(name) map { value =>
      (value map { v =>
        catching(classOf[NumberFormatException]) opt v.toLong
      }).flatten
    }

  def unapply(params: Map[String, Seq[String]]): Option[Long] = unapplySeq(params).flatMap(_.headOption)
}

/**
 * Double param extractor:
 *   object Latitude extends DoubleParamMatcher("lat")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "closest" :? Latitude("lat") => ...
 */
abstract class DoubleParamMatcher(name: String) {
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[Double]] =
    params.get(name) map { value =>
      (value map { v =>
        catching(classOf[NumberFormatException]) opt v.toDouble
      }).flatten
    }

  def unapply(params: Map[String, Seq[String]]): Option[Double] = unapplySeq(params).flatMap(_.headOption)
}