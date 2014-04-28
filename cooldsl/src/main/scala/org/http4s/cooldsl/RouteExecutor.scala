package org.http4s.cooldsl

import shapeless.{HNil, HList, ::}

import CoolApi._
import org.http4s.{Header, HeaderKey, Request, Response}
import scalaz.concurrent.Task
import scalaz.{-\/, \/-, \/}

import BodyCodec._

import org.http4s.Status.BadRequest

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

  def compile[T <: HList, F](r: Runnable[T], f: F, hf: HListToFunc[T, Task[Response], F]): Goal = {

    val ff: Goal = { req =>
       pathAndValidate(req, r).map(_ match {
           case \/-(stack) => hf.conv(f)(stack)
           case -\/(s) => onBadRequest(s)
       })
    }

    ff
  }
  
  def compileWithBody[T <: HList, F, R](r: CodecRunnable[T, R], f: F, hf: HListToFunc[R::T, Task[Response], F]): Goal = {
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

  private def pathAndValidate[T <: HList](req: Request, r: Runnable[T]): Option[\/[String, T]] = {
    val p = parsePath(req.requestUri.path)
    runStatus(r.p, p).map(h => runValidation(req, r.validators, h)).asInstanceOf[Option[\/[String, T]]]
  }

  /** Attempts to find a compatible codec */
  private def pickDecoder[T](req: Request, d: BodyTransformer[T]): Option[Dec[T]] = d match {
    case Decoder(codec) =>
      if (codec.checkHeaders(req.headers)) Some(codec)
      else None

    case OrDec(c1, c2) => pickDecoder(req, c1).orElse(pickDecoder(req, c2))
  }

  /** Runs the URL and pushes values to the HList stack */
  private def runStatus[T1<: HList](v: PathValidator[T1], path: List[String]): Option[T1] = {
    def go(v: PathValidator[_ <: HList], path: List[String], stack: HList): Option[(List[String],HList)] = v match {

      case PathAnd(a, b) => go(a, path, stack).flatMap {
        case (Nil, _)     => None
        case (lst, stack) => go(b, lst, stack)
      }

      case PathOr(a, b) => go(a, path, stack).orElse(go(b, path, stack))

      case PathCapture(f) => f(path.head).map{ i => (path.tail, i::stack)}

      case PathMatch(s) =>
        if (path.head == s) Some((path.tail, stack))
        else None

      case PathEmpty => // Needs to be the empty path
        if (path.head.length == 0) Some(path.tail, stack)
        else None
    }

    if (!path.isEmpty) go(v, path, HNil).flatMap {
      case (Nil, stack) => Some(stack.asInstanceOf[T1])
      case _ => None
    }
    else None
  }

  /** Walks the validation tree
    * @param v Validator tree
    * @param req [[Request]]
    * @tparam T1 HList representation of the result of the validator tree
    * @return \/-[T1] if successful, -\/(reason string) otherwise
    */
  private[cooldsl] def ensureValidHeaders[T1 <: HList](v: Validator[T1], req: Request): \/[String,T1] =
    runValidation(req, v, HNil).asInstanceOf[\/[String,T1]]

  /** The untyped guts of ensureValidHeaders and friends */
  private[this] def runValidation(req: Request, v: Validator[_ <: HList], stack: HList): \/[String,HList] = v match {
    case And(a, b) => runValidation(req, b, stack).flatMap(runValidation(req, a, _))

    case Or(a, b) => runValidation(req, a, stack).orElse(runValidation(req, b, stack))

    case HeaderCapture(key) => req.headers.get(key) match {
      case Some(h) => \/-(h::stack)
      case None => -\/(missingHeader(key))
    }

    case HeaderValidator(key, f) => req.headers.get(key) match {
      case Some(h) => if (f(h)) \/-(stack) else -\/(invalidHeader(h))
      case None => -\/(missingHeader(key))
    }

    case HeaderMapper(key, f) => req.headers.get(key) match {
      case Some(h) => \/-(f(h)::stack)
      case None => -\/(missingHeader(key))
    }

    case QueryMapper(name, parser) =>
      req.requestUri.params.get(name) match {
        case Some(v) => parser.parse(v).map(_::stack)
        case None => -\/(missingQuery(name))
      }

    case EmptyValidator => \/-(stack)
  }

}
