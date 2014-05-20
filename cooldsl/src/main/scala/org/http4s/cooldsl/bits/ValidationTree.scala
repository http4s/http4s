package org.http4s.cooldsl
package bits

import scala.language.existentials

import shapeless.HList
import org.http4s.cooldsl.HeaderRule
import org.http4s.{Response, Request}
import scalaz.concurrent.Task
import scalaz.{\/-, -\/, \/}
import scala.annotation.tailrec

/**
 * Created by Bryce Anderson on 5/3/14.
 */

private[bits] object TempTools extends ExecutableCompiler {
  override def runValidation(req: Request, v: HeaderRule[_ <: HList], stack: HList): \/[String, HList] =
    super.runValidation(req, v, stack)
}

private[cooldsl] trait ValidationTree {

  private type Result = String\/(()=>Task[Response])

  protected trait Leaf {
    def attempt(req: Request, stack: HList): Result
    def document: String = ???  // TODO: How will docs work?

    final def ++(l: Leaf): Leaf = (this, l) match {
      case (s1@ SingleLeaf(_,_,_), s2@ SingleLeaf(_,_,_)) => ListLeaf(s1::s2::Nil)
      case (s1@ SingleLeaf(_,_,_), ListLeaf(l))           => ListLeaf(s1::l)
      case (ListLeaf(l), s2@ SingleLeaf(_,_,_))           => ListLeaf(l:+s2)
      case (ListLeaf(l1), ListLeaf(l2))                   => ListLeaf(l1:::l2)
    }
  }

  final private case class SingleLeaf(vals: HeaderRule[_ <: HList],       // TODO: For documentation purposes
                                      codec: Option[Decoder[_]],  // For documentation purposes
                                      f: (Request, HList)=>Result) extends Leaf {
    override def attempt(req: Request, stack: HList): Result = f(req,stack)
  }


  final private case class ListLeaf(leaves: List[SingleLeaf]) extends Leaf {
    override def attempt(req: Request, stack: HList): Result = {
      @tailrec
      def go(l: List[SingleLeaf], error: -\/[String]): Result = if (!l.isEmpty) {
        l.head.attempt(req, stack) match {
          case e@ -\/(_) => go(l.tail, if (error != null) error else e)
          case r@ \/-(_) => r
        }
      } else error
      go(leaves, null)
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  protected def makeLeaf[T <: HList, F, O](action: CoolAction[T, F, O]): Leaf = {
    action.router match {
      case Router(method, _, vals) =>
        SingleLeaf(vals, None, (req, pathstack) =>
            TempTools.runValidation(req, vals, pathstack).map { pathstack => () =>
              action.hf.conv(action.f)(req,pathstack.asInstanceOf[T])
            })

      case c@ CodecRouter(_, parser) =>
        val actionf = action.hf.conv(action.f)
        SingleLeaf(c.validators, Some(parser), {
          (req, pathstack) =>
            TempTools.runValidation(req, c.validators, pathstack).map { pathstack => () =>
              parser.decode(req).flatMap {
                case \/-(r) => actionf(req, (r::pathstack).asInstanceOf[T])
                case -\/(e) => TempTools.onBadRequest(s"Decoding error: $e")
              }
            }
        })
    }
  }

}
