package org.http4s.util

trait OptionOps[A] {
  def self: Option[A]

  // present in scala 2.11 but not 2.10
  def contains(a: A) = self.isDefined && self.get == a
}

trait OptionSyntax {
  implicit def ToOptionOps[A](o: Option[A]) = new OptionOps[A] {
    def self: Option[A] = o
  }
}

object option extends OptionSyntax
