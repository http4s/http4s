package org.http4s
package cooldsl

import scalaz.concurrent.Task
import shapeless.{HNil, HList}
import scalaz.{-\/, \/-, \/}
import org.http4s.cooldsl.bits.HListToFunc

/**
* Created by Bryce Anderson on 4/30/14.
*/
trait CoolService extends HttpService with ExecutableCompiler with bits.PathTree {

  private type Goal = (Request, HList) => String\/(() =>Task[Response])

  private var methods: Map[Method, Node] = Method.methods.map((_, HeadNode())).toMap

  private def missingMethod = sys.error("Somehow an unknown Method type was found!")

  private def getMethod(method: Method) = methods.get(method).getOrElse(missingMethod)

  def append[T <: HList, F, O](action: CoolAction[T, F, O]): Unit = {
    val newLeaf = makeLeaf(action)
    val newNode = methods.get(action.router.method).getOrElse(missingMethod)
                      .append(action.router.path, newLeaf)

    methods = methods.updated(action.router.method, newNode)
  }

  private def getResult(req: Request): Option[()=>Task[Response]] = {
    val path = req.requestUri.path.split("/").toList
    getMethod(req.requestMethod).walk(req, path, HNil) match {
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

  override def toString(): String = s"CoolService($methods)"
}

class CoolAction[T <: HList, F, O](private[cooldsl] val router: RouteExecutable[T],
                                private[cooldsl] val f: F,
                                private[cooldsl] val hf: HListToFunc[T, O, F])
