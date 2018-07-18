package org.http4s

trait Sigma[TC[_]] {
  type Type
  def value: Type
  def typeclass: TC[Type]
}

object Sigma {
  implicit def apply[A, TC[_]](a: A)(implicit A: TC[A]): Sigma[TC] = new Sigma[TC] {
    type Type = A
    val value = a
    val typeclass = A
  }
}