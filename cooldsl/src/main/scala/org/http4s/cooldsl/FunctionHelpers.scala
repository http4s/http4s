package org.http4s.cooldsl

import shapeless.{HList, HNil, ::}
import scala.annotation.implicitNotFound

/**
 * Created by Bryce Anderson on 4/27/14.
 */

/////////////////// Helpers for turning a function of may params to a function of a HList

trait HListToFunc[H <: HList, O, F] {
  def conv(f: F): H => O
}

object FuncHelpers {

  implicit def fun0[O] = new HListToFunc[HNil, O, () => O] {
    override def conv(f: () => O): (HNil) => O = { h => f() }
  }

  implicit def fun1[T1, O] = new HListToFunc[T1::HNil, O, T1 => O] {
    override def conv(f: (T1) => O): (T1::HNil) => O = { h => f(h.head) }
  }

  implicit def fun2[T1, T2, O] = new HListToFunc[T1::T2::HNil, O, (T2, T1) => O] {
    override def conv(f: (T2, T1) => O): (T1::T2::HNil) => O = { h => f(h.tail.head,h.head) }
  }

  implicit def fun3[T1, T2, T3, O] = new HListToFunc[T3::T2::T1::HNil, O, Function3[T1, T2, T3, O]] {
    override def conv(f: (T1, T2, T3) => O): (T3::T2::T1::HNil) => O = { h3 =>
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      f(t1,t2,t3)
    }
  }

  implicit def fun4[T1, T2, T3, T4, O] = new HListToFunc[T4::T3::T2::T1::HNil, O, Function4[T1, T2, T3, T4, O]] {
    override def conv(f: (T1, T2, T3, T4) => O): (T4::T3::T2::T1::HNil) => O = { h4 =>
      val t4 = h4.head
      val h3 = h4.tail
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      f(t1,t2,t3, t4)
    }
  }

}

