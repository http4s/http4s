/*
 * Based on https://raw.githubusercontent.com/scalaz/scalaz/v7.1.7/core/src/main/scala/scalaz/NonEmptyList.scala
 * License: https://raw.githubusercontent.com/scalaz/scalaz/v7.1.7/etc/LICENCE
 */
package org.http4s.util

import scala.annotation.tailrec
import scalaz._

/** A singly-linked list that is guaranteed to be non-empty.
  *
  * Forked from Scalaz 7.1 after moving past Scalaz 7.1 out of a desire to maintain
  * one based on [[scala.List]].
  */
final class NonEmptyList[+A] private[util] (val head: A, val tail: List[A]) {
  import NonEmptyList._
  import Zipper._

  def <::[AA >: A](b: AA): NonEmptyList[AA] = nel(b, head :: tail)

  def <:::[AA >: A](bs: List[AA]): NonEmptyList[AA] = bs match {
    case Nil => this
    case b :: bs => nel(b, bs ::: list)
  }

  def :::>[AA >: A](bs: List[AA]): NonEmptyList[AA] = nel(head, tail ::: bs)

  /** Append one nonempty list to another. */
  def append[AA >: A](f2: NonEmptyList[AA]): NonEmptyList[AA] = list <::: f2

  def map[B](f: A => B): NonEmptyList[B] = nel(f(head), tail.map(f))

  def foreach(f: A => Unit): Unit = {
    f(head)
    tail foreach f
  }

  import collection.mutable.ListBuffer

  def flatMap[B](f: A => NonEmptyList[B]): NonEmptyList[B] = {
    val b = new ListBuffer[B]
    val p = f(head)
    b += p.head
    b ++= p.tail
    tail.foreach {
      a =>
        val p = f(a)
        b += p.head
        b ++= p.tail
    }
    val bb = b.toList
    nel(bb.head, bb.tail)
  }

  def distinct[AA>:A](implicit A: Order[AA]): NonEmptyList[AA] = {
    @tailrec def loop(src: List[A], seen: ISet[AA], acc: NonEmptyList[AA]): NonEmptyList[AA] =
      src match {
        case h :: t =>
          if (seen.notMember(h))
            loop(t, seen.insert(h), h <:: acc)
          else
            loop(t, seen, acc)
        case Nil =>
          acc.reverse
      }
    loop(tail, ISet.singleton(head), NonEmptyList(head))
  }

  def traverse1[F[_], B](f: A => F[B])(implicit F: Apply[F]): F[NonEmptyList[B]] = {
    import std.list._
    tail match {
      case Nil => F.map(f(head))(nel(_, Nil))
      case b :: bs => F.apply2(f(head), OneAnd.oneAndTraverse[List].traverse1(OneAnd(b, bs))(f)) {
        case (h, t) => nel(h, t.head :: t.tail)
      }
    }
  }

  def list: List[A] = head :: tail

  def stream: Stream[A] = head #:: tail.toStream

  def toZipper: Zipper[A] = zipper(Stream.Empty, head, tail.toStream)

  def zipperEnd: Zipper[A] = {
    import Stream._
    tail.reverse match {
      case Nil     => zipper(empty, head, empty)
      case t :: ts => zipper(ts.toStream :+ head, t, empty)
    }
  }

  def init: List[A] = if(tail.isEmpty) Nil else (head :: tail.init)

  def last: A = if(tail.isEmpty) head else tail.last

  def tails: NonEmptyList[NonEmptyList[A]] = {
    @annotation.tailrec
    def tails0(as: NonEmptyList[A], accum: List[NonEmptyList[A]]): NonEmptyList[NonEmptyList[A]] =
      as.tail match {
        case Nil => nel(as, accum).reverse
        case h :: t => tails0(nel(h, t), as :: accum)
      }
    tails0(this, Nil)
  }

  def reverse: NonEmptyList[A] = (list.reverse: @unchecked) match {
    case x :: xs => nel(x, xs)
  }

  def sortBy[B](f: A => B)(implicit o: Order[B]): NonEmptyList[A] = (list.sortBy(f)(o.toScalaOrdering): @unchecked) match {
    case x :: xs => nel(x, xs)
  }

  def sortWith(lt: (A, A) => Boolean): NonEmptyList[A] = (list.sortWith(lt): @unchecked) match {
    case x :: xs => nel(x, xs)
  }

  def sorted[B >: A](implicit o: Order[B]): NonEmptyList[A] = (list.sorted(o.toScalaOrdering): @unchecked) match {
    case x :: xs => nel(x, xs)
  }

  def size: Int = 1 + tail.size

  def zip[B](b: => NonEmptyList[B]): NonEmptyList[(A, B)] = {
    val _b = b
    nel((head, _b.head), tail zip _b.tail)
  }

  def unzip[X, Y](implicit ev: A <:< (X, Y)): (NonEmptyList[X], NonEmptyList[Y]) = {
    val (a, b) = head: (X, Y)
    val (aa, bb) = tail.unzip: (List[X], List[Y])
    (nel(a, aa), nel(b, bb))
  }

  def zipWithIndex: NonEmptyList[(A, Int)] = {
    @annotation.tailrec
    def loop(as: List[A], i: Int, acc: List[(A, Int)]): List[(A, Int)] =
      as match {
        case x :: y => loop(y, i + 1, (x, i) :: acc)
        case _ => acc.reverse
      }
    new NonEmptyList((head, 0), loop(tail, 1, Nil))
  }

  override def toString: String = "NonEmpty" + (head :: tail)

  override def equals(any: Any): Boolean =
    any match {
      case that: NonEmptyList[_] => this.list == that.list
      case _                     => false
    }

  override def hashCode: Int =
    list.hashCode

  def exists(p: A => Boolean): Boolean =
    p(head) || tail.exists(p)

  def contains(elem: Any): Boolean =
    head == elem || tail.contains(elem)

  def collectFirst[B](pf: PartialFunction[A, B]): Option[B] =
    pf.lift(head).orElse(tail.collectFirst(pf))

  def mkString(sep: String): String = {
    val sb = new StringBuilder
    sb.append(head)
    tail.foreach(a => sb.append(sep).append(a))
    sb.toString
  }

  def length: Int =
    1 + tail.length
}

object NonEmptyList extends NonEmptyListInstances with NonEmptyListFunctions {
  def apply[A](h: A, t: A*): NonEmptyList[A] =
    nels(h, t: _*)

  def unapplySeq[A](v: NonEmptyList[A]): Option[(A, List[A])] =
    Some((v.head, v.tail))

  def lift[A, B](f: NonEmptyList[A] => B): IList[A] => Option[B] = {
    case INil() ⇒ None
    case ICons(h, t) ⇒ Some(f(NonEmptyList.nel(h, t.toList)))
  }

}

sealed abstract class NonEmptyListInstances0 {
  implicit def nonEmptyListEqual[A: Equal]: Equal[NonEmptyList[A]] = Equal.equalBy[NonEmptyList[A], List[A]](_.list)(std.list.listEqual[A])
}

sealed abstract class NonEmptyListInstances extends NonEmptyListInstances0 {
  implicit val nonEmptyList =
    new Traverse1[NonEmptyList] with Monad[NonEmptyList] with Plus[NonEmptyList] with Comonad[NonEmptyList] with Zip[NonEmptyList] with Unzip[NonEmptyList] with Align[NonEmptyList] {
      def traverse1Impl[G[_] : Apply, A, B](fa: NonEmptyList[A])(f: A => G[B]): G[NonEmptyList[B]] =
        fa traverse1 f

      override def foldMapRight1[A, B](fa: NonEmptyList[A])(z: A => B)(f: (A, => B) => B): B = {
        val reversed = fa.reverse
        reversed.tail.foldLeft(z(reversed.head))((x, y) => f(y, x))
      }

      override def foldMapLeft1[A, B](fa: NonEmptyList[A])(z: A => B)(f: (B, A) => B): B =
        fa.tail.foldLeft(z(fa.head))(f)

      override def foldMap1[A, B](fa: NonEmptyList[A])(f: A => B)(implicit F: Semigroup[B]): B = {
        fa.tail.foldLeft(f(fa.head))((x, y) => F.append(x, f(y)))
      }

      // would otherwise use traverse1Impl
      override def foldLeft[A, B](fa: NonEmptyList[A], z: B)(f: (B, A) => B): B =
        fa.tail.foldLeft(f(z, fa.head))(f)

      def bind[A, B](fa: NonEmptyList[A])(f: A => NonEmptyList[B]): NonEmptyList[B] = fa flatMap f

      def point[A](a: => A): NonEmptyList[A] = NonEmptyList(a)

      def plus[A](a: NonEmptyList[A], b: => NonEmptyList[A]): NonEmptyList[A] = a.list <::: b

      def copoint[A](p: NonEmptyList[A]): A = p.head

      def cobind[A, B](fa: NonEmptyList[A])(f: NonEmptyList[A] => B): NonEmptyList[B] = map(cojoin(fa))(f)

      override def cojoin[A](a: NonEmptyList[A]): NonEmptyList[NonEmptyList[A]] = a.tails

      def zip[A, B](a: => NonEmptyList[A], b: => NonEmptyList[B]) = a zip b

      def unzip[A, B](a: NonEmptyList[(A, B)]) = a.unzip

      def alignWith[A, B, C](f: A \&/ B => C) = (a, b) => {
        import std.list._
        NonEmptyList.nel(f(\&/.Both(a.head, b.head)), Align[List].alignWith(f)(a.tail, b.tail))
      }

      override def length[A](a: NonEmptyList[A]): Int = a.size

      override def all[A](fa: NonEmptyList[A])(f: A => Boolean) =
        f(fa.head) && fa.tail.forall(f)

      override def any[A](fa: NonEmptyList[A])(f: A => Boolean) =
        f(fa.head) || fa.tail.exists(f)
    }

  implicit def nonEmptyListSemigroup[A]: Semigroup[NonEmptyList[A]] = new Semigroup[NonEmptyList[A]] {
    def append(f1: NonEmptyList[A], f2: => NonEmptyList[A]) = f1 append f2
  }

  implicit def nonEmptyListShow[A: Show]: Show[NonEmptyList[A]] =
    Contravariant[Show].contramap(std.list.listShow[A])(_.list)

  implicit def nonEmptyListOrder[A: Order]: Order[NonEmptyList[A]] =
    Order.orderBy[NonEmptyList[A], List[A]](_.list)(std.list.listOrder[A])
}

trait NonEmptyListFunctions {
  def nel[A](h: A, t: List[A]): NonEmptyList[A] =
    new NonEmptyList(h, t)

  def nels[A](h: A, t: A*): NonEmptyList[A] =
    nel(h, t.toList)
}
