package org.http4s
package cooldsl

import scalaz.concurrent.Task
import shapeless.{HNil, HList}
import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.language.existentials
import scala.annotation.tailrec
import scalaz.{-\/, \/-, \/}

/**
* Created by Bryce Anderson on 4/30/14.
*/
trait CoolService extends HttpService with LazyLogging with ExecutableCompiler {

  type Goal = (Request, HList) => String\/(() =>Task[Response])

  private sealed abstract class Node {

    override def toString: String = {
      s"${this.getClass.getName}($paths" +
        (if (end != null) ", Matched" else "") +
        (if (variadic != null) "variadic)" else ")")
    }

    def matchString(s: String, stack: HList): HList
    final var end: Goal = null
    final var variadic: Goal = null
    final var paths: List[Node] = Nil

    // Append the action to the tree by walking the PathRule stack
    final def append(tail: List[PathRule[_ <: HList]], action: Goal): Unit = tail match {
      case h::tail => h match {
        case PathAnd(p1, p2) => append(p1::p2::tail, action)
        case PathOr(p1, p2) => append(p1::tail, action); append(p2::tail, action)

        case PathMatch(s: String) =>
          paths.collectFirst{ case n@ MatchNode(s1) if s == s1 => n }.getOrElse {
            val n = new MatchNode(s)
            paths = n::paths
            n
          }.append(tail, action)

        case PathCapture(p) =>
          paths.collectFirst{ case n@ CaptureNode(p1) if p1 eq p => n } match {
            case Some(w) => w.append(tail, action)
            case None =>
              val w = CaptureNode(p)
              paths = paths:+w      // append captures to the rear
              w.append(tail, action)
          }

        case CaptureTail() =>
          variadic = if (variadic != null) fuseGoals(variadic, action) else action

        case PathEmpty => append(tail, action)
      }

      case Nil =>  // this is the end of the stack
        end = if (end != null) fuseGoals(end, action) else action
    }

    private def fuseGoals(g1: Goal, g2: Goal): Goal = (req, stack) => {
      g1(req, stack) match {
        case r@ \/-(_) => r
        case e@ -\/(_) => g2(req, stack).orElse(e)
      }
    }

    /** This function traverses the tree, matching paths in order of priority, provided they path the matches function:
      * 1: exact matches are given priority to wild cards node at a time
      *     This means /"foo"/wild has priority over /wild/"bar" for the route "/foo/bar"
    */
    final def walk(path: List[String], req: Request, stack: HList): String\/(() => Task[Response]) = {
      val h = matchString(path.head, stack)
      if (h != null) {
        if (path.tail.isEmpty) {
          if (end != null) end(req, h)
          else if (variadic != null) variadic(req, h)
          else null
        }
        else {
          @tailrec    // error may be null
          def go(nodes: List[Node], error: -\/[String]): String\/(()=>Task[Response]) = {
            if (nodes.isEmpty) error
            else nodes.head.walk(path.tail, req, h) match {
              case null => go(nodes.tail, error)
              case r@ \/-(_) => r
              case e@ -\/(_) => go(nodes.tail, if (error != null) error else e)
            }
          }

          val routeMatch = go(paths, null)
          if (routeMatch != null) routeMatch
          else if(variadic != null) variadic(req, h)
          else null
        }

      }
      else null
    }
  }

  override def toString(): String = s"CoolService($methods)"

  final private case class CaptureNode(parser: StringParser[_]) extends Node {
    override def matchString(s: String, h: HList): HList = {
      parser.parse(s) match {
        case \/-(v) => v::h
        case _ => null
      }
    }
  }

  final private class HeadNode extends Node {
    override def matchString(s: String, stack: HList): HList = stack
  }

  final private case class MatchNode(name: String) extends Node {
    override def matchString(s: String, h: HList): HList = if (s == name) h else null
  }

  private val methods = Method.methods.map((_, new HeadNode)).toMap

  private def getMethod(method: Method) = methods.get(method).getOrElse(sys.error("Somehow an unknown Method type was found!"))

  def append[T <: HList, F](action: CoolAction[T, F]): Unit = {
    action.router match {
      case Router(method, path, vals) =>
        getMethod(method).append(path::Nil, {(req, pathstack) =>
          runValidation(req, vals, pathstack).map{ pathstack => () =>
            action.hf.conv(action.f)(pathstack.asInstanceOf[T])
          }
        })

      case CodecRouter(Router(method, path, vals), parser) =>
        getMethod(method).append(path::Nil, { (req, pathstack) =>
          runValidation(req, vals, pathstack).map{ pathstack => () =>
            parser.decode(req) match {
              case Some(t) => t.flatMap { r =>
                  action.hf.conv(action.f)((r :: pathstack).asInstanceOf[T])
                }

              case None => onBadRequest("No acceptable decoder")
            }
          }
        })
    }
  }

  private def getResult(req: Request): Option[()=>Task[Response]] = {
    val path = req.requestUri.path.split("/").toList
    getMethod(req.requestMethod).walk(path, req, HNil) match {
      case null => None
      case \/-(t) => Some(t)
      case -\/(s) => Some(()=>onBadRequest(s))
    }
  }

  override def isDefinedAt(x: Request): Boolean = getResult(x).isDefined

  override def apply(v1: Request): Task[Response] = getResult(v1)
                                        .map(_.apply())
                                        .getOrElse{throw new MatchError("Route not defined")}

  override def applyOrElse[A1 <: Request, B1 >: Task[Response]](x: A1, default: (A1) => B1): B1 = {
    getResult(x) match {
      case Some(f) => f()
      case None => default(x)
    }
  }
}

class CoolAction[T <: HList, F](private[cooldsl] val router: RouteExecutable[T],
                                private[cooldsl] val f: F,
                                private[cooldsl] val hf: HListToFunc[T, Task[Response], F])
