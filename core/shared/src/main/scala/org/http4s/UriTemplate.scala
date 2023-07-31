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

import org.http4s.Uri.{Fragment => _, Path => _, apply => _, unapply => _, _}
import org.http4s.UriTemplate._
import org.http4s.util.StringWriter

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/** Simple representation of a URI Template that can be rendered as RFC6570
  * conform string.
  *
  * This model reflects only a subset of RFC6570.
  *
  * Level 1 and Level 2 are completely modeled and
  * Level 3 features are limited to:
  *  - Path segments, slash-prefixed
  *  - Form-style query, ampersand-separated
  *  - Fragment expansion
  */
final case class UriTemplate(
    scheme: Option[Scheme] = None,
    authority: Option[Authority] = None,
    path: Path = Nil,
    query: UriTemplate.Query = Nil,
    fragment: Fragment = Nil,
) {

  /** Replaces any expansion type that matches the given `name`. If no matching
    * `expansion` could be found the same instance will be returned.
    */
  def expandAny[T: QueryParamEncoder](name: String, value: T): UriTemplate =
    expandPath(name, value).expandQuery(name, value).expandFragment(name, value)

  /** Replaces any expansion type in `fragment` that matches the given `name`.
    * If no matching `expansion` could be found the same instance will be
    * returned.
    */
  def expandFragment[T: QueryParamEncoder](name: String, value: T): UriTemplate =
    if (fragment.isEmpty) this
    else copy(fragment = expandFragmentN(fragment, name, QueryParamEncoder[T].encode(value).value))

  /** Replaces any expansion type in `path` that matches the given `name`. If no
    * matching `expansion` could be found the same instance will be returned.
    */
  def expandPath[T: QueryParamEncoder](name: String, values: List[T]): UriTemplate =
    copy(path = expandPathN(path, name, values.map(QueryParamEncoder[T].encode)))

  /** Replaces any expansion type in `path` that matches the given `name`. If no
    * matching `expansion` could be found the same instance will be returned.
    */
  def expandPath[T: QueryParamEncoder](name: String, value: T): UriTemplate =
    copy(path = expandPathN(path, name, QueryParamEncoder[T].encode(value) :: Nil))

  /** Replaces any expansion type in `query` that matches the specified `name`.
    * If no matching `expansion` could be found the same instance will be
    * returned.
    */
  def expandQuery[T: QueryParamEncoder](name: String, values: List[T]): UriTemplate =
    if (query.isEmpty) this
    else copy(query = expandQueryN(query, name, values.map(QueryParamEncoder[T].encode(_).value)))

  /** Replaces any expansion type in `query` that matches the specified `name`.
    * If no matching `expansion` could be found the same instance will be
    * returned.
    */
  def expandQuery(name: String): UriTemplate = expandQuery(name, List[String]())

  /** Replaces any expansion type in `query` that matches the specified `name`.
    * If no matching `expansion` could be found the same instance will be
    * returned.
    */
  def expandQuery[T: QueryParamEncoder](name: String, values: T*): UriTemplate =
    expandQuery(name, values.toList)

  override lazy val toString: String =
    renderUriTemplate(this)

  /** If no expansion is available an `Uri` will be created otherwise the
    * current instance of `UriTemplate` will be returned.
    */
  def toUriIfPossible: Try[Uri] =
    if (containsExpansions(this))
      Failure(
        new IllegalStateException(s"all expansions must be resolved to be convertable: $this")
      )
    else Success(toUri(this))
}

object UriTemplate {
  type Path = List[PathDef]
  type Query = List[QueryDef]
  type Fragment = List[FragmentDef]

  protected val unreserved: Set[Char] =
    (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') :+ '-' :+ '.' :+ '_' :+ '~').toSet

  //  protected val genDelims = ':' :: '/' :: '?' :: '#' :: '[' :: ']' :: '@' :: Nil
  //  protected val subDelims = '!' :: '$' :: '&' :: '\'' :: '(' :: ')' :: '*' :: '+' :: ',' :: ';' :: '=' :: Nil
  //  protected val reserved = genDelims ::: subDelims

  def isUnreserved(s: String): Boolean = s.forall(unreserved.contains)

  def isUnreservedOrEncoded(s: String): Boolean =
    Uri
      .encode(s, spaceIsPlus = true, toSkip = unreserved)
      .forall(c => unreserved.contains(c) || c == '%')

  protected def expandPathN(path: Path, name: String, values: List[QueryParameterValue]): Path = {
    val acc = new ArrayBuffer[PathDef]()
    def appendValues(): Unit =
      values.foreach { v =>
        acc.append(PathElm(v.value))
      }
    path.foreach {
      case p @ PathElm(_) => acc.append(p)
      case p @ VarExp(collection.Seq(n)) =>
        if (n == name) appendValues()
        else acc.append(p)
      case p @ VarExp(ns) =>
        if (ns.contains(name)) {
          appendValues()
          acc.append(VarExp(ns.filterNot(_ == name)))
        } else acc.append(p)
      case p @ ReservedExp(collection.Seq(n)) =>
        if (n == name) appendValues()
        else acc.append(p)
      case p @ ReservedExp(ns) =>
        if (ns.contains(name)) {
          appendValues()
          acc.append(VarExp(ns.filterNot(_ == name)))
        } else acc.append(p)
      case p @ PathExp(collection.Seq(n)) =>
        if (n == name) appendValues()
        else acc.append(p)
      case p @ PathExp(ns) =>
        if (ns.contains(name)) {
          appendValues()
          acc.append(PathExp(ns.filterNot(_ == name)))
        } else acc.append(p)
    }
    acc.toList
  }

  protected def expandQueryN(query: Query, name: String, values: List[String]): Query = {
    val acc = new ArrayBuffer[QueryDef]()
    query.foreach {
      case p @ ParamElm(_, _) => acc.append(p)
      case p @ ParamVarExp(r, List(n)) =>
        if (n == name) acc.append(ParamElm(r, values))
        else acc.append(p)
      case p @ ParamVarExp(r, ns) =>
        if (ns.contains(name)) {
          acc.append(ParamElm(r, values))
          acc.append(ParamVarExp(r, ns.filterNot(_ == name)))
        } else acc.append(p)
      case p @ ParamReservedExp(r, List(n)) =>
        if (n == name) acc.append(ParamElm(r, values))
        else acc.append(p)
      case p @ ParamReservedExp(r, ns) =>
        if (ns.contains(name)) {
          acc.append(ParamElm(r, values))
          acc.append(ParamReservedExp(r, ns.filterNot(_ == name)))
        } else acc.append(p)
      case p @ ParamExp(collection.Seq(n)) =>
        if (n == name) acc.append(ParamElm(name, values))
        else acc.append(p)
      case p @ ParamExp(ns) =>
        if (ns.contains(name)) {
          acc.append(ParamElm(name, values))
          acc.append(ParamExp(ns.filterNot(_ == name)))
        } else acc.append(p)
      case p @ ParamContExp(collection.Seq(n)) =>
        if (n == name) acc.append(ParamElm(name, values))
        else acc.append(p)
      case p @ ParamContExp(ns) =>
        if (ns.contains(name)) {
          acc.append(ParamElm(name, values))
          acc.append(ParamContExp(ns.filterNot(_ == name)))
        } else acc.append(p)
    }
    acc.toList
  }

  protected def expandFragmentN(fragment: Fragment, name: String, value: String): Fragment = {
    val acc = new ArrayBuffer[FragmentDef]()
    fragment.foreach {
      case p @ FragmentElm(_) => acc.append(p)
      case p @ SimpleFragmentExp(n) =>
        if (n == name) acc.append(FragmentElm(value)) else acc.append(p)
      case p @ MultiFragmentExp(collection.Seq(n)) =>
        if (n == name) acc.append(FragmentElm(value)) else acc.append(p)
      case p @ MultiFragmentExp(ns) =>
        if (ns.contains(name)) {
          acc.append(FragmentElm(value))
          acc.append(MultiFragmentExp(ns.filterNot(_ == name)))
        } else acc.append(p)
    }
    acc.toList
  }

  protected def renderAuthority(a: Authority): String =
    a match {
      case Authority(Some(u), h, None) => s"${renderUserInfo(u)}@${renderHost(h)}"
      case Authority(Some(u), h, Some(p)) => s"${renderUserInfo(u)}@${renderHost(h)}:${p}"
      case Authority(None, h, Some(p)) => renderHost(h) + ":" + p
      case Authority(_, h, _) => renderHost(h)
    }

  protected def renderUserInfo(u: UserInfo): String =
    u match {
      case UserInfo(username, Some(password)) =>
        (new StringWriter << username << ":" << password).result
      case UserInfo(username, None) => username
    }

  protected def renderHost(h: Host): String =
    h match {
      case RegName(n) => n.toString
      case a: Ipv4Address => a.value
      case a: Ipv6Address => "[" + a.value + "]"
    }

  protected def renderScheme(s: Scheme): String =
    (new StringWriter << s << ":").result

  protected def renderSchemeAndAuthority(t: UriTemplate): String =
    t match {
      case UriTemplate(None, None, _, _, _) => ""
      case UriTemplate(Some(s), Some(a), _, _, _) => renderScheme(s) + "//" + renderAuthority(a)
      case UriTemplate(Some(s), None, _, _, _) => renderScheme(s)
      case UriTemplate(None, Some(a), _, _, _) => renderAuthority(a)
    }

  protected def renderQuery(ps: Query): String = {
    val parted = ps.partition {
      case ParamElm(_, _) => false
      case ParamVarExp(_, _) => false
      case ParamReservedExp(_, _) => false
      case ParamExp(_) => true
      case ParamContExp(_) => true
    }
    val elements = new ArrayBuffer[String]()
    parted._2.foreach {
      case ParamElm(n, Nil) => elements.append(n)
      case ParamElm(n, List(v)) => elements.append(n + "=" + v)
      case ParamElm(n, vs) => vs.foreach(v => elements.append(n + "=" + v))
      case ParamVarExp(n, vs) => elements.append(n + "=" + "{" + vs.mkString(",") + "}")
      case ParamReservedExp(n, vs) => elements.append(n + "=" + "{+" + vs.mkString(",") + "}")
      case u => throw new IllegalStateException(s"type ${u.getClass.getName} not supported")
    }
    val exps = new ArrayBuffer[String]()
    def separator = if (elements.isEmpty && exps.isEmpty) "?" else "&"
    parted._1.foreach {
      case ParamExp(ns) => exps.append("{" + separator + ns.mkString(",") + "}")
      case ParamContExp(ns) => exps.append("{" + separator + ns.mkString(",") + "}")
      case u => throw new IllegalStateException(s"type ${u.getClass.getName} not supported")
    }
    if (elements.isEmpty) exps.mkString
    else "?" + elements.mkString("&") + exps.mkString
  }

  protected def renderFragment(f: Fragment): String = {
    val elements = new mutable.ArrayBuffer[String]()
    val expansions = new mutable.ArrayBuffer[String]()
    f.foreach {
      case FragmentElm(v) => elements.append(v)
      case SimpleFragmentExp(n) => expansions.append(n)
      case MultiFragmentExp(ns) => expansions.append(ns.mkString(","))
    }
    if (elements.nonEmpty && expansions.nonEmpty)
      "#" + elements.mkString(",") + "{#" + expansions.mkString(",") + "}"
    else if (elements.nonEmpty)
      "#" + elements.mkString(",")
    else if (expansions.nonEmpty)
      "{#" + expansions.mkString(",") + "}"
    else
      "#"
  }

  protected def renderFragmentIdentifier(f: Fragment): String = {
    val elements = new mutable.ArrayBuffer[String]()
    f.foreach {
      case FragmentElm(v) => elements.append(v)
      case SimpleFragmentExp(_) =>
        throw new IllegalStateException("SimpleFragmentExp cannot be converted to a Uri")
      case MultiFragmentExp(_) =>
        throw new IllegalStateException("MultiFragmentExp cannot be converted to a Uri")
    }
    if (elements.isEmpty) ""
    else elements.mkString(",")
  }

  protected def buildQuery(q: Query): org.http4s.Query = {
    val vec = q.foldLeft(Vector.empty[(String, Option[String])]) {
      case (elements, ParamElm(n, Nil)) => elements :+ (n -> None)
      case (elements, ParamElm(n, List(v))) => elements :+ (n -> Some(v))
      case (elements, ParamElm(n, vs)) =>
        vs.foldLeft(elements) { case (elements, v) => elements :+ (n -> Some(v)) }
      case u =>
        throw new IllegalStateException(s"${u.getClass.getName} cannot be converted to a Uri")
    }

    org.http4s.Query.fromVector(vec)
  }

  protected def renderPath(p: Path): String =
    p match {
      case Nil => "/"
      case ps =>
        val elements = new ArrayBuffer[String]()
        ps.foreach {
          case PathElm(n) => elements.append("/" + n)
          case VarExp(ns) => elements.append("{" + ns.mkString(",") + "}")
          case ReservedExp(ns) => elements.append("{+" + ns.mkString(",") + "}")
          case PathExp(ns) => elements.append("{/" + ns.mkString(",") + "}")
        }
        elements.mkString
    }

  protected def renderPathAndQueryAndFragment(t: UriTemplate): String =
    t match {
      case UriTemplate(_, _, Nil, Nil, Nil) => "/"
      case UriTemplate(_, _, Nil, Nil, f) => "/" + renderFragment(f)
      case UriTemplate(_, _, Nil, query, Nil) => "/" + renderQuery(query)
      case UriTemplate(_, _, Nil, query, f) => "/" + renderQuery(query) + renderFragment(f)
      case UriTemplate(_, _, path, Nil, Nil) => renderPath(path)
      case UriTemplate(_, _, path, query, Nil) => renderPath(path) + renderQuery(query)
      case UriTemplate(_, _, path, Nil, f) => renderPath(path) + renderFragment(f)
      case UriTemplate(_, _, path, query, f) =>
        renderPath(path) + renderQuery(query) + renderFragment(f)
    }

  protected def renderUriTemplate(t: UriTemplate): String =
    t match {
      case UriTemplate(None, None, Nil, Nil, Nil) => "/"
      case UriTemplate(Some(_), Some(_), Nil, Nil, Nil) => renderSchemeAndAuthority(t)
      case UriTemplate(scheme @ _, authority @ _, path @ _, params @ _, fragment @ _) =>
        renderSchemeAndAuthority(t) + renderPathAndQueryAndFragment(t)
    }

  protected def fragmentExp(f: FragmentDef): Boolean =
    f match {
      case FragmentElm(_) => false
      case SimpleFragmentExp(_) => true
      case MultiFragmentExp(_) => true
    }

  protected def pathExp(p: PathDef): Boolean =
    p match {
      case PathElm(_) => false
      case VarExp(_) => true
      case ReservedExp(_) => true
      case PathExp(_) => true
    }

  protected def queryExp(q: QueryDef): Boolean =
    q match {
      case ParamElm(_, _) => false
      case ParamVarExp(_, _) => true
      case ParamReservedExp(_, _) => true
      case ParamExp(_) => true
      case ParamContExp(_) => true
    }

  protected def containsExpansions(t: UriTemplate): Boolean =
    t match {
      case UriTemplate(_, _, Nil, Nil, Nil) => false
      case UriTemplate(_, _, Nil, Nil, f) => f.exists(fragmentExp)
      case UriTemplate(_, _, Nil, q, Nil) => q.exists(queryExp)
      case UriTemplate(_, _, Nil, q, f) => (q.exists(queryExp)) || (f.exists(fragmentExp))
      case UriTemplate(_, _, p, Nil, Nil) => p.exists(pathExp)
      case UriTemplate(_, _, p, Nil, f) => (p.exists(pathExp)) || (f.exists(fragmentExp))
      case UriTemplate(_, _, p, q, Nil) => (p.exists(pathExp)) || (q.exists(queryExp))
      case UriTemplate(_, _, p, q, f) =>
        (p.exists(pathExp)) || (q.exists(queryExp)) || (f.exists(fragmentExp))
    }

  protected def toUri(t: UriTemplate): Uri =
    t match {
      case UriTemplate(s, a, Nil, Nil, Nil) => Uri(s, a)
      case UriTemplate(s, a, Nil, Nil, f) => Uri(s, a, fragment = Some(renderFragmentIdentifier(f)))
      case UriTemplate(s, a, Nil, q, Nil) => Uri(s, a, query = buildQuery(q))
      case UriTemplate(s, a, Nil, q, f) =>
        Uri(s, a, query = buildQuery(q), fragment = Some(renderFragmentIdentifier(f)))
      case UriTemplate(s, a, p, Nil, Nil) => Uri(s, a, Uri.Path.unsafeFromString(renderPath(p)))
      case UriTemplate(s, a, p, q, Nil) =>
        Uri(s, a, Uri.Path.unsafeFromString(renderPath(p)), buildQuery(q))
      case UriTemplate(s, a, p, Nil, f) =>
        Uri(
          s,
          a,
          Uri.Path.unsafeFromString(renderPath(p)),
          fragment = Some(renderFragmentIdentifier(f)),
        )
      case UriTemplate(s, a, p, q, f) =>
        Uri(
          s,
          a,
          Uri.Path.unsafeFromString(renderPath(p)),
          buildQuery(q),
          Some(renderFragmentIdentifier(f)),
        )
    }

  sealed trait PathDef

  /** Static path element */
  final case class PathElm(value: String) extends PathDef

  sealed trait QueryDef

  sealed trait QueryExp extends QueryDef

  /** Static query parameter element */
  final case class ParamElm(name: String, values: List[String]) extends QueryDef
  object ParamElm {
    def apply(name: String): ParamElm = new ParamElm(name, Nil)
    def apply(name: String, values: String*): ParamElm = new ParamElm(name, values.toList)
  }

  /** Simple string expansion for query parameter
    */
  final case class ParamVarExp(name: String, variables: List[String]) extends QueryDef {
    require(variables.forall(isUnreserved), "all variables must consist of unreserved characters")
  }
  object ParamVarExp {
    def apply(name: String): ParamVarExp = new ParamVarExp(name, Nil)
    def apply(name: String, variables: String*): ParamVarExp =
      new ParamVarExp(name, variables.toList)
  }

  /** Reserved string expansion for query parameter
    */
  final case class ParamReservedExp(name: String, variables: List[String]) extends QueryDef {
    require(variables.forall(isUnreserved), "all variables must consist of unreserved characters")
  }
  object ParamReservedExp {
    def apply(name: String): ParamReservedExp = new ParamReservedExp(name, Nil)
    def apply(name: String, variables: String*): ParamReservedExp =
      new ParamReservedExp(name, variables.toList)
  }

  /** URI Templates are similar to a macro language with a fixed set of macro
    * definitions: the expression type determines the expansion process.
    *
    * The default expression type is simple string expansion (Level 1), wherein a
    * single named variable is replaced by its value as a string after
    * pct-encoding any characters not in the set of unreserved URI characters
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-1.5">Section 1.5</a>).
    *
    * Level 2 templates add the plus ("+") operator, for expansion of values that
    * are allowed to include reserved URI characters
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-1.5">Section 1.5</a>),
    * and the crosshatch ("#") operator for expansion of fragment identifiers.
    *
    * Level 3 templates allow multiple variables per expression, each
    * separated by a comma, and add more complex operators for dot-prefixed
    * labels, slash-prefixed path segments, semicolon-prefixed path
    * parameters, and the form-style construction of a query syntax
    * consisting of name=value pairs that are separated by an ampersand
    * character.
    */
  sealed trait ExpansionType

  sealed trait FragmentDef

  /** Static fragment element */
  final case class FragmentElm(value: String) extends FragmentDef

  /** Fragment expansion, crosshatch-prefixed
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.4">Section 3.2.4</a>)
    */
  final case class SimpleFragmentExp(name: String) extends FragmentDef {
    require(name.nonEmpty, "at least one character must be set")
    require(isUnreserved(name), "name must consist of unreserved characters")
  }

  /** Level 1 allows string expansion
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.2">Section 3.2.2</a>)
    *
    * Level 3 allows string expansion with multiple variables
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.2">Section 3.2.2</a>)
    */
  final case class VarExp(names: List[String]) extends PathDef {
    require(names.nonEmpty, "at least one name must be set")
    require(names.forall(isUnreserved), "all names must consist of unreserved characters")
  }
  object VarExp {
    def apply(names: String*): VarExp = new VarExp(names.toList)
  }

  /** Level 2 allows reserved string expansion
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.3">Section 3.2.3</a>)
    *
    * Level 3 allows reserved expansion with multiple variables
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.3">Section 3.2.3</a>)
    */
  final case class ReservedExp(names: List[String]) extends PathDef {
    require(names.nonEmpty, "at least one name must be set")
    require(names.forall(isUnreserved), "all names must consist of unreserved characters")
  }
  object ReservedExp {
    def apply(names: String*): ReservedExp = new ReservedExp(names.toList)
  }

  /** Fragment expansion with multiple variables, crosshatch-prefixed
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.4">Section 3.2.4</a>)
    */
  final case class MultiFragmentExp(names: List[String]) extends FragmentDef {
    require(names.nonEmpty, "at least one name must be set")
    require(names.forall(isUnreserved), "all names must consist of unreserved characters")
  }
  object MultiFragmentExp {
    def apply(names: String*): MultiFragmentExp = new MultiFragmentExp(names.toList)
  }

  /** Path segments, slash-prefixed
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.6">Section 3.2.6</a>)
    */
  final case class PathExp(names: List[String]) extends PathDef {
    require(names.nonEmpty, "at least one name must be set")
    require(names.forall(isUnreserved), "all names must consist of unreserved characters")
  }
  object PathExp {
    def apply(names: String*): PathExp = new PathExp(names.toList)
  }

  /** Form-style query, ampersand-separated
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.8">Section 3.2.8</a>)
    */
  final case class ParamExp(names: List[String]) extends QueryExp {
    require(names.nonEmpty, "at least one name must be set")
    require(
      names.forall(isUnreservedOrEncoded),
      "all names must consist of unreserved characters or be encoded",
    )
  }
  object ParamExp {
    def apply(names: String*): ParamExp = new ParamExp(names.toList)
  }

  /** Form-style query continuation
    * (<a href="https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.9">Section 3.2.9</a>)
    */
  final case class ParamContExp(names: List[String]) extends QueryExp {
    require(names.nonEmpty, "at least one name must be set")
    require(names.forall(isUnreserved), "all names must consist of unreserved characters")
  }
  object ParamContExp {
    def apply(names: String*): ParamContExp = new ParamContExp(names.toList)
  }
}
