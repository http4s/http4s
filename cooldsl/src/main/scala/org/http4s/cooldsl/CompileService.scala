package org.http4s.cooldsl

import shapeless.HList

/**
 * Created by Bryce Anderson on 5/4/14.
 */
trait CompileService[A] {
  def compile[T <: HList, F, O](action: CoolAction[T, F, O]): A
}