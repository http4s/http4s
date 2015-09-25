package org.http4s
package server

import scalaz.{Equal, Monoid}

/**
  * Encapsulates the notion of fallthrough orElse for a Service
  * For any given B, if a Fallthrough[B] exists within implicit context
  * then [[Service#orElse]] can be used.
  * The default Fallthrough implementation will select the first fallthrough
  * response Whenever a sequence of fallthrough responses is observed.
  */
trait Fallthrough[B] {
  def isFallthrough(a: B): Boolean
  def fallthrough[A](fst: B, snd: Service[A, B]): Service[A, B] =
    if (isFallthrough(fst)) snd.map { snd =>
      if (isFallthrough(snd)) fst else snd
    } else Service.constVal(fst)
}

/** Houses the principal [[Fallthrough]] typeclass instances. */
object Fallthrough {
  /** Sintacticl utility for recovering the [[Fallthrough]] currently in scope. */
  def apply[B](implicit F : Fallthrough[B]): Fallthrough[B] = F

  /** A [[Fallthrough]] for any Monoid with an Equals. */
  implicit def forMonoid[B : Monoid : Equal]: Fallthrough[B] = new Fallthrough[B] {
    def isFallthrough(a: B): Boolean = Monoid[B].isMZero(a)
  }

  /** A [[Response]] specific [[Fallthrough]] which considers any response with a 404
    * status code as a fallthrough. */
  implicit def forResponse: Fallthrough[Response] = new Fallthrough[Response] {
    def isFallthrough(r: Response): Boolean = r.status.code == 404
  }

  /** A [[Fallthrough]] which never falls through. */
  def never[B]: Fallthrough[B] = new Fallthrough[B] {
    def isFallthrough(a: B): Boolean = false
  }

}
