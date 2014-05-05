package org.http4s
package cooldsl

import shapeless.{HNil, HList, ::}

import scalaz.concurrent.Task
import scalaz.{-\/, \/-, \/}

import BodyCodec._

import org.http4s.Status.BadRequest
import org.http4s.cooldsl.bits.HListToFunc

/**
 * Created by Bryce Anderson on 4/27/14.
 */


trait ExecutableCompiler {
  def missingHeader(key: HeaderKey): String = s"Missing header: ${key.name}"

  def missingQuery(key: String): String = s"Missing query param: $key"

  def invalidHeader(h: Header): String = s"Invalid header: $h"

  def onBadRequest(reason: String): Task[Response] = BadRequest(reason)

  def parsePath(path: String): List[String] = path.split("/").toList

  //////////////////////// Stuff for executing the route //////////////////////////////////////

  /** The untyped guts of ensureValidHeaders and friends */
  protected def runValidation(req: Request, v: HeaderRule[_ <: HList], stack: HList): \/[String,HList] = v match {
    case And(a, b) => runValidation(req, a, stack).flatMap(runValidation(req, b, _))

    case Or(a, b) => runValidation(req, a, stack).orElse(runValidation(req, b, stack))

    case HeaderCapture(key) => req.headers.get(key) match {
      case Some(h) => \/-(h::stack)
      case None => -\/(missingHeader(key))
    }

    case HeaderRequire(key, f) => req.headers.get(key) match {
      case Some(h) => if (f(h)) \/-(stack) else -\/(invalidHeader(h))
      case None => -\/(missingHeader(key))
    }

    case HeaderMapper(key, f) => req.headers.get(key) match {
      case Some(h) => \/-(f(h)::stack)
      case None => -\/(missingHeader(key))
    }

    case QueryRule(name, parser) => parser.collect(name, req).map(_::stack)

    case EmptyHeaderRule => \/-(stack)
  }

  /** Runs the URL and pushes values to the HList stack */
  protected def runPath[T1<: HList](req: Request, v: PathRule[T1], path: List[String]): Option[\/[String,T1]] = {

    // setup a stack for the path
    var currentPath = path
    def pop = {
      val head = currentPath.head
      currentPath = currentPath.tail
      head
    }

    // WARNING: returns null if not matched but no nulls should escape the runPath method
    def go(v: PathRule[_ <: HList], stack: HList): \/[String,HList] = v match {
      case PathAnd(a, b) =>
        val v = go(a, stack)
        if (v == null) null
        else if (!currentPath.isEmpty    ||
          b.isInstanceOf[PathAnd[_]]     ||
          b.isInstanceOf[CaptureTail]) v.flatMap(go(b, _))
        else null

      case PathOr(a, b) =>
        val oldPath = currentPath
        val v = go(a, stack)
        if (v != null) v
        else {
          currentPath = oldPath // reset the path stack
          go(b, stack)
        }

      case PathCapture(f, _) => f.parse(pop).map{ i => i::stack}

      case PathMatch(s, _) =>
        if (pop == s) \/-(stack)
        else null

      case PathEmpty => // Needs to be the empty path
        if (currentPath.head.length == 0) {
          pop
          \/-(stack)
        }
        else null

      case CaptureTail(_) =>
        val p = currentPath
        currentPath = Nil
        \/-(p::stack)
    }

    if (!path.isEmpty) {
      val r = go(v, HNil)
      if (currentPath.isEmpty) r match {
        case null => None
        case r@ \/-(_) => Some(r.asInstanceOf[\/[String,T1]])
        case r@ -\/(_) => Some(r)
      } else None
    }
    else None
  }

}

object RouteExecutor extends RouteExecutor

private[cooldsl] trait RouteExecutor extends ExecutableCompiler with CompileService[Request=>Option[Task[Response]]] {
  
  private type Result = Request => Option[Task[Response]]

  ///////////////////// Route execution bits //////////////////////////////////////

  override def compile[T <: HList, F, O](action: CoolAction[T, F, O]): Result = action match {
    case CoolAction(r@ Router(_,_,_), f, hf) => compileRouter(r, f, hf)
    case CoolAction(r@ CodecRouter(_,_), f, hf) => compileCodecRouter(r, f, hf)
  }

  protected def compileRouter[T <: HList, F, O](r: Router[T], f: F, hf: HListToFunc[T, O, F]): Result = {
    val readyf = hf.conv(f)
    val ff: Result = { req =>
       pathAndValidate[T](req, r.path, r.validators).map(_ match {
           case \/-(stack) => readyf(req,stack)
           case -\/(s) => onBadRequest(s)
       })
    }

    ff
  }
  
  protected def compileCodecRouter[T <: HList, F, O, R](r: CodecRouter[T, R], f: F, hf: HListToFunc[R::T, O, F]): Result = {
    val actionf = hf.conv(f)
    val allvals = And(r.router.validators, r.decoder.validations)
    val ff: Result = { req =>
      pathAndValidate[T](req, r.router.path, allvals).map(_ match {
        case \/-(stack) => r.decoder.decode(req).flatMap(_ match {
            case \/-(r) => actionf(req,r::stack)
            case -\/(e) => onBadRequest(s"Error decoding body: $e")
          })
        case -\/(s) => onBadRequest(s)
      })
    }

    ff
  }

  private def pathAndValidate[T <: HList](req: Request, path: PathRule[_ <: HList], v: HeaderRule[_ <: HList]): Option[\/[String, T]] = {
    val p = parsePath(req.requestUri.path)
    runPath(req, path, p).map(_.flatMap(runValidation(req, v, _))).asInstanceOf[Option[\/[String, T]]]
  }

  /** Walks the validation tree */
  def ensureValidHeaders[T1 <: HList](v: HeaderRule[T1], req: Request): \/[String,T1] =
    runValidation(req, v, HNil).asInstanceOf[\/[String,T1]]
}
