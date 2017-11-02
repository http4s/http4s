package org.http4s.syntax

import cats.data.NonEmptyList

trait NonEmptyListSyntax {
  implicit def http4sNonEmptyListSyntax[A](l: NonEmptyList[A]): NonEmptyListOps[A] =
    new NonEmptyListOps[A](l)
}

final class NonEmptyListOps[A](val self: NonEmptyList[A]) extends AnyVal {
  def contains(a: A): Boolean =
    if (a == self.head) true
    else self.tail.contains(a)

  def collectFirst[B](pf: PartialFunction[A, B]): Option[B] =
    pf.lift(self.head).orElse(self.tail.collectFirst(pf))

  def mkString(s: String): String =
    // TODO Could squeeze a few more cycles out of this
    self.toList.mkString(s)

  def mkString(begin: String, s: String, end: String): String =
    // TODO Could squeeze a few more cycles out of this
    self.toList.mkString(begin, s, end)
}
