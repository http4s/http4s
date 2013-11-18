package org.http4s

private[http4s] trait HttpValue[+A] {
  def value: A
  override def toString = value.toString
}
