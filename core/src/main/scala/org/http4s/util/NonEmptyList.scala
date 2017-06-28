package org.http4s.util

import cats.data.NonEmptyList

trait NonEmptyListSyntax {
  implicit class NonEmptyListOps[A](self: NonEmptyList[A]) {
    def contains(a: A): Boolean =
      if (a == self.head) true
      else self.tail.contains(a)

    def length: Int =
      1 + self.tail.length

    def collectFirst[B](pf: PartialFunction[A, B]): Option[B] =
      pf.lift(self.head).orElse(self.tail.collectFirst(pf))

    def mkString(s: String): String =
      // TODO Could squeeze a few more cycles out of this
      self.toList.mkString(s)

    def mkString(begin: String, s: String, end: String): String =
      // TODO Could squeeze a few more cycles out of this
      self.toList.mkString(begin, s, end)
  }
}

object nonEmptyList extends NonEmptyListSyntax
