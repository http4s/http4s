package org.http4s.cooldsl

import shapeless.{HList, HNil, ::}

/**
 * Created by Bryce Anderson on 4/27/14.
 */

/////////////////// Helpers for turning a function of may params to a function of a HList

trait HListToFunc[H <: HList, O, F] {
  def conv(f: F): H => O
}

object FuncHelpers {

  implicit def fun0[O] = new HListToFunc[HNil, O, Function0[O]] {
    override def conv(f: () => O): (HNil) => O = { h => f() }
  }

  implicit def fun1[T1, O] = new HListToFunc[T1::HNil, O, Function1[T1, O]] {
    override def conv(f: (T1) => O): (T1::HNil) => O = { h => f(h.head) }
  }

  implicit def fun2[T1, T2, O] = new HListToFunc[T2::T1::HNil, O, Function2[T1, T2, O]] {
    override def conv(f: (T1, T2) => O): (T2::T1::HNil) => O = { h => f(h.tail.head,h.head) }
  }

}

