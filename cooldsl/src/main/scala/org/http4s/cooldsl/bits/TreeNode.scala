//package org.http4s.cooldsl
//package bits
//
//import shapeless.HList
//import scala.collection.mutable.ListBuffer
//import scalaz.\/-
//
//
///**
// * Created by Bryce Anderson on 5/9/14.
// */
//
//trait TreeNode  {
//
//  protected type NodeResult >: Null <: AnyRef
//
//  protected def concat(l: NodeResult, r: NodeResult): NodeResult
//
//  protected sealed abstract class Node {
//
//    protected def paths: List[Node]
//
//    protected def end: NodeResult
//
//    protected def variadic: NodeResult
//
//    protected def clone(paths: List[Node], variadic: NodeResult, end: NodeResult): Node
//
//    protected def addNode(n: Node): Node
//
//    protected def replaceNode(o: Node, n: Node): Node
//
//    protected def matchString(s: String, stack: HList): HList
//
//    // Appends the action to the tree by walking the PathRule stack, returning a new Node structure
//    final def append(tail: PathRule[_ <: HList], action: NodeResult): Node = append(tail :: Nil, action)
//
//    final private[Node] def append(tail: List[PathRule[_ <: HList]], action: NodeResult): Node = tail match {
//      case h :: tail => h match {
//        case PathAnd(p1, p2) => append(p1 :: p2 :: tail, action)
//
//        case PathOr(p1, p2) => append(p1 :: tail, action).append(p2 :: tail, action)
//
//        case PathMatch(s: String, doc) =>
//          paths.collectFirst {
//            case n@MatchNode(s1, _, _, _, _) if s == s1 => n
//          } match {
//            case Some(n) => replaceNode(n, n.append(tail, action))
//            case None => addNode(MatchNode(s, doc).append(tail, action))
//          }
//
//        case PathCapture(p, doc) =>
//          paths.collectFirst {
//            case n@CaptureNode(p1, _, _, _, _) if p1 eq p => n
//          } match {
//            case Some(w) => replaceNode(w, w.append(tail, action))
//            case None => addNode(CaptureNode(p, doc).append(tail, action))
//          }
//
//        case CaptureTail(doc) =>
//          val v = if (variadic != null) concat(variadic, action) else action
//          clone(paths, v, end)
//
//        case PathEmpty => append(tail, action)
//
//        case _: MetaData => append(tail, action)
//      }
//
//      case Nil => // this is the end of the stack
//        val e = if (end != null) concat(end, action) else action
//        clone(paths, variadic, e)
//    }
//
//    // Searches the available nodes and replaces the current one
//    protected def replace(o: Node, n: Node): List[Node] = {
//      val b = new ListBuffer[Node]
//      def go(l: List[Node]): List[Node] = l match {
//        case h :: tail if h eq o => b += n; b.prependToList(tail)
//        case h :: tail => b += h; go(tail)
//        case _ => sys.error("Shouldn't get here!")
//      }
//      go(paths)
//    }
//  }
//
//  final private case class CaptureNode(parser: StringParser[_],
//                                       doc: Option[String],
//                                       paths: List[Node] = Nil,
//                                       variadic: NodeResult = null,
//                                       end: NodeResult = null
//                                        ) extends Node {
//
//    override protected def replaceNode(o: Node, n: Node): CaptureNode = copy(paths = replace(o, n))
//
//    override protected def addNode(n: Node): CaptureNode = n match {
//      case n: CaptureNode => copy(paths = paths:+n)
//      case n: MatchNode   => copy(paths = n::paths)
//      case n: HeadNode    => sys.error("Shouldn't get here!")
//    }
//
//    override protected def clone(paths: List[Node], variadic: NodeResult, end: NodeResult): CaptureNode =
//      copy(paths = paths, variadic = variadic, end = end)
//
//    override protected def matchString(s: String, h: HList): HList = {
//      parser.parse(s) match {
//        case \/-(v) => v::h
//        case _ => null
//      }
//    }
//  }
//
//  protected case class HeadNode(paths: List[Node] = Nil,
//                                variadic: NodeResult = null,
//                                end: NodeResult = null) extends Node {
//
//    override protected def replaceNode(o: Node, n: Node): HeadNode = copy(paths = replace(o, n))
//
//    override protected def addNode(n: Node): HeadNode = n match {
//      case n: CaptureNode => copy(paths = paths:+n)
//      case n: MatchNode   => copy(paths = n::paths)
//      case n: HeadNode    => sys.error("Shouldn't get here!")
//    }
//
//    override protected def clone(paths: List[Node], variadic: NodeResult, end: NodeResult): HeadNode =
//      copy(paths = paths, variadic = variadic, end = end)
//
//    override protected def matchString(s: String, stack: HList): HList = {
//      if (s.length == 0) stack
//      else sys.error("Invalid start string")
//    }
//  }
//
//  final private case class MatchNode(name: String, doc: Option[String],
//                                     paths: List[Node] = Nil,
//                                     variadic: NodeResult = null,
//                                     end: NodeResult = null) extends Node {
//
//    override protected def replaceNode(o: Node, n: Node): MatchNode = copy(paths = replace(o, n))
//
//    override protected def addNode(n: Node): MatchNode = n match {
//      case n: CaptureNode => copy(paths = paths:+n)
//      case n: MatchNode   => copy(paths = n::paths)
//      case n: HeadNode    => sys.error("Shouldn't get here!")
//    }
//
//    override protected def clone(paths: List[Node], variadic: NodeResult, end: NodeResult): MatchNode =
//      copy(paths = paths, variadic = variadic, end = end)
//
//    override protected def matchString(s: String, h: HList): HList = if (s == name) h else null
//  }
//
//}
