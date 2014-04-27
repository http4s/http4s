package org.http4s.cooldsl

import shapeless.{HNil, HList}

import CoolApi._
import org.http4s.{Header, HeaderKey, Request, Response}
import scalaz.concurrent.Task
import scalaz.{-\/, \/-, \/}
import shapeless.ops.hlist.Prepend

import org.http4s.Status.BadRequest

/**
 * Created by Bryce Anderson on 4/27/14.
 */

object RouteExecutor extends RouteExecutor

trait RouteExecutor {

  def missingHeader(key: HeaderKey): String = s"Missing header: ${key.name}"

  def invalidHeader(h: Header): String = s"Invalid header: $h"

  def onBadRequest(reason: String): Task[Response] = BadRequest(reason)


  ///////////////////// Route execution bits //////////////////////////////////////

  def compile[T <: HList, F](r: Runnable[T], f: F, conv: HListToFunc[T, Task[Response], F]): Goal = {



    ???
  }

  private def compileStatus[T1<: HList](v: PathValidator[T1]): Option[T1] = {
    def go(v: PathValidator[_ <: HList]): Option[HList] = v match {
      case PathAnd(a, b) => ???
      case PathOr(a, b) => ???

      case PathCapture(f) => ???

      case PathMatch(s) => ???

    }

    ???
  }

  /** Walks the validation tree
    * @param v Validator tree
    * @param req [[Request]]
    * @tparam T1 HList representation of the result of the validator tree
    * @return \/-[T1] if successful, -\/(reason string) otherwise
    */
  private[cooldsl] def ensureValidHeaders[T1 <: HList](v: Validator[T1])(req: Request): String\/T1 = {
    def go(v: Validator[_ <: HList], stack: HList): String\/HList = v match {
      case And(a, b) => go(a, stack).flatMap(go(b, _))

      case Or(a, b) => go(a, stack).orElse(go(b, stack))

      case HeaderCapture(key) => req.headers.get(key) match {
        case Some(h) => \/-(h :: stack)
        case None => -\/(missingHeader(key))
      }

      case HeaderValidator(key, f) => req.headers.get(key) match {
        case Some(h) => if (f(h)) \/-(stack) else -\/(invalidHeader(h))
        case None => -\/(missingHeader(key))
      }

      case HeaderMapper(key, f) => req.headers.get(key) match {
        case Some(h) => \/-(f(h)::HNil)
        case None => -\/(missingHeader(key))
      }

      case EmptyValidator => \/-(stack)
    }

    go(v, HNil).asInstanceOf[\/[String,T1]]
  }

}
