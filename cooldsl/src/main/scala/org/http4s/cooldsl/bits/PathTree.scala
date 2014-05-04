package org.http4s.cooldsl
package bits

import scala.language.existentials

import shapeless.HList
import org.http4s.cooldsl._
import org.http4s.{Request}
import scalaz.{\/-, -\/, \/}
import scalaz.concurrent.Task
import scala.annotation.tailrec
import org.http4s.cooldsl.PathAnd
import org.http4s.cooldsl.PathOr
import org.http4s.Response
import scala.collection.mutable.ListBuffer

/**
 * Created by Bryce Anderson on 5/3/14.
 */
trait PathTree extends ValidationTree {

  protected sealed abstract class Node {

    protected def paths: List[Node]
    protected def end: Leaf
    protected def variadic: Leaf

    protected def clone(paths: List[Node], variadic: Leaf, end: Leaf): Node

    protected def addNode(n: Node): Node

    protected def replaceNode(o: Node, n: Node): Node

    protected def matchString(s: String, stack: HList): HList

    // Appends the action to the tree by walking the PathRule stack, returning a new Node structure
    final def append(tail: PathRule[_ <: HList], action: Leaf): Node = append(tail::Nil, action)

    final private def append(tail: List[PathRule[_ <: HList]], action: Leaf): Node = tail match {
      case h::tail => h match {
        case PathAnd(p1, p2) => append(p1::p2::tail, action)

        case PathOr(p1, p2) => append(p1::tail, action).append(p2::tail, action)

        case PathMatch(s: String, doc) =>
          paths.collectFirst { case n@MatchNode(s1,_,_,_,_) if s == s1 => n } match {
            case Some(n) => replaceNode(n, n.append(tail, action))
            case None    => addNode(MatchNode(s,doc).append(tail, action))
          }

        case PathCapture(p, doc) =>
          paths.collectFirst{ case n@ CaptureNode(p1,_,_,_,_) if p1 eq p => n } match {
            case Some(w) => replaceNode(w, w.append(tail, action))
            case None    => addNode(CaptureNode(p,doc).append(tail, action))
          }

        case CaptureTail(doc) =>
          val v = if (variadic != null) variadic ++ action else action
          clone(paths, v, end)

        case PathEmpty => append(tail, action)
      }

      case Nil =>  // this is the end of the stack
        val e = if (end != null) end ++ action else action
        clone(paths, variadic, e)
    }

    /** This function traverses the tree, matching paths in order of priority, provided they path the matches function:
      * 1: exact matches are given priority to wild cards node at a time
      *     This means /"foo"/wild has priority over /wild/"bar" for the route "/foo/bar"
      */
    final def walk(req: Request, path: List[String], stack: HList): String\/(() => Task[Response]) = {
      val h = matchString(path.head, stack)
      if (h != null) {
        if (path.tail.isEmpty) {
          if (end != null) end.attempt(req, h)
          else if (variadic != null) variadic.attempt(req, Nil::h)
          else null
        }
        else {
          @tailrec               // error may be null
          def go(nodes: List[Node], error: -\/[String]): String\/(()=>Task[Response]) = {
            if (nodes.isEmpty) error
            else nodes.head.walk(req, path.tail, h) match {
              case null => go(nodes.tail, error)
              case r@ \/-(_) => r
              case e@ -\/(_) => go(nodes.tail, if (error != null) error else e)
            }
          }

          val routeMatch = go(paths, null)
          if (routeMatch != null) routeMatch
          else if(variadic != null) variadic.attempt(req, path.tail::h)
          else null
        }

      }
      else null
    }

    // Searches the available nodes and replaces the current one
    protected def replace(o: Node, n: Node): List[Node] = {
      val b = new ListBuffer[Node]
      def go(l: List[Node]): List[Node] = l match {
        case h::tail if h eq o => b += n; b.prependToList(tail)
        case h::tail           => b += h; go(tail)
        case _                 => sys.error("Shouldn't get here!")
      }
      go(paths)
    }
  }

  final private case class CaptureNode(parser: StringParser[_],
                                       doc: Option[String],
                                       paths: List[Node] = Nil,
                                       variadic: Leaf = null,
                                       end: Leaf = null
                                       ) extends Node {

    override protected def replaceNode(o: Node, n: Node): CaptureNode = copy(paths = replace(o, n))

    override protected def addNode(n: Node): CaptureNode = n match {
      case n: CaptureNode => copy(paths = paths:+n)
      case n: MatchNode   => copy(paths = n::paths)
      case n: HeadNode    => sys.error("Shouldn't get here!")
    }

    override protected def clone(paths: List[Node], variadic: Leaf, end: Leaf): CaptureNode =
      copy(paths = paths, variadic = variadic, end = end)

    override protected def matchString(s: String, h: HList): HList = {
      parser.parse(s) match {
        case \/-(v) => v::h
        case _ => null
      }
    }
  }

  protected case class HeadNode(paths: List[Node] = Nil,
                                variadic: Leaf = null,
                                end: Leaf = null) extends Node {

    override protected def replaceNode(o: Node, n: Node): HeadNode = copy(paths = replace(o, n))

    override protected def addNode(n: Node): HeadNode = n match {
      case n: CaptureNode => copy(paths = paths:+n)
      case n: MatchNode   => copy(paths = n::paths)
      case n: HeadNode    => sys.error("Shouldn't get here!")
    }

    override protected def clone(paths: List[Node], variadic: Leaf, end: Leaf): HeadNode =
      copy(paths = paths, variadic = variadic, end = end)

    override protected def matchString(s: String, stack: HList): HList = {
      if (s.length == 0) stack
      else sys.error("Invalid start string")
    }
  }

  final private case class MatchNode(name: String, doc: Option[String],
                                     paths: List[Node] = Nil,
                                     variadic: Leaf = null,
                                     end: Leaf = null) extends Node {

    override protected def replaceNode(o: Node, n: Node): MatchNode = copy(paths = replace(o, n))

    override protected def addNode(n: Node): MatchNode = n match {
      case n: CaptureNode => copy(paths = paths:+n)
      case n: MatchNode   => copy(paths = n::paths)
      case n: HeadNode    => sys.error("Shouldn't get here!")
    }

    override protected def clone(paths: List[Node], variadic: Leaf, end: Leaf): MatchNode =
      copy(paths = paths, variadic = variadic, end = end)

    override protected def matchString(s: String, h: HList): HList = if (s == name) h else null
  }

}
