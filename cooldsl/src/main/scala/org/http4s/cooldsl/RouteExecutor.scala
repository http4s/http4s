package org.http4s.cooldsl

import shapeless.{HNil, HList, ::}

import org.http4s.{Header, HeaderKey, Request, Response}
import scalaz.concurrent.Task
import scalaz.{-\/, \/-, \/}

import BodyCodec._

import org.http4s.Status.BadRequest
import scala.annotation.unchecked.uncheckedVariance

/**
 * Created by Bryce Anderson on 4/27/14.
 */

object RouteExecutor extends RouteExecutor

trait RouteExecutor {

  type Goal = Request => Option[Task[Response]]

  def missingHeader(key: HeaderKey): String = s"Missing header: ${key.name}"

  def missingQuery(key: String): String = s"Missing query param: $key"

  def invalidHeader(h: Header): String = s"Invalid header: $h"

  def onBadRequest(reason: String): Task[Response] = BadRequest(reason)

  def parsePath(path: String): List[String] = path.split("/").toList


  ///////////////////// Route execution bits //////////////////////////////////////

  def compile[T <: HList, F](r: Runnable[T, _ <: HList], f: F, hf: HListToFunc[T, Task[Response], F]): Goal = {

    val ff: Goal = { req =>
       pathAndValidate(req, r).map(_ match {
           case \/-(stack) => hf.conv(f)(stack)
           case -\/(s) => onBadRequest(s)
       })
    }

    ff
  }
  
  def compileWithBody[T <: HList, F, R](r: CodecRunnable[T,_ <: HList, R], f: F, hf: HListToFunc[R::T, Task[Response], F]): Goal = {
    val ff: Goal = { req =>
      pathAndValidate(req, r.r).map(_ match {
        case \/-(stack) =>
          pickDecoder(req, r.t)
            .map(_.decode(req.body).flatMap { r =>
              hf.conv(f)(r :: stack)
            }).getOrElse(onBadRequest("No valid decoder"))

        case -\/(s) => onBadRequest(s)
      })
    }

    ff
  }

  private def pathAndValidate[T <: HList](req: Request, r: Runnable[T, _ <: HList]): Option[\/[String, T]] = {
    val p = parsePath(req.requestUri.path)
    runStatus(req, r.p, p).map(_.flatMap(runValidation(req, r.validators, _))).asInstanceOf[Option[\/[String, T]]]
  }

  /** Attempts to find a compatible codec */
  private def pickDecoder[T](req: Request, d: BodyTransformer[T]): Option[Dec[T]] = d match {
    case Decoder(codec) =>
      if (codec.checkHeaders(req.headers)) Some(codec)
      else None

    case OrDec(c1, c2) => pickDecoder(req, c1).orElse(pickDecoder(req, c2))
  }

  /** Runs the URL and pushes values to the HList stack */
  private def runStatus[T1<: HList](req: Request, v: PathRule[T1], path: List[String]): Option[\/[String,T1]] = {

    // setup a stack for the path
    var currentPath = path
    def pop = {
      val head = currentPath.head
      currentPath = currentPath.tail
      head
    }

    // WARNING: returns null if not matched
    def go(v: PathRule[_ <: HList], stack: HList): \/[String,HList] = v match {
      case PathAnd(a, b) =>
        val v = go(a, stack)
        if (v == null) null
        else if (!currentPath.isEmpty     ||
           b.isInstanceOf[PathAnd[_]]     ||
           b.isInstanceOf[QueryMapper[_]] ||
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

      case PathCapture(f) => f.parse(pop).map{ i => i::stack}

      case PathMatch(s) =>
        if (pop == s) \/-(stack)
        else null

      case QueryMapper(name, parser) =>
        if (currentPath.isEmpty) req.requestUri.params.get(name) match {
          case Some(v) => parser.parse(v).map(_::stack)
          case None => -\/(s"Missing query param: $name")
        } else null

      case PathEmpty => // Needs to be the empty path
        if (currentPath.head.length == 0) {
          pop
          \/-(stack)
        }
        else null

      case CaptureTail() =>
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

  /** Walks the validation tree */
  private[cooldsl] def ensureValidHeaders[T1 <: HList](v: HeaderRule[T1], req: Request): \/[String,T1] =
    runValidation(req, v, HNil).asInstanceOf[\/[String,T1]]

  /** The untyped guts of ensureValidHeaders and friends */
  private[this] def runValidation(req: Request, v: HeaderRule[_ <: HList], stack: HList): \/[String,HList] = v match {
    case And(a, b) => runValidation(req, b, stack).flatMap(runValidation(req, a, _))

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

    case EmptyHeaderRule => \/-(stack)
  }

}
