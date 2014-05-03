package org.http4s
package cooldsl

import scalaz.concurrent.Task
import shapeless.{HList}
import com.typesafe.scalalogging.Logging

import scala.language.existentials
import scala.annotation.tailrec

/**
* Created by Bryce Anderson on 4/30/14.
*/
trait CoolService extends HttpService with Logging {

  private sealed trait Node {
    def matchString(s: String): Boolean
    protected var end: Goal
    protected var variadic: Goal
    protected var paths: List[MatchNode]
    protected var wilds: WildNode

    // Append the action to the tree by walking the PathRule stack
    final def append(tail: List[PathRule[_ <: HList]], action: Goal): Unit = tail match {
      case h::tail => h match {
        case PathAnd(p1, p2) => append(p1::p2::tail, action)
        case PathOr(p1, p2) => append(p1::tail, action); append(p2::tail, action)

        case PathMatch(s: String) =>
          paths.find(_.name == s).getOrElse {
            val n = new MatchNode(s)
            paths = n::paths
            n
          }.append(tail, action)

        case PathCapture(_) =>
          if (wilds == null) wilds = WildNode()
          wilds.append(tail, action)

        case CaptureTail() =>
          if (variadic != null) logger.warn("Redefining variadic path!")
          variadic = action

        case PathEmpty => append(tail, action)
      }

      case Nil =>  // this is the end of the stack
        if (end != null) logger.warn("Redefining route!")
        end = action
    }

    /** This function traverses the tree, matching paths in order of priority, provided they path the matches function:
      * 1: exact matches are given priority to wild cards node at a time
      *     This means /"foo"/wild has priority over /wild/"bar" for the route "/foo/bar"
    */
    final def walk(path: List[String], matches: Goal => Boolean): Goal = {
      if (path.isEmpty) {
        if (end != null && matches(end)) end
        else if (variadic != null && matches(variadic)) variadic
        else null
      }
      else if (matchString(path.head)) {
        @tailrec
        def go(nodes: List[Node]): Goal = {
          if (nodes.isEmpty) null
          else {
            val n = nodes.head.walk(path.tail, matches)
            if (n != null) n else go(nodes.tail)
          }
        }

        var routeMatch = go(paths)
        if (routeMatch != null) routeMatch
        else {
          if (wilds != null) routeMatch = wilds.walk(path.tail, matches)
          if (routeMatch == null && variadic != null && matches(variadic)) variadic
          else routeMatch
        }
      }
      else null
    }
  }

  final private case class WildNode(protected var paths: List[MatchNode] = Nil,
                              protected var wilds: WildNode = null,
                              protected var end: Goal = null,
                              protected var variadic: Goal = null) extends Node {
    override def matchString(s: String): Boolean = true
  }

  final private case class MatchNode(name: String,
                               protected var paths: List[MatchNode] = Nil,
                               protected var wilds: WildNode = null,
                               protected var end: Goal = null,
                               protected var variadic: Goal = null) extends Node {
    override def matchString(s: String): Boolean = s == name
  }

  private val methods = Method.methods.map((_, WildNode())).toMap

  private def getMethod(method: Method) = methods.get(method)
                                          .getOrElse(sys.error("Somehow an unknown Method type was found!"))

  def append(action: CoolAction[_, _]): Unit = {
    val (method,path,vals) = action.router match {
      case Router(method, path, vals) => (method, path, vals)
      case CodecRouter(Router(method, path, vals), _) => (method, path, vals)
    }

    val head = getMethod(method)
    head.append(path::Nil, null)
  }

  private def getResult(req: Request): Option[Task[Response]] = {
    val path = req.requestUri.path.split("/").toList
    var result: Task[Response] = null
    getMethod(req.requestMethod).walk(path, { g =>
      g.apply(req) match {
        case Some(t) => result = t; true
        case None => false
      }
    })
    Option(result)
  }

  override def isDefinedAt(x: Request): Boolean = getResult(x).isDefined

  override def apply(v1: Request): Task[Response] = getResult(v1).getOrElse{throw new MatchError("Route not defined")}

  override def applyOrElse[A1 <: Request, B1 >: Task[Response]](x: A1, default: (A1) => B1): B1 = {
    getResult(x).getOrElse(default(x))
  }
}

class CoolAction[T <: HList, F](private[cooldsl] val router: RouteExecutable[T],
                                private[cooldsl] val f: F,
                                private[cooldsl] val hf: HListToFunc[T, Task[Response], F])
