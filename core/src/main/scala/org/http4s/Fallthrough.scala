package org.http4s

import scalaz.{Equal, Monoid}

/**
  * Encapsulates the notion of fallthrough orElse for a Service
  * For any given B, if a Fallthrough[B] exists within implicit context
  * then [[Service#orElse]] can be used.
  */
trait Fallthrough[B] {
  def fallthrough: B
  def isFallthrough(a: B): Boolean
  def fallthrough[A](fst: B, snd: Service[A, B]): Service[A, B] =
    if (isFallthrough(fst)) snd else Service.constVal(fst)
}

/** Houses the principal [[Fallthrough]] typeclass instances. */
object Fallthrough {
  /** Sintacticl utility for recovering the [[Fallthrough]] currently in scope. */
  def apply[B](implicit F : Fallthrough[B]): Fallthrough[B] = F

  /** A [[Fallthrough]] for any Monoid with an Equals. */
  implicit def forMonoid[B : Monoid : Equal]: Fallthrough[B] = new Fallthrough[B] {
    def fallthrough: B = Monoid[B].zero
    def isFallthrough(a: B): Boolean = Monoid[B].isMZero(a)
  }
}
