package org.http4s.util

import cats.data.NonEmptyList
import org.http4s.batteries._

trait NonEmptyListSyntax {
  implicit class NonEmptyListOps[A](self: NonEmptyList[A]) {
    def :::(a: NonEmptyList[A]): NonEmptyList[A] = {
      NonEmptyList(self.head, self.tail ::: a.unwrap)
    }

    def contains(a: A): Boolean =
      self.unwrap.contains(a)

    def collectFirst[B](pf: PartialFunction[A, B]): Option[B] =
      self.unwrap.collectFirst(pf)

    def mkString(s: String): String =
      self.unwrap.mkString(s)

    def mkString(begin: String, s: String, end: String): String =
      self.unwrap.mkString(begin, s, end)
  }
}

object nonEmptyList extends NonEmptyListSyntax
